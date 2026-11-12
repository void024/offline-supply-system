/**
 * useIssuer.ts
 * Responsibility: React Query wrappers for the issuer resource group (contract endpoints 9-10).
 * Offline behavior per contract: fall back to the cached designation from the last sync.
 * API calls: GET /issuer, POST /issuer/designate.
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as issuerApi from '../api/issuer.api'
import * as localStore from '../lib/localStore'
import type { DesignateDto } from '../types'

export function useIssuer() {
  return useQuery({
    queryKey: ['issuer'],
    queryFn: async () => {
      try {
        return await issuerApi.getIssuer()
      } catch {
        const cached = await localStore.getCachedIssuer()
        if (cached) return cached
        throw new Error('Issuer designation unavailable (offline and never synced)')
      }
    },
    retry: 1,
  })
}

export function useDesignateIssuer() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (dto: DesignateDto) => issuerApi.designateIssuer(dto),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['issuer'] })
    },
  })
}
