/**
 * demo/server.ts
 * Responsibility: an in-browser simulation of the Prompt 2 contract, used ONLY in demo mode.
 * It enforces the same rules the contract specifies (per-event validation, duplicate handling,
 * issuer restriction, supervisor-only adjustments, resolve idempotency, designation singleton)
 * so screens behave truthfully. It is NOT the backend: reconciliation here is a simplified
 * R1/R2 recompute, nothing persists beyond the tab, and no claim about real sync is made.
 * The real server is Prompt 4 scope (Java 21 + Spring Boot + PostgreSQL).
 */

import { getDeviceIdentity } from '../lib/localStore'
import type {
  ApiError,
  AuthUser,
  DesignateDto,
  DesignateResponse,
  DiscrepanciesResponse,
  Discrepancy,
  DiscrepancyStatus,
  EventHistoryParams,
  EventHistoryResponse,
  EventResult,
  IssuerStatus,
  Item,
  ItemsResponse,
  LoginDto,
  LoginResponse,
  PullResponse,
  PushEventsRequest,
  PushEventsResponse,
  ResolveDto,
  ResolveResponse,
  SubmissionIssue,
  SupplyEvent,
} from '../types'
import { DEMO_USERS, SEED_DISCREPANCIES, SEED_EVENTS, SEED_ITEMS } from './data'

interface DemoState {
  session: AuthUser | null
  devices: Map<string, string> // device_id -> device_key (enrollment register)
  events: SupplyEvent[]
  nextSeq: number
  discrepancies: Discrepancy[]
  resolutions: Map<string, { discrepancyId: string; dto: ResolveDto; response: ResolveResponse }>
  issuer: IssuerStatus
  submissionIssues: SubmissionIssue[]
}

function freshState(): DemoState {
  return {
    session: null,
    devices: new Map(),
    events: SEED_EVENTS.map((e) => ({ ...e })),
    nextSeq: SEED_EVENTS.length + 1,
    discrepancies: SEED_DISCREPANCIES.map((d) => ({ ...d })),
    resolutions: new Map(),
    issuer: { device_id: null, designated_at: null },
    submissionIssues: [],
  }
}

const state: DemoState = freshState()

const sleep = (ms: number): Promise<void> => new Promise((r) => setTimeout(r, ms))

function fail(statusCode: number, message: string): never {
  throw { message, statusCode } satisfies ApiError
}

function requireSession(): AuthUser {
  if (!state.session) fail(401, 'Not authenticated (demo session)')
  return state.session
}

function computeItems(): Item[] {
  return SEED_ITEMS.map((seed) => {
    let balance = 0
    let planned = 0
    let handed = 0
    for (const e of state.events) {
      if (e.item_id !== seed.id) continue
      if (e.type === 'RECEIPT') balance += e.quantity
      else if (e.type === 'HANDOVER') balance -= e.quantity
      else if (e.type === 'ADJUSTMENT') balance += e.quantity
      else if (e.type === 'PLANNED_ISSUE') planned += e.quantity
      if (e.type === 'HANDOVER' && e.planned_issue_id !== null) handed += e.quantity
    }
    return { ...seed, balance, planned_outstanding: Math.max(0, planned - handed) }
  })
}

/** Simplified demo reconciliation: R1 (negative balance) + R2 (unplanned handover), deduped. */
function recomputeDiscrepancies(): void {
  for (const item of computeItems()) {
    const exists = state.discrepancies.some(
      (d) => d.item_id === item.id && d.type === 'NEGATIVE_BALANCE' && d.status === 'OPEN',
    )
    if (item.balance < 0 && !exists) {
      state.discrepancies.push({
        id: crypto.randomUUID(),
        item_id: item.id,
        type: 'NEGATIVE_BALANCE',
        details: `Accounting balance is ${item.balance} for ${item.name}. A delayed receipt or a factual over-handover can explain this; the facts are retained.`,
        status: 'OPEN',
        detected_at: new Date().toISOString(),
        resolution: null,
      })
    }
  }
  for (const e of state.events) {
    if (e.type !== 'HANDOVER' || e.planned_issue_id !== null) continue
    const exists = state.discrepancies.some(
      (d) => d.type === 'UNPLANNED_HANDOVER' && d.details.includes(e.event_id) && d.status === 'OPEN',
    )
    if (!exists) {
      state.discrepancies.push({
        id: crypto.randomUUID(),
        item_id: e.item_id,
        type: 'UNPLANNED_HANDOVER',
        details: `Handover ${e.event_id} of ${e.quantity} unit(s) has no planned issue linked. The fact is retained; a supervisor reviews it.`,
        status: 'OPEN',
        detected_at: new Date().toISOString(),
        resolution: null,
      })
    }
  }
}

// ---- auth (contract endpoints 1-2) ----

