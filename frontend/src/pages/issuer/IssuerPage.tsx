/**
 * IssuerPage
 * Route          : /issuer
 * Responsibility : show the fixed v1 issuer designation; supervisors can designate once (9-10).
 * API calls      : useIssuer (GET /issuer, cached fallback offline), useDesignateIssuer (POST),
 *                  useSyncStatus (local store, for this device's id)
 * Auth required  : Yes (designation requires supervisor)
 */

import IssuerPanel from '../../components/issuer/IssuerPanel'
import LoadingSpinner from '../../components/common/LoadingSpinner'
import ErrorMessage from '../../components/common/ErrorMessage'
import { useDesignateIssuer, useIssuer } from '../../hooks/useIssuer'
import { useSyncStatus } from '../../hooks/useSync'
import { useAuth } from '../../hooks/useAuth'

export default function IssuerPage() {
  const { user } = useAuth()
  const issuer = useIssuer()
  const status = useSyncStatus()
  const designate = useDesignateIssuer()

  if (issuer.isLoading || status.isLoading) return <LoadingSpinner />
  if (issuer.isError) return <ErrorMessage message={issuer.error?.message} />
  if (!issuer.data || !status.data) return <ErrorMessage message="Issuer status unavailable." />

  return (
    <main className="workspace">
      <p className="eyebrow">FIELD OPERATIONS / SUPPLY LEDGER</p>
      <h1>Issuer designation</h1>
      <IssuerPanel
        issuer={issuer.data}
        thisDeviceId={status.data.device_id}
        canDesignate={user?.role === 'supervisor'}
        pending={designate.isPending}
        serverError={designate.isError ? designate.error.message : null}
        onDesignate={(deviceId) => designate.mutate({ device_id: deviceId })}
      />
    </main>
  )
}
