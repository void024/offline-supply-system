/**
 * useSync.ts
 * Responsibility: React Query wrappers for the sync resource group (contract endpoint 4)
 * and the device-local sync status.
 * API calls: GET /sync/pull (via the sync engine).
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as syncEngine from '../lib/syncEngine'

export function useSyncStatus() {
  return useQuery({ queryKey: ['sync-status'], networkMode: 'always', queryFn: () => syncEngine.getStatus() })
}

export function useSyncNow() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: syncEngine.syncNow,
    onSuccess: () => {
      void queryClient.invalidateQueries() // a successful sync can change every layer
    },
  })
}