export async function loginUser(dto: LoginDto): Promise<LoginResponse> {
  await sleep(150)
  const user = DEMO_USERS.find((u) => u.username === dto.username && u.password === dto.password)
  if (!user) fail(401, 'Bad credentials')
  const identity = await getDeviceIdentity()
  const knownKey = state.devices.get(identity.device_id)
  if (knownKey !== undefined) {
    if (identity.device_key !== knownKey) fail(403, 'Known device, bad or missing key')
    state.session = { user_id: user.user_id, username: user.username, role: user.role }
    return { user_id: user.user_id, username: user.username, role: user.role, device_key: null }
  }
  const deviceKey = crypto.randomUUID()
  state.devices.set(identity.device_id, deviceKey)
  state.session = { user_id: user.user_id, username: user.username, role: user.role }
  return { user_id: user.user_id, username: user.username, role: user.role, device_key: deviceKey }
}

export async function logoutUser(): Promise<void> {
  await sleep(50)
  state.session = null
}

// ---- events (contract endpoints 3, 6) ----

function samePayload(a: SupplyEvent | PushEventsRequest['events'][number], b: PushEventsRequest['events'][number]): boolean {
  return (
    a.device_seq === b.device_seq &&
    a.recorded_by_user_id === b.recorded_by_user_id &&
    a.type === b.type &&
    a.item_id === b.item_id &&
    a.quantity === b.quantity &&
    a.planned_issue_id === b.planned_issue_id &&
    a.reason === b.reason
  )
}

export async function pushEvents(body: PushEventsRequest): Promise<PushEventsResponse> {
  await sleep(200)
  const user = requireSession()
  const identity = await getDeviceIdentity()
  const results: EventResult[] = []
  for (const ev of body.events) {
    const existing = state.events.find((e) => e.event_id === ev.event_id)
    if (existing) {
      results.push(
        samePayload(existing, ev)
          ? { event_id: ev.event_id, status: 'DUPLICATE', detail: null }
          : { event_id: ev.event_id, status: 'CONFLICT', detail: 'Same event_id, different payload' },
      )
      continue
    }
    const item = SEED_ITEMS.find((i) => i.id === ev.item_id)
    const knownAuthor = DEMO_USERS.some((u) => u.user_id === ev.recorded_by_user_id)
    let verdict: EventResult
    if (!item) {
      verdict = { event_id: ev.event_id, status: 'QUARANTINED', detail: 'Unknown item_id' }
    } else if (!knownAuthor) {
      verdict = { event_id: ev.event_id, status: 'QUARANTINED', detail: 'recorded_by does not reference a real user' }
    } else if (ev.type === 'ADJUSTMENT' && (ev.quantity === 0 || !ev.reason)) {
      verdict = { event_id: ev.event_id, status: 'QUARANTINED', detail: 'ADJUSTMENT needs a non-zero quantity and a reason' }
    } else if (ev.type !== 'ADJUSTMENT' && ev.quantity <= 0) {
      verdict = { event_id: ev.event_id, status: 'QUARANTINED', detail: 'Quantity must be positive' }
    } else if (ev.type === 'ADJUSTMENT' && user.role !== 'supervisor') {
      verdict = { event_id: ev.event_id, status: 'QUARANTINED', detail: 'ADJUSTMENT requires the session user to be a supervisor' }
    } else if (
      (ev.type === 'PLANNED_ISSUE' || ev.type === 'HANDOVER') &&
      state.issuer.device_id !== identity.device_id
    ) {
      verdict = { event_id: ev.event_id, status: 'QUARANTINED', detail: 'Issue records are accepted only from the designated device' }
    } else {
      verdict = { event_id: ev.event_id, status: 'ACCEPTED', detail: null }
    }
    if (verdict.status === 'ACCEPTED') {
      state.events.push({
        ...ev,
        server_seq: state.nextSeq++,
        origin: 'DEVICE',
        received_at: new Date().toISOString(),
      })
    } else if (verdict.status === 'CONFLICT' || verdict.status === 'QUARANTINED') {
      if (!state.submissionIssues.some((s) => s.claimed_event_id === ev.event_id && s.kind === verdict.status)) {
        state.submissionIssues.push({
          claimed_event_id: ev.event_id,
          kind: verdict.status,
          detail: verdict.detail ?? '',
        })
      }
    }
    results.push(verdict)
  }
  if (results.some((r) => r.status === 'ACCEPTED')) recomputeDiscrepancies()
  return { results }
}

export async function listEventHistory(params: EventHistoryParams): Promise<EventHistoryResponse> {
  await sleep(100)
  requireSession()
  const after = params.after ?? 0
  const limit = params.limit ?? 100
  const events = state.events
    .filter((e) => e.server_seq > after)
    .filter((e) => (params.item_id ? e.item_id === params.item_id : true))
    .filter((e) => (params.type ? e.type === params.type : true))
    .sort((a, b) => a.server_seq - b.server_seq)
    .slice(0, limit)
  return { events }
}

