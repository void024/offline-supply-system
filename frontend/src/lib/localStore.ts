/**
 * localStore.ts
 * Responsibility: the device-local durable store from the Prompt 2 CLIENT section.
 *   IndexedDB stores:
 *     events  - full local copy keyed by event_id, each with pending/confirmed/quarantined state
 *     meta    - one row: device_id, device_key, next_device_seq, pull_cursor, cached user display
 *     ref     - reference cache rows: items, issuer, discrepancies, submission_issues
 *   One tab per device in v1; allocation of device_seq is additionally guarded by the Web Locks API.
 * API calls: none. IndexedDB is ordinary browser storage, not a vault (contract FLAGS).
 */

import { env } from '../config/env'
import type {
  AuthUser,
  Discrepancy,
  EventOrigin,
  EventResult,
  IssuerStatus,
  Item,
  LocalSyncState,
  SubmissionIssue,
  SupplyEvent,
} from '../types'

export interface StoredEvent {
  event_id: string
  /** null for SERVER-origin events (server-created adjustments carry no device_seq). */
  device_seq: number | null
  /** null for SERVER-origin events; otherwise the unverified client claim. */
  recorded_by_user_id: string | null
  type: SupplyEvent['type']
  item_id: string
  quantity: number
  planned_issue_id: string | null
  reason: string | null
  occurred_at: string
  sync_state: LocalSyncState
  status_detail: string | null
  created_at: string
  server_seq: number | null
  origin: EventOrigin | null
  received_at: string | null
}

interface MetaRow {
  key: 'device'
  device_id: string
  device_key: string | null
  next_device_seq: number
  pull_cursor: number
  /** Contract rule 6: first use must be online - true only after one full successful pull. */
  bootstrapped: boolean
  user: AuthUser | null
}

type RefKey = 'items' | 'issuer' | 'discrepancies' | 'submission_issues'

interface RefRow {
  key: RefKey
  value: unknown
  as_of: number
}

// Demo mode gets its OWN database, so demo records, demo device credentials and the demo
// outbox can never leak into the real backend's local store on a mode switch.
const DB_NAME = env.demoMode ? 'supply-local-demo' : 'supply-local'
const DB_VERSION = 1

let dbPromise: Promise<IDBDatabase> | null = null

function openDb(): Promise<IDBDatabase> {
  if (!dbPromise) {
    dbPromise = new Promise((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, DB_VERSION)
      req.onupgradeneeded = () => {
        const db = req.result
        if (!db.objectStoreNames.contains('events')) db.createObjectStore('events', { keyPath: 'event_id' })
        if (!db.objectStoreNames.contains('meta')) db.createObjectStore('meta', { keyPath: 'key' })
        if (!db.objectStoreNames.contains('ref')) db.createObjectStore('ref', { keyPath: 'key' })
      }
      req.onsuccess = () => resolve(req.result)
      req.onerror = () => reject(req.error ?? new Error('IndexedDB open failed'))
    })
  }
  return dbPromise
}

function txDone(tx: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error ?? new Error('IndexedDB transaction failed'))
    tx.onabort = () => reject(tx.error ?? new Error('IndexedDB transaction aborted'))
  })
}

function reqValue<T>(req: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error ?? new Error('IndexedDB request failed'))
  })
}

async function readMeta(db: IDBDatabase): Promise<MetaRow> {
  const tx = db.transaction('meta', 'readonly')
  const row = await reqValue(tx.objectStore('meta').get('device') as IDBRequest<MetaRow | undefined>)
  await txDone(tx)
  if (row) return row
  const fresh: MetaRow = {
    key: 'device',
    device_id: crypto.randomUUID(),
    device_key: null,
    next_device_seq: 1,
    pull_cursor: 0,
    bootstrapped: false,
    user: null,
  }
  const wtx = db.transaction('meta', 'readwrite')
  wtx.objectStore('meta').put(fresh)
  await txDone(wtx)
  return fresh
}

async function writeMeta(patch: Partial<MetaRow>): Promise<MetaRow> {
  const db = await openDb()
  const current = await readMeta(db)
  const next: MetaRow = { ...current, ...patch, key: 'device' }
  const tx = db.transaction('meta', 'readwrite')
  tx.objectStore('meta').put(next)
  await txDone(tx)
  return next
}

export interface DeviceIdentity {
  device_id: string
  device_key: string | null
}

export async function getDeviceIdentity(): Promise<DeviceIdentity> {
  const db = await openDb()
  const meta = await readMeta(db)
  return { device_id: meta.device_id, device_key: meta.device_key }
}

export async function saveEnrollment(device_key: string, user: AuthUser): Promise<void> {
  await writeMeta({ device_key, user })
}

export async function saveUser(user: AuthUser | null): Promise<void> {
  await writeMeta({ user })
}

