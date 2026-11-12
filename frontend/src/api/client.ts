/**
 * client.ts
 * Responsibility: the single Axios instance for the supply-server API.
 *   - Base URL from env (src/config/env.ts)
 *   - Session cookie: withCredentials (HttpOnly; JS never reads it)
 *   - Device credential: X-Device-Id always, X-Device-Key when enrolled (read from IndexedDB meta)
 *   - Errors normalized to ApiError; 401 triggers the registered unauthorized handler
 * Contract note: this replaces the template's Bearer-token interceptor - the Prompt 2 contract
 * authenticates with a session cookie plus device headers, not tokens.
 * Export default; no raw fetch/axios anywhere else.
 */

import axios, { AxiosError } from 'axios'
import { env } from '../config/env'
import { getDeviceIdentity } from '../lib/localStore'
import type { ApiError } from '../types'

const client = axios.create({
  baseURL: env.apiBaseUrl,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
})

client.interceptors.request.use(async (config) => {
  const identity = await getDeviceIdentity()
  config.headers.set('X-Device-Id', identity.device_id)
  if (identity.device_key !== null) {
    config.headers.set('X-Device-Key', identity.device_key)
  }
  return config
})

let onUnauthorized: (() => void) | null = null

/** Registered once by the auth store; keeps client.ts free of store/router imports. */
export function setUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler
}

export function normalizeError(error: unknown): ApiError {
  if (axios.isAxiosError(error)) {
    const axiosError = error as AxiosError<{ message?: string; errors?: Record<string, string[]> }>
    const statusCode = axiosError.response?.status ?? 0
    const body = axiosError.response?.data
    return {
      message: body?.message ?? axiosError.message ?? 'Request failed',
      statusCode,
      errors: body?.errors,
    }
  }
  return { message: error instanceof Error ? error.message : 'Unknown error', statusCode: 0 }
}

client.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const apiError = normalizeError(error)
    if (apiError.statusCode === 401 && onUnauthorized) onUnauthorized()
    return Promise.reject(apiError)
  },
)

export default client
