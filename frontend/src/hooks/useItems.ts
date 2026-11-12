/**
 * useItems.ts
 * Responsibility: React Query wrappers for the items resource group (contract endpoint 5)
 * plus the local overlay layers the contract requires alongside confirmed stock.
 * API calls: GET /items (via items.api.ts); overlays read the device-local store.
 */

import { useQuery } from '@tanstack/react-query'
import * as itemsApi from '../api/items.api'
import * as localStore from '../lib/localStore'

export function useItems() {
  return useQuery({ queryKey: ['items'], queryFn: () => itemsApi.listItems(), retry: 1 })
}

/** The contract's three separate UI layers: confirmed stock, pending records, quarantined records. */
export function useLocalOverlays() {
  return useQuery({
    queryKey: ['local-overlays'],
    networkMode: 'always', // IndexedDB reads must not wait for the network.
    queryFn: async () => ({
      pending: await localStore.listPendingEvents(),
      quarantined: await localStore.listQuarantinedEvents(),
    }),
  })
}