export async function getCachedUser(): Promise<AuthUser | null> {
  const db = await openDb()
  return (await readMeta(db)).user
}

/**
 * Enrollment-loss recovery (contract, endpoint 1): an undeliverable credential is treated like
 * lost storage - a NEW device_id is generated and the device re-enrolls. Unsynced outbox records
 * are lost (the Prompt 1 durability boundary); synced data returns via a full re-pull from 0.
 */
export async function resetForNewDevice(): Promise<void> {
  const db = await openDb()
  const tx = db.transaction(['events', 'meta', 'ref'], 'readwrite')
  tx.objectStore('events').clear()
  tx.objectStore('ref').clear()
  tx.objectStore('meta').put({
    key: 'device',
    device_id: crypto.randomUUID(),
    device_key: null,
    next_device_seq: 1,
    pull_cursor: 0,
    bootstrapped: false,
    user: null,
  } satisfies MetaRow)
  await txDone(tx)
}

export interface NewLocalEvent {
  type: SupplyEvent['type']
  item_id: string
  quantity: number
  planned_issue_id: string | null
  reason: string | null
  occurred_at: string
  recorded_by_user_id: string
}

/**
 * Sync Protocol rule 1 (save): allocate device_seq and write the event as pending in ONE
 * IndexedDB transaction, guarded by the Web Locks API so two tabs cannot take the same number.
 * Only after this resolves may the UI say "saved".
 */
export async function saveEventLocally(input: NewLocalEvent): Promise<StoredEvent> {
  const run = async (): Promise<StoredEvent> => {
    const db = await openDb()
    const tx = db.transaction(['events', 'meta'], 'readwrite')
    const meta = (await reqValue(
      tx.objectStore('meta').get('device') as IDBRequest<MetaRow | undefined>,
    )) ?? {
      key: 'device' as const,
      device_id: crypto.randomUUID(),
      device_key: null,
      next_device_seq: 1,
      pull_cursor: 0,
      bootstrapped: false,
      user: null,
    }
    const event: StoredEvent = {
      event_id: crypto.randomUUID(),
      device_seq: meta.next_device_seq,
      recorded_by_user_id: input.recorded_by_user_id,
      type: input.type,
      item_id: input.item_id,
      quantity: input.quantity,
      planned_issue_id: input.planned_issue_id,
      reason: input.reason,
      occurred_at: input.occurred_at,
      sync_state: 'pending',
      status_detail: null,
      created_at: new Date().toISOString(),
      server_seq: null,
      origin: 'DEVICE',
      received_at: null,
    }
    tx.objectStore('events').put(event)
    tx.objectStore('meta').put({ ...meta, next_device_seq: meta.next_device_seq + 1 })
    await txDone(tx)
    return event
  }
  if (typeof navigator !== 'undefined' && 'locks' in navigator) {
    return navigator.locks.request('supply-device-seq', run)
  }
  return run()
}

async function getAllEvents(): Promise<StoredEvent[]> {
  const db = await openDb()
  const tx = db.transaction('events', 'readonly')
  const rows = await reqValue(tx.objectStore('events').getAll() as IDBRequest<StoredEvent[]>)
  await txDone(tx)
  return rows
}

function byLocalOrder(a: StoredEvent, b: StoredEvent): number {
  if (a.server_seq !== null && b.server_seq !== null) return a.server_seq - b.server_seq
  if (a.server_seq !== null) return -1
  if (b.server_seq !== null) return 1
  if (a.device_seq !== null && b.device_seq !== null) return a.device_seq - b.device_seq
  return a.created_at.localeCompare(b.created_at)
}

export async function listLocalEvents(): Promise<StoredEvent[]> {
  return (await getAllEvents()).sort(byLocalOrder)
}

export async function listPendingEvents(): Promise<StoredEvent[]> {
  return (await getAllEvents())
    .filter((e) => e.sync_state === 'pending')
    .sort((a, b) => (a.device_seq ?? 0) - (b.device_seq ?? 0))
}

export async function listQuarantinedEvents(): Promise<StoredEvent[]> {
  return (await getAllEvents())
    .filter((e) => e.sync_state === 'quarantined')
    .sort(byLocalOrder)
}

/**
 * Sync Protocol rule 2 (push outcome): ACCEPTED/DUPLICATE clears the pending flag;
 * CONFLICT/QUARANTINED moves the record to the quarantined overlay with the server's reason.
 * The record is never erased.
 */
