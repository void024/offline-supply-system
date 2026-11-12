/** Regression: real React Query mutation + IndexedDB engine must save while offline. */
import { afterEach, expect, it, vi } from 'vitest'
import { MutationObserver, QueryClient, onlineManager } from '@tanstack/react-query'
import { recordMutationOptions } from './useEvents'
import { listPendingEvents, _closeDbForTests } from '../lib/localStore'
vi.mock('../api/events.api', () => ({ pushEvents: vi.fn().mockRejectedValue(new Error('Network offline')) }))
afterEach(async () => { onlineManager.setOnline(true); await _closeDbForTests() })
it('writes locally without waiting for reconnection, even when push fails', async () => {
  onlineManager.setOnline(false)
  const client = new QueryClient()
  const observer = new MutationObserver(client, recordMutationOptions)
  const result = await observer.mutate({ type: 'RECEIPT', item_id: 'offline-item', quantity: 7,
    planned_issue_id: null, reason: null, occurred_at: '2026-11-11T15:30:00Z', recorded_by_user_id: 'user-1' })
  expect(result.pushedNow).toBe(false)
  expect(observer.getCurrentResult().isPaused).toBe(false)
  const pending = await listPendingEvents()
  expect(pending.some(event => event.event_id === result.event.event_id && event.quantity === 7)).toBe(true)
  client.clear()
})
