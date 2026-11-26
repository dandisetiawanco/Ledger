# Demonstration Workspace

Ledger ships with a deterministic, isolated demonstration workspace representing **Nusa Karsa Studio**, a fictional digital product and engineering agency in Jakarta (base currency `IDR`, timezone `Asia/Jakarta`, fiscal year January through December).

## Key Guarantees

- **100% Synthetic Data**: No real business records or personal client data are included.
- **Real Accounting Engine**: `DemoSeedService` creates every bank opening balance, invoice, partial payment, credit note, multi-currency USD settlement, operating expense, supplier bill, CSV statement import, reconciliation match, budget, and pending approval exclusively through the validated domain services.
- **Verified Reconciliation**: Automated tests assert that the seeded workspace satisfies Trial Balance equality (`Dr == Cr`), the Balance Sheet equation (`Assets == Liabilities + Equity`), Direct Cash Flow reconciliation, and AR/AP subledger-to-GL control reconciliation.
- **Session Isolation**: Clicking **Try the demo** invokes `POST /api/v1/demo/start`, issuing a session for `demo@nusakarsa.demo` (`is_demo_user = true`). `SessionAuthenticationFilter` prevents demo users from accessing any non-demo organization or downloading private attachments.
