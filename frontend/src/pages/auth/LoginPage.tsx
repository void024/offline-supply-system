/**
 * LoginPage
 * Route          : /login
 * Responsibility : sign in (and enroll this device on first login) via contract endpoint 1.
 * API calls      : useAuth().login -> POST /auth/login
 * Auth required  : No
 */

import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'
import DemoBanner from '../../components/common/DemoBanner'

export default function LoginPage() {
  const { login, loginPending, loginError, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  if (isAuthenticated) {
    navigate('/', { replace: true })
    return null
  }

  return (
    <main className="login-layout">
      <aside className="login-story"><div className="wordmark">SUPPLY<span> / FIELD LEDGER</span></div><p className="eyebrow">BUILT FOR THE LAST MILE</p><h2>Every movement.<br />Accounted for.</h2><p>Clear stock. Honest records. A steady hand when the connection isn't.</p><div className="ledger-art" aria-hidden="true"><span>01 / RECEIVE</span><span>02 / RECORD</span><span>03 / RECONCILE</span></div><p className="login-footnote">A physical handover is a fact, not a promise.</p></aside>
      <section className="login-card">
      <p className="eyebrow">YOUR WORKSPACE</p>
      <DemoBanner />
      <h1>Welcome back.</h1><p>Sign in to your supply ledger.</p>
      <form
        onSubmit={(e) => {
          e.preventDefault()
          void login({ username, password }).then(() => navigate('/', { replace: true })).catch(() => undefined)
        }}
        style={{ display: 'flex', flexDirection: 'column', gap: 10 }}
      >
        <label>
          Username
          <input type="text" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} />
        </label>
        <label>
          Password
          <input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        {loginError ? <p role="alert">{loginError.message}</p> : null}
        <button type="submit" disabled={loginPending}>
          {loginPending ? 'Signing in...' : 'Sign in'}
        </button>
      </form>
      <p>First sign-in on a new device enrolls it and stores a device credential on this device.</p>
      </section>
    </main>
  )
}
