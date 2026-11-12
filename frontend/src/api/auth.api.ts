/**
 * auth.api.ts
 * Responsibility: typed functions for the auth resource group (contract endpoints 1-2).
 * API calls: POST /auth/login, POST /auth/logout. No try/catch here - errors reach React Query.
 */

import client from './client'
import { env } from '../config/env'
import * as demo from '../demo'
import type { LoginDto, LoginResponse } from '../types'

export async function loginUser(dto: LoginDto): Promise<LoginResponse> {
  if (env.demoMode) return demo.loginUser(dto)
  const { data } = await client.post<LoginResponse>('/auth/login', dto)
  return data
}

export async function logoutUser(): Promise<void> {
  if (env.demoMode) return demo.logoutUser()
  await client.post('/auth/logout')
}
