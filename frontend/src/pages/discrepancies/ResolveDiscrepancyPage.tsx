/**
 * ResolveDiscrepancyPage
 * Route          : /discrepancies/:id/resolve
 * Responsibility : record a supervisor's reasoned outcome for one discrepancy (endpoint 8).
 * API calls      : useResolveDiscrepancy (POST /discrepancies/{id}/resolve)
 * Auth required  : Yes, supervisor
 */

import { useNavigate, useParams } from 'react-router-dom'
import ResolveForm from '../../components/discrepancies/ResolveForm'
import ErrorMessage from '../../components/common/ErrorMessage'
import { useResolveDiscrepancy } from '../../hooks/useDiscrepancies'

export default function ResolveDiscrepancyPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const resolve = useResolveDiscrepancy(id ?? '')
  if (!id) return <ErrorMessage message="Missing discrepancy id." />
  return (
    <main className="workspace">
      <p className="eyebrow">FIELD OPERATIONS / SUPPLY LEDGER</p>
      <h1>Review discrepancy</h1>
      <p>
        Record a reasoned outcome. "Explained - no stock change" keeps the facts as they are; an
        adjustment is made only when a stock correction is justified.
      </p>
      <ResolveForm
        pending={resolve.isPending}
        serverError={resolve.isError ? resolve.error.message : null}
        onSubmit={(dto) =>
          resolve.mutate(dto, {
            onSuccess: () => navigate('/discrepancies'),
          })
        }
      />
    </main>
  )
}
