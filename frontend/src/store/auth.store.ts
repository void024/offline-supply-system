/**
 * auth.store.ts
 * Responsibility: session state (Zustand). The session itself is the server's HttpOnly cookie;
 * this store holds the user returned by login plus initialization state.
 * Contract mapping notes:
 *   - No Bearer token exists in the Prompt 2 contract, so nothing token-shaped is stored here.
 *   - The contract has no "who am I" endpoint; initialize() restores the cached display user
 *     from IndexedDB and confirms the session is alive with GET /issuer (an existing endpoint),
 *     instead of inventing a new one.
 *   - Enrollment-loss recovery (contract endpoint 1): a 403 "known device, bad/missing key"
 *     triggers a NEW device_id and one re-login retry; the orphaned row stays server-side.
 * API calls: via auth.api.ts and issuer.api.ts.
 */

import { create } from 'zustand'
import * as authApi from '../api/auth.api'
import * as issuerApi from '../api/issuer.api'
import { setUnauthorizedHandler } from '../api/client'
import * as localStore from '../lib/localStore'
import * as syncEngine from '../lib/syncEngine'
import type { ApiError, AuthUser, LoginDto } from '../types'

interface AuthState {
  user: AuthUser | null
  isAuthenticated: boolean
  isInitialized: boolean
  loginPending: boolean
  loginError: ApiError | null
  login: (credentials: LoginDto) => Promise<void>
  logout: () => Promise<void>
  initialize: () => Promise<void>
}

function isApiError(e: unknown): e is ApiError {
  return typeof e === 'object' && e !== null && 'statusCode' in e
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  isAuthenticated: false,
  isInitialized: false,
  loginPending: false,
  loginError: null,

  login: async (credentials) => {
    set({ loginPending: true, loginError: null })
    try {
      let response
      try {
        response = await authApi.loginUser(credentials)
      } catch (first) {
        // Contract recovery path: 403 on a known device means the credential is undeliverable
        // or lost. Treat like lost storage: new device_id, re-enroll once.
        if (isApiError(first) && first.statusCode === 403) {
          await localStore.resetForNewDevice()
          response = await authApi.loginUser(credentials)
        } else {
          throw first
        }
      }
      if (response.device_key !== null) {
        await localStore.saveEnrollment(response.device_key, {
          user_id: response.user_id,
          username: response.username,
          role: response.role,
        })
      } else {
        await localStore.saveUser({
          user_id: response.user_id,
          username: response.username,
          role: response.role,
        })
      }
      set({
        user: { user_id: response.user_id, username: response.username, role: response.role },
        isAuthenticated: true,
        loginPending: false,
      })
      // Contract rule 6 (bootstrap): first use must be online - one full pull before the device
      // is usable. If the network is down the pull fails and RecordEventPage keeps the gate closed.
      if (!(await localStore.isBootstrapped())) {
        try {
          await syncEngine.pullAll()
        } catch {
          // offline at first login: the record page will require the first sync explicitly
        }
      }
    } catch (e) {
      set({
        loginPending: false,
        loginError: isApiError(e) ? e : { message: 'Login failed', statusCode: 0 },
      })
      throw e
    }
  },

  logout: async () => {
    try {
      await authApi.logoutUser()
    } finally {
      await localStore.saveUser(null)
      set({ user: null, isAuthenticated: false })
    }
  },

  initialize: async () => {
    if (get().isInitialized) return
    setUnauthorizedHandler(() => {
      set({ user: null, isAuthenticated: false })
    })
    const cached = await localStore.getCachedUser()
    if (cached === null) {
      set({ isInitialized: true, isAuthenticated: false })
      return
    }
    try {
      await issuerApi.getIssuer() // session liveness probe; the contract defines no whoami endpoint
      set({ user: cached, isAuthenticated: true, isInitialized: true })
    } catch (e) {
      if (isApiError(e) && e.statusCode === 401) {
        await localStore.saveUser(null)
        set({ user: null, isAuthenticated: false, isInitialized: true })
      } else {
        // Network down with a cached session: stay signed in locally; the offline-first
        // design expects periods without connectivity. Server calls will retry later.
        set({ user: cached, isAuthenticated: true, isInitialized: true })
      }
    }
  },
}))
