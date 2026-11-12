/**
 * useEvents.ts
 * Responsibility: React Query wrappers for the events resource group (contract endpoints 3, 6).
 * Recording goes through the sync engine (save locally first, then push) - never a direct POST.
 * API calls: POST /events, GET /events (via api layer / engine).
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as eventsApi from '../api/events.api'
import * as syncEngine from '../lib/syncEngine'
import * as localStore from '../lib/localStore'
import type { EventHistoryParams } from '../types'

export function useEventHistory(params: EventHistoryParams) {
  return useQuery({
    queryKey: ['events', 'history', params],
    queryFn: () => eventsApi.listEventHistory(params),
    enabled: params.item_id !== undefined,
  })
}

// Local durability must run even when React Query knows the browser is offline.
// The engine catches a failed network push and leaves the IndexedDB record pending.
export const recordMutationOptions = {
  mutationFn: syncEngine.recordEvent,
  networkMode: 'always' as const,
}

export function useRecordEvent() {
  const queryClient = useQueryClient()
  return useMutation({
    ...recordMutationOptions,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['local-overlays'] })
      void queryClient.invalidateQueries({ queryKey: ['local-events'] })
      void queryClient.invalidateQueries({ queryKey: ['sync-status'] })
      void queryClient.invalidateQueries({ queryKey: ['items'] })
    },
  })
}

/** Every locally visible event (confirmed + pending + quarantined) - the guard's input set. */
export function useAllLocalEvents() {
  return useQuery({ queryKey: ['local-events'], networkMode: 'always', queryFn: () => localStore.listLocalEvents() })
}
