/**
 * DiscrepanciesListPage
 * Route          : /discrepancies
 * Responsibility : list OPEN discrepancies surfaced by the server's reconciliation (endpoint 7).
 * API calls      : useDiscrepancies (GET /discrepancies?status=OPEN)
 * Auth required  : Yes (resolve link shown to supervisors only)
 */

import DiscrepancyList from '../../components/discrepancies/DiscrepancyList'
import LoadingSpinner from '../../components/common/LoadingSpinner'
import ErrorMessage from '../../components/common/ErrorMessage'
import EmptyState from '../../components/common/EmptyState'
import { useDiscrepancies } from '../../hooks/useDiscrepancies'
import { useAuth } from '../../hooks/useAuth'

export default function DiscrepanciesListPage() {
  const { user } = useAuth()
  const { data, isLoading, isError, error } = useDiscrepancies('OPEN')
  if (isLoading) return <LoadingSpinner />
  if (isError) return <ErrorMessage message={error?.message} />
  if (!data || data.discrepancies.length === 0) {
    return <EmptyState message="No open discrepancies. Stock facts and plans currently line up." />
  }
  return (
    <main className="workspace">
      <p className="eyebrow">FIELD OPERATIONS / SUPPLY LEDGER</p>
      <h1>Open discrepancies</h1>
      <p>
        The system surfaces mismatches; it never silently fixes them. A supervisor reviews each one
        with a reasoned outcome.
      </p>
      <DiscrepancyList discrepancies={data.discrepancies} canResolve={user?.role === 'supervisor'} />
    </main>
  )
}
