/** NetworkStatus: browser connectivity hint only, not a server-health or sync claim. */
import { env } from '../../config/env'
import { useEffect, useState } from 'react'
export default function NetworkStatus() {
  const [online, setOnline] = useState(navigator.onLine)
  useEffect(() => {
    const update = () => setOnline(navigator.onLine)
    window.addEventListener('online', update)
    window.addEventListener('offline', update)
    return () => { window.removeEventListener('online', update); window.removeEventListener('offline', update) }
  }, [])
  return <p className={`network-status ${online ? '' : 'network-offline'}`} role="status">{env.demoMode ? 'Demo mode. Sync is simulated; no backend requests.' : online ? 'Network available. Sync verifies server reachability.' : 'Network offline. Saved local records remain pending until sync succeeds.'}</p>
}
