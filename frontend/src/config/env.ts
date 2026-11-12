/**
 * env.ts
 * Responsibility: typed loader for build-time environment. The only file that reads import.meta.env.
 * API calls: none.
 */
export const env = {
  apiBaseUrl: (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '/api/v1',
  demoMode: ((import.meta.env.VITE_DEMO_MODE as string | undefined) ?? 'false') === 'true',
} as const
