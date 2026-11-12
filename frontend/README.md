# Supply / Field Ledger - styled frontend

Separate UI release, based on the confirmed Prompt 3 app and Prompt 2 contract.
Your original frontend and the existing backend are not overwritten.

## Included

- All existing routes: stock, record, sync, discrepancies, supervisor review, issuer and history.
- Responsive field-ledger presentation, mobile stock cards, keyboard focus states, readable status labels.
- Fonts are bundled locally under public/fonts with their SIL Open Font Licenses. No font CDN needed at runtime.
- Demo and real backend modes remain separate. The package starts in DEMO mode.
- Minimal local-first fix: recording and local-only queries run even when React Query knows the browser is offline.

## Preview later, when ready (Windows PowerShell)

Node/npm must be available. From this folder:

```powershell
npm.cmd install
npm.cmd run dev
```

Open the localhost address Vite prints. Local demo login: boss1 / demo (supervisor),
keeper1 / demo (storekeeper). These are sample accounts, not deployment credentials.
The yellow DEMO DATA banner means sample data only, no backend requests.

## Real backend mode (optional, later)

Use the already-delivered Spring Boot source. Start it with its dev profile against
a separate local development PostgreSQL database, as its README describes.
Edit this frontend's .env.local to VITE_DEMO_MODE=false, keep
VITE_API_BASE_URL=/api/v1, and restart Vite. The delivery proxy points to
http://localhost:8080. Both ports must refer to the same laptop for this setup.
No database password belongs in frontend configuration or chat.

For verification only, an isolated PostgreSQL18.6 server and backend port8082 were
used. That test port is NOT the delivery default. If you choose another backend
port, update the proxy target in vite.config.ts deliberately.

Demo uses supply-local-demo IndexedDB; real mode uses supply-local. Demo data is
not converted into real stock. A separate folder is not a separate browser origin:
if Vite reuses the old hostname/port, the browser can reuse that origin's existing
storage. Keep the original demo running and use the different port Vite prints if
you want separate browser storage for the new preview. Do not clear real-mode
storage casually: that can lose pending records and device enrollment.

## Verification, 11 November 2026

- TypeScript and Vite production build: PASS.
- Frontend tests:17/17 PASS (7 guard,4 local store,5 demo,1 offline-mutation regression).
- Unchanged backend:37/37 tests PASS against real PostgreSQL18.6, separately verified.
- Styled real-backend browser flows: enrollment/login/bootstrap, stock and negative
  balances, receipt save/confirmation, manual sync, discrepancy review with reason,
  ledger history, issuer status and logout: PASS.
- Browser offline simulation: in an already-open bootstrapped app, receipt13 saved
  immediately, remained pending, appeared on Sync while offline, then confirmed on
  reconnect + manual Sync. A second Sync applied nothing twice. Database query
  confirmed one event row for that event_id, quantity13.
- Demo isolation: zero API requests during demo login/bootstrap/stock/manual sync.
- Automated accessibility check: no WCAG A/AA violations found in login plus the five
  main screens at1440/768/390/320px. Secondary-text contrast corrected after first scan.
  No page-level horizontal overflow. This is not a full accessibility certification.

## Boundaries

This release is not production-certified. Actual phone hardware, HTTPS/LAN use,
Docker build, high load and Windows setup remain untested. Narrow screenshots are
browser viewports, not real devices. The service-worker/installable offline shell
was not added in this styling pass. An offline cold launch or refresh is NOT promised.
Network available is only a browser connectivity hint, never a server/sync claim.
Reconnection does not imply automatic background sync: use Sync now.

## Developer checks

```powershell
npm.cmd run build
npm.cmd test
```

No API, ledger, sync-engine, auth-store, type, demo-server or config module was
changed by the styling pass. Query/mutation networkMode adjustments are confined
to hooks for the existing local-first contract. No new endpoints or business actions.
