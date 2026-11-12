/**
 * DemoBanner.tsx
 * Responsibility: unmistakable marker that the screen shows demo data, not a real backend.
 * Rendered only when VITE_DEMO_MODE=true. API calls: none.
 */

import { env } from '../../config/env'

export default function DemoBanner() {
  if (!env.demoMode) return null
  return (
    <p role="note" className="demo-banner">
      DEMO DATA - no backend connected. Records on this screen are sample data in this browser only.
    </p>
  )
}
