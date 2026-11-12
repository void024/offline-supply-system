/**
 * ProtectedRoute.tsx
 * Responsibility: gate routes on authentication (and optionally a role).
 *   !isInitialized -> spinner; !isAuthenticated -> /login; wrong role -> notice; else children.
 * Props: children, requiredRole? API calls: none.
 */

import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import type { Role } from '../../types'
import LoadingSpinner from './LoadingSpinner'

interface Props {
  children: ReactNode
  requiredRole?: Role
}

export default function ProtectedRoute({ children, requiredRole }: Props) {
  const { user, isAuthenticated, isInitialized } = useAuth()
  if (!isInitialized) return <LoadingSpinner />
  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (requiredRole && user?.role !== requiredRole) {
    return <p role="alert">This page requires the {requiredRole} role.</p>
  }
  return <>{children}</>
}
