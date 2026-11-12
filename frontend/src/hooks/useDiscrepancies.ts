/**
 * useDiscrepancies.ts
 * Responsibility: React Query wrappers for the discrepancies resource group (contract 7-8).
 * API calls: GET /discrepancies, POST /discrepancies/{id}/resolve.
 */

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import * as discrepanciesApi from '../api/discrepancies.api'
import type { DiscrepancyStatus, ResolveDto } from '../types'

export function useDiscrepancies(status: DiscrepancyStatus = 'OPEN') {
  return useQuery({
    queryKey: ['discrepancies', status],
    queryFn: () => discrepanciesApi.listDiscrepancies(status),
    retry: 1,
  })
}

export function useResolveDiscrepancy(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (dto: ResolveDto) => discrepanciesApi.resolveDiscrepancy(id, dto),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['discrepancies'] })
      void queryClient.invalidateQueries({ queryKey: ['items'] })
      void queryClient.invalidateQueries({ queryKey: ['events'] })
    },
  })
}
