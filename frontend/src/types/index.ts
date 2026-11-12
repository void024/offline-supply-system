/**
 * index.ts — single re-export point for every contract type (Prompt 3 template: one source of truth).
 * Split per resource because the interface count exceeds 15.
 */
export * from './auth.types'
export * from './events.types'
export * from './sync.types'
export * from './items.types'
export * from './discrepancies.types'
export * from './issuer.types'
export * from './local.types'
export * from './api.types'
