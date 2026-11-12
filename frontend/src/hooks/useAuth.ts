/**
 * useAuth.ts
 * Responsibility: React-facing view over the auth store.
 * API calls: none directly (store actions call auth.api).
 */

import { useAuthStore } from '../store/auth.store'

export function useAuth() {
  return useAuthStore()
}
