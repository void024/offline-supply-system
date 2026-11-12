/** NavBar: navigation and logout; preserves existing auth and route behavior. */
import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'

const routes = [ ['/', 'Stock', '01'], ['/record', 'Record', '02'], ['/sync', 'Sync', '03'], ['/discrepancies', 'Discrepancies', '04'], ['/issuer', 'Issuer', '05'] ] as const

export default function NavBar() {
  const { user, isAuthenticated, logout } = useAuth()
  const navigate = useNavigate()
  if (!isAuthenticated) return null
  return (
    <header className="app-header">
      <a className="wordmark" href="/">SUPPLY<span> / FIELD LEDGER</span></a>
      <nav aria-label="Main navigation">
        {routes.map(([to, title, number]) => <NavLink key={to} to={to} end={to === '/'}><span className="nav-number" aria-hidden="true">{number}</span>{title}</NavLink>)}
      </nav>
      <div className="user-panel"><span className="user-avatar" aria-hidden="true">{user?.username.slice(0, 1).toUpperCase()}</span><span><strong>{user?.username}</strong><small>{user?.role}</small></span><button className="logout-button" type="button" onClick={() => { void logout().then(() => navigate('/login')) }}>Log out</button></div>
    </header>
  )
}
