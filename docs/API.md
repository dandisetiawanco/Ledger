# Versioned REST API Reference

All application endpoints live under `/api/v1/*`. Interactive OpenAPI 3 documentation is exposed at `/swagger-ui.html` and `/v3/api-docs`.

## Conventions

- **Authentication**: HTTP-only `LEDGER_SESSION` cookie (`SameSite=Lax`).
- **CSRF Protection**: State-changing authenticated endpoints (`POST`, `PUT`, `PATCH`, `DELETE`) require `X-XSRF-TOKEN` matching the `XSRF-TOKEN` cookie issued via `GET /api/v1/auth/csrf`.
- **Idempotency**: `POST /api/v1/payments` supports the `Idempotency-Key` header (or `idempotencyKey` body field).
- **Monetary Values**: Returned as canonical decimal strings (e.g. `"10000000.00"`).
- **Error Format**: RFC 9457 `application/problem+json` (`status`, `title`, `detail`, `errorCode`, `requestId`, `timestamp`, and optional `fieldErrors`).

## Endpoint Summary

| Domain | Endpoints |
|---|---|
| Auth | `GET /api/v1/auth/csrf`, `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`, `POST /api/v1/auth/switch-organization`, `GET /api/v1/auth/sessions`, `DELETE /api/v1/auth/sessions/{id}`, `PATCH /api/v1/auth/profile` |
| Demo | `POST /api/v1/demo/start`, `GET /api/v1/public/demo-preview` |
| Organizations | `POST /api/v1/organizations`, `GET/PATCH /api/v1/organizations/current`, `GET/POST /api/v1/organizations/current/members`, `PATCH /api/v1/organizations/current/members/{id}`, `GET /api/v1/organizations/currencies`, `GET/POST /api/v1/organizations/current/exchange-rates` |
| Accounts & Periods | `GET/POST /api/v1/accounts`, `PATCH/DELETE /api/v1/accounts/{id}`, `POST /api/v1/accounts/{id}/archive`, `GET/POST /api/v1/fiscal-years`, `GET /api/v1/periods`, `POST /api/v1/periods/{id}/close`, `POST /api/v1/periods/{id}/reopen` |
| Journals & GL | `GET/POST /api/v1/journals`, `GET/PATCH/DELETE /api/v1/journals/{id}`, `POST /api/v1/journals/{id}/post`, `POST /api/v1/journals/{id}/reverse`, `GET /api/v1/general-ledger` |
| Contacts & Catalog | `GET/POST /api/v1/contacts`, `GET/PATCH /api/v1/contacts/{id}`, `GET/POST /api/v1/catalog`, `PATCH /api/v1/catalog/{id}` |
| Invoices | `GET/POST /api/v1/invoices`, `GET/PATCH /api/v1/invoices/{id}`, `POST /api/v1/invoices/{id}/issue`, `POST /api/v1/invoices/{id}/void`, `POST /api/v1/invoices/{id}/credit-notes`, `GET /api/v1/invoices/{id}/pdf`, `GET /api/v1/invoices/credit-notes/{id}/pdf` |
| Expenses | `GET/POST /api/v1/expenses`, `GET /api/v1/expenses/{id}`, `POST /api/v1/expenses/{id}/post`, `POST /api/v1/expenses/{id}/reverse`, `GET/POST /api/v1/expenses/categories` |
| Payments | `GET/POST /api/v1/payments`, `POST /api/v1/payments/{id}/reverse`, `GET /api/v1/payments/{id}/receipt.pdf` |
| Banking & Recon | `GET/POST /api/v1/banking/accounts`, `GET /api/v1/banking/accounts/{id}`, `POST /api/v1/banking/accounts/{id}/import/preview`, `POST /api/v1/banking/accounts/{id}/import/commit`, `GET /api/v1/banking/sample-statement.csv`, `GET /api/v1/reconciliation/bank-accounts/{id}`, `POST /api/v1/reconciliation/match`, `POST /api/v1/reconciliation/create-journal-and-match`, `POST /api/v1/reconciliation/{id}/unmatch` |
| Budgets | `GET/POST /api/v1/budgets`, `GET/PATCH /api/v1/budgets/{id}` |
| Reports & Exports | `GET /api/v1/reports/overview`, `/trial-balance`, `/profit-loss`, `/balance-sheet`, `/cash-flow`, `/receivables-aging`, `/payables-aging`, plus `/export?format=csv|pdf` endpoints |
| Governance | `GET /api/v1/approvals`, `POST /api/v1/approvals/submit`, `POST /api/v1/approvals/{id}/decide`, `GET /api/v1/audit`, `GET/POST /api/v1/attachments`, `GET /api/v1/attachments/{id}/download`, `GET /api/v1/notifications`, `POST /api/v1/notifications/{id}/read` |
| Observability | `GET /actuator/health`, `GET /actuator/metrics` (Owner/Admin only), `GET /actuator/prometheus` (Owner/Admin only) |
