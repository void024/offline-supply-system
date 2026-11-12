/**
 * server.test.ts (demo)
 * Verifies the demo server's contract behavior: issuer restriction, duplicate-safe push replay
 * (the response-loss case), and resolve idempotency. These are contract-semantics tests against
 * the demo simulation - they make NO claim about the real backend, which is Prompt 4 scope.
 */

import { beforeAll, describe, expect, it } from 'vitest'
import * as demo from './server'
import { getDeviceIdentity } from '../lib/localStore'
import type { EventInputDto } from '../types'

const BOSS = { username: 'boss1', password: 'demo' }

function makeEvent(partial: Partial<EventInputDto>): EventInputDto {
  return {
    event_id: crypto.randomUUID(),
    device_seq: 1,
    recorded_by_user_id: '22222222-2222-4222-8222-222222222222',
    type: 'RECEIPT',
    item_id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa01',
    quantity: 5,
    planned_issue_id: null,
    reason: null,
    occurred_at: '2026-11-11T02:00:00.000Z',
    ...partial,
  }
}

beforeAll(async () => {
  await demo.loginUser(BOSS) // supervisor session for the whole file
})

describe('demo server contract behavior', () => {
  it('quarantines a handover from a non-designated device', async () => {
    const res = await demo.pushEvents({ events: [makeEvent({ type: 'HANDOVER', quantity: 1 })] })
    expect(res.results[0].status).toBe('QUARANTINED')
    expect(res.results[0].detail).toMatch(/designated/)
  })

  it('response-loss replay: re-pushing the same batch returns DUPLICATE and changes nothing', async () => {
    const batch = { events: [makeEvent({ quantity: 7 })] }
    const first = await demo.pushEvents(batch)
    expect(first.results[0].status).toBe('ACCEPTED')
    const second = await demo.pushEvents(batch) // client retries after losing the response
    expect(second.results[0].status).toBe('DUPLICATE')
    const history = await demo.listEventHistory({ item_id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa01' })
    expect(history.events.filter((e) => e.event_id === batch.events[0].event_id).length).toBe(1)
  })

  it('same event_id with a different payload is a CONFLICT, reported once', async () => {
    const ev = makeEvent({ quantity: 3 })
    await demo.pushEvents({ events: [ev] })
    const conflict = await demo.pushEvents({ events: [{ ...ev, quantity: 4 }] })
    expect(conflict.results[0].status).toBe('CONFLICT')
    const again = await demo.pushEvents({ events: [{ ...ev, quantity: 4 }] })
    expect(again.results[0].status).toBe('CONFLICT') // stable retry, no new machinery
  })

  it('designation is a singleton: same device idempotent, different device 409', async () => {
    const identity = await getDeviceIdentity()
    const first = await demo.designateIssuer({ device_id: identity.device_id })
    expect(first.device_id).toBe(identity.device_id)
    const again = await demo.designateIssuer({ device_id: identity.device_id })
    expect(again.device_id).toBe(identity.device_id)
    await expect(demo.designateIssuer({ device_id: crypto.randomUUID() })).rejects.toMatchObject({ statusCode: 409 })
  })

  it('after designation this device can record issues; resolve is idempotent under replay', async () => {
    const identity = await getDeviceIdentity()
    const handover = await demo.pushEvents({ events: [makeEvent({ type: 'HANDOVER', quantity: 1 })] })
    expect(handover.results[0].status).toBe('ACCEPTED')
    expect((await demo.getIssuer()).device_id).toBe(identity.device_id)

    const open = await demo.listDiscrepancies('OPEN')
    expect(open.discrepancies.length).toBeGreaterThan(0)
    const target = open.discrepancies[0]
    const dto = {
      resolution_id: crypto.randomUUID(),
      outcome: 'NO_CHANGE' as const,
      reason: 'Verified with the supplier - facts stand.',
      quantity_delta: null,
    }
    const r1 = await demo.resolveDiscrepancy(target.id, dto)
    expect(r1.status).toBe('RESOLVED')
    const r2 = await demo.resolveDiscrepancy(target.id, dto) // lost response, user retried
    expect(r2).toEqual(r1)
    await expect(
      demo.resolveDiscrepancy(target.id, { ...dto, reason: 'different payload' }),
    ).rejects.toMatchObject({ statusCode: 409 })
  })
})