export async function applyPushResults(results: EventResult[]): Promise<void> {
  if (results.length === 0) return
  const db = await openDb()
  const tx = db.transaction('events', 'readwrite')
  const store = tx.objectStore('events')
  for (const r of results) {
    const row = (await reqValue(store.get(r.event_id) as IDBRequest<StoredEvent | undefined>))
    if (!row) continue
    const next: StoredEvent =
      r.status === 'ACCEPTED' || r.status === 'DUPLICATE'
        ? { ...row, sync_state: 'confirmed', status_detail: null }
        : { ...row, sync_state: 'quarantined', status_detail: r.detail ?? r.status }
    store.put(next)
  }
  await txDone(tx)
}

/**
 * Sync Protocol rule 3 (pull): apply one pulled page AND advance the cursor in ONE local
 * transaction. Crash mid-apply leaves the cursor unmoved, so the page is re-pulled; applying is
 * idempotent because rows are keyed by event_id. The reference snapshot is written only with the
 * final page (has_more === false), per the pull contract.
 */
export async function applyPullPage(page: {
  events: SupplyEvent[]
  next_cursor: number
  has_more: boolean
  reference: { items: Item[]; issuer: IssuerStatus; discrepancies: Discrepancy[]; submission_issues: SubmissionIssue[] }
  reference_as_of: number
}): Promise<void> {
  const db = await openDb()
  const tx = db.transaction(['events', 'meta', 'ref'], 'readwrite')
  const eventStore = tx.objectStore('events')
  for (const ev of page.events) {
    const existing = await reqValue(eventStore.get(ev.event_id) as IDBRequest<StoredEvent | undefined>)
    const row: StoredEvent = {
      event_id: ev.event_id,
      device_seq: ev.device_seq,
      recorded_by_user_id: ev.recorded_by_user_id,
      type: ev.type,
      item_id: ev.item_id,
      quantity: ev.quantity,
      planned_issue_id: ev.planned_issue_id,
      reason: ev.reason,
      occurred_at: ev.occurred_at,
      sync_state: 'confirmed',
      status_detail: null,
      created_at: existing?.created_at ?? ev.received_at,
      server_seq: ev.server_seq,
      origin: ev.origin,
      received_at: ev.received_at,
    }
    eventStore.put(row)
  }
  const metaStore = tx.objectStore('meta')
  const meta = (await reqValue(metaStore.get('device') as IDBRequest<MetaRow | undefined>)) ?? {
    key: 'device' as const,
    device_id: crypto.randomUUID(),
    device_key: null,
    next_device_seq: 1,
    pull_cursor: 0,
    bootstrapped: false,
    user: null,
  }
  metaStore.put({ ...meta, pull_cursor: page.next_cursor })
  if (!page.has_more) {
    const refStore = tx.objectStore('ref')
    refStore.put({ key: 'items', value: page.reference.items, as_of: page.reference_as_of } satisfies RefRow)
    refStore.put({ key: 'issuer', value: page.reference.issuer, as_of: page.reference_as_of } satisfies RefRow)
    refStore.put({ key: 'discrepancies', value: page.reference.discrepancies, as_of: page.reference_as_of } satisfies RefRow)
    refStore.put({ key: 'submission_issues', value: page.reference.submission_issues, as_of: page.reference_as_of } satisfies RefRow)
  }
  await txDone(tx)
}

export async function markBootstrapped(): Promise<void> {
  await writeMeta({ bootstrapped: true })
}

export async function isBootstrapped(): Promise<boolean> {
  const db = await openDb()
  return (await readMeta(db)).bootstrapped
}

/** Test support only: close the cached connection so a test can simulate a full page reload. */
export async function _closeDbForTests(): Promise<void> {
  if (dbPromise) {
    const db = await dbPromise
    db.close()
    dbPromise = null
  }
}

export async function getPullCursor(): Promise<number> {
  const db = await openDb()
  return (await readMeta(db)).pull_cursor
}

async function getRefRow<T>(key: RefKey): Promise<{ value: T; as_of: number } | null> {
  const db = await openDb()
  const tx = db.transaction('ref', 'readonly')
  const row = await reqValue(tx.objectStore('ref').get(key) as IDBRequest<RefRow | undefined>)
  await txDone(tx)
  return row ? { value: row.value as T, as_of: row.as_of } : null
}

export async function getCachedItems(): Promise<Item[] | null> {
  const row = await getRefRow<Item[]>('items')
  return row?.value ?? null
}

export async function getCachedIssuer(): Promise<IssuerStatus | null> {
  const row = await getRefRow<IssuerStatus>('issuer')
  return row?.value ?? null
}

export async function getCachedDiscrepancies(): Promise<Discrepancy[] | null> {
  const row = await getRefRow<Discrepancy[]>('discrepancies')
  return row?.value ?? null
}

export async function getCachedSubmissionIssues(): Promise<SubmissionIssue[] | null> {
  const row = await getRefRow<SubmissionIssue[]>('submission_issues')
  return row?.value ?? null
}
