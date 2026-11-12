/**
 * demo/index.ts
 * Responsibility: re-export the demo server so api modules can switch with one import.
 * Loaded ONLY when VITE_DEMO_MODE=true (checked in each src/api/*.ts function).
 */
export * from './server'
