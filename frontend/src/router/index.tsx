/**
 * router/index.tsx
 * Responsibility: every route definition, in one place (React Router v6, createBrowserRouter).
 *
 * | Path                         | Page                    | Auth | Role       | API calls made                    |
 * |------------------------------|-------------------------|------|------------|-----------------------------------|
 * | /login                       | LoginPage               | No   | -          | POST /auth/login                  |
 * | /                            | ItemsListPage           | Yes  | -          | GET /items + local store          |
 * | /items/:itemId/history       | ItemHistoryPage         | Yes  | -          | GET /events?item_id=...           |
 * | /record                      | RecordEventPage         | Yes  | -          | POST /events + GET /items,/issuer |
 * | /sync                        | SyncPage                | Yes  | -          | POST /events + GET /sync/pull     |
 * | /discrepancies               | DiscrepanciesListPage   | Yes  | -          | GET /discrepancies?status=OPEN    |
 * | /discrepancies/:id/resolve   | ResolveDiscrepancyPage  | Yes  | supervisor | POST /discrepancies/{id}/resolve  |
 * | /issuer                      | IssuerPage              | Yes  | -          | GET /issuer, POST /issuer/designate (supervisor) |
 */

import { createBrowserRouter, Outlet, RouterProvider } from 'react-router-dom'
import ProtectedRoute from '../components/common/ProtectedRoute'
import NavBar from '../components/common/NavBar'
import NetworkStatus from '../components/common/NetworkStatus'
import DemoBanner from '../components/common/DemoBanner'
import LoginPage from '../pages/auth/LoginPage'
import ItemsListPage from '../pages/items/ItemsListPage'
import ItemHistoryPage from '../pages/events/ItemHistoryPage'
import RecordEventPage from '../pages/events/RecordEventPage'
import SyncPage from '../pages/sync/SyncPage'
import DiscrepanciesListPage from '../pages/discrepancies/DiscrepanciesListPage'
import ResolveDiscrepancyPage from '../pages/discrepancies/ResolveDiscrepancyPage'
import IssuerPage from '../pages/issuer/IssuerPage'

function AppLayout() {
  return (
    <>
      <DemoBanner />
      <NavBar />
      <div className="content-area"><NetworkStatus /><Outlet /><footer className="page-footer"><span>SUPPLY / FIELD LEDGER</span><span>Facts stay recorded. Reviews stay traceable.</span></footer></div>
    </>
  )
}

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <AppLayout />,
    children: [
      { path: '/', element: <ProtectedRoute><ItemsListPage /></ProtectedRoute> },
      { path: '/items/:itemId/history', element: <ProtectedRoute><ItemHistoryPage /></ProtectedRoute> },
      { path: '/record', element: <ProtectedRoute><RecordEventPage /></ProtectedRoute> },
      { path: '/sync', element: <ProtectedRoute><SyncPage /></ProtectedRoute> },
      { path: '/discrepancies', element: <ProtectedRoute><DiscrepanciesListPage /></ProtectedRoute> },
      {
        path: '/discrepancies/:id/resolve',
        element: (
          <ProtectedRoute requiredRole="supervisor">
            <ResolveDiscrepancyPage />
          </ProtectedRoute>
        ),
      },
      { path: '/issuer', element: <ProtectedRoute><IssuerPage /></ProtectedRoute> },
    ],
  },
])

export default function AppRouter() {
  return <RouterProvider router={router} />
}