// ---- sync pull (contract endpoint 4) ----

export async function pullEvents(params: { after: number; limit?: number }): Promise<PullResponse> {
  await sleep(150)
  requireSession()
  const limit = params.limit ?? 500
  const remaining = state.events
    .filter((e) => e.server_seq > params.after)
    .sort((a, b) => a.server_seq - b.server_seq)
  const page = remaining.slice(0, limit)
  const maxSeq = state.events.reduce((m, e) => Math.max(m, e.server_seq), 0)
  return {
    events: page,
    next_cursor: page.length > 0 ? page[page.length - 1].server_seq : params.after,
    has_more: remaining.length > limit,
    reference: {
      items: computeItems(),
      issuer: { ...state.issuer },
      discrepancies: state.discrepancies.map((d) => ({ ...d })),
      submission_issues: state.submissionIssues.map((s) => ({ ...s })),
    },
    reference_as_of: maxSeq,
  }
}

// ---- items (contract endpoint 5) ----

export async function listItems(): Promise<ItemsResponse> {
  await sleep(100)
  requireSession()
  return { items: computeItems() }
}

// ---- discrepancies (contract endpoints 7-8) ----

export async function listDiscrepancies(status: DiscrepancyStatus): Promise<DiscrepanciesResponse> {
  await sleep(100)
  requireSession()
  return { discrepancies: state.discrepancies.filter((d) => d.status === status).map((d) => ({ ...d })) }
}

export async function resolveDiscrepancy(id: string, dto: ResolveDto): Promise<ResolveResponse> {
  await sleep(150)
  const user = requireSession()
  if (user.role !== 'supervisor') fail(403, 'Resolving a discrepancy requires a supervisor')
  const prior = state.resolutions.get(dto.resolution_id)
  if (prior) {
    const identical =
      prior.discrepancyId === id &&
      prior.dto.outcome === dto.outcome &&
      prior.dto.reason === dto.reason &&
      prior.dto.quantity_delta === dto.quantity_delta
    if (!identical) fail(409, 'resolution_id was already used with a different payload')
    return prior.response
  }
  const discrepancy = state.discrepancies.find((d) => d.id === id)
  if (!discrepancy) fail(404, 'Discrepancy not found')
  if (!dto.reason || dto.reason.length === 0) fail(400, 'A reason is required for every outcome')
  let adjustmentEventId: string | null = null
  if (dto.outcome === 'ADJUSTMENT') {
    if (dto.quantity_delta === null || dto.quantity_delta === 0) {
      fail(400, 'ADJUSTMENT requires a non-zero quantity_delta')
    }
    adjustmentEventId = dto.resolution_id // contract: the ADJUSTMENT event's event_id IS the resolution_id
    state.events.push({
      event_id: dto.resolution_id,
      device_seq: null,
      recorded_by_user_id: null,
      type: 'ADJUSTMENT',
      item_id: discrepancy.item_id,
      quantity: dto.quantity_delta,
      planned_issue_id: null,
      reason: dto.reason,
      occurred_at: new Date().toISOString(),
      server_seq: state.nextSeq++,
      origin: 'SERVER',
      received_at: new Date().toISOString(),
    })
  }
  discrepancy.status = 'RESOLVED'
  discrepancy.resolution = {
    resolution_id: dto.resolution_id,
    outcome: dto.outcome,
    reason: dto.reason,
    quantity_delta: dto.quantity_delta,
    resolved_at: new Date().toISOString(),
  }
  const response: ResolveResponse = {
    resolution_id: dto.resolution_id,
    status: 'RESOLVED',
    adjustment_event_id: adjustmentEventId,
  }
  state.resolutions.set(dto.resolution_id, { discrepancyId: id, dto, response })
  return response
}

// ---- issuer (contract endpoints 9-10) ----

export async function getIssuer(): Promise<IssuerStatus> {
  await sleep(80)
  requireSession()
  return { ...state.issuer }
}

export async function designateIssuer(dto: DesignateDto): Promise<DesignateResponse> {
  await sleep(120)
  const user = requireSession()
  if (user.role !== 'supervisor') fail(403, 'Designating the issuer requires a supervisor')
  const existingDevice = state.issuer.device_id
  if (existingDevice !== null) {
    if (existingDevice === dto.device_id) {
      return { device_id: existingDevice, designated_at: state.issuer.designated_at ?? '' }
    }
    fail(409, 'A designation already exists; v1 has no re-designate endpoint')
  }
  const designatedAt = new Date().toISOString()
  state.issuer = { device_id: dto.device_id, designated_at: designatedAt }
  return { device_id: dto.device_id, designated_at: designatedAt }
}
