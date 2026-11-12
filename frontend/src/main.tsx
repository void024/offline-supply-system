/**
 * main.tsx
 * Responsibility: application entry - React root, React Query provider, session restore, router.
 * API calls: GET /issuer (session liveness probe inside initialize, per the contract's endpoints).
 */

import './fonts.css'
import './styles.css'

import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AppRouter from './router'
import { useAuthStore } from './store/auth.store'

const queryClient = new QueryClient()

void useAuthStore.getState().initialize()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AppRouter />
    </QueryClientProvider>
  </StrictMode>,
)
