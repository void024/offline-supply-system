/** auth.types.ts — request/response shapes for POST /api/v1/auth/login and /logout (Prompt 2 contract, endpoints 1-2). */

export type Role = 'storekeeper' | 'supervisor'

export interface AuthUser {
  user_id: string
  username: string
  role: Role
}

export interface LoginDto {
  username: string
  password: string
}

export interface LoginResponse {
  user_id: string
  username: string
  role: Role
  /** Present exactly once, on first enrollment of this device. Stored in IndexedDB, never shown again. */
  device_key: string | null
}
