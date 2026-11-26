# Threat Model

Structured threat analysis covering key financial application risks, implemented controls, and residual boundaries.

| Threat | Attack Vector | Implemented Mitigation | Verification |
|---|---|---|---|
| Broken Access Control (IDOR) | Supplying another organization's invoice, journal, or attachment UUID | Every repository query filters by `(id, organizationId)` from authenticated `LedgerPrincipal`; `X-Organization-Id` verified against active membership | `SecurityAndIsolationTest` |
| Unauthorized Financial Posting | `VIEWER` or `FINANCE_OFFICER` calling `/journals/{id}/post` or `/periods/{id}/close` | Server-side `principal.requireWriteAccounting()` / `requireFinanceOperations()` checks on every mutating service method | `SecurityAndIsolationTest` |
| Self-Approval Fraud | Requester approving their own high-value expense | `ApprovalService` rejects `requesterId == principal.userId()` with 403 when `enforceSeparationOfDuties` is enabled | `SecurityAndIsolationTest` |
| CSRF on State-Changing APIs | Cross-site forged POST/DELETE using ambient browser cookie | `CookieCsrfTokenRepository` + `X-XSRF-TOKEN` required on authenticated mutations + `SameSite=Lax` cookies + strict CORS allowlist | `SecurityAndIsolationTest` |
| SQL Injection | Malicious search input in ledger, journal, or audit queries | Parameterized Spring Data JPQL queries exclusively; zero string-concatenated SQL | Code review + JPA queries |
| Duplicate Payment / Race Over-Allocation | Double-click retry or parallel payment requests against one open invoice | `Idempotency-Key` uniqueness constraint + `PESSIMISTIC_WRITE` row lock on target invoice + SQL `CHECK (outstanding_balance >= 0)` | `InvoiceAndPaymentWorkflowTest` |
| Silent Ledger Tampering | Editing or deleting posted journal lines to alter historical balances | `JournalService` rejects edits/deletes on `POSTED`/`REVERSED` entries; corrections require linked reversal journals | `AccountingEngineTest` |
| Path Traversal & Malicious Uploads | Uploading `../../etc/passwd` or executable scripts | Basename extraction, character allowlist, UUID stored filename, canonical `targetPath.startsWith(orgDir)` guard, MIME allowlist, `nosniff` | `AttachmentService` |
| Brute-Force Credential Stuffing | High-frequency login attempts | Sliding-window `LoginRateLimiter` + BCrypt(12) slow password hashing | `AuthService` |
| Demo Data Contamination | Public demo visitor accessing real workspaces | Separate `is_demo_user` & `is_demo` checks in `SessionAuthenticationFilter` and `AuthService.switchOrganization` | `SessionAuthenticationFilter` |

## Residual Limitations

- `LoginRateLimiter` is in-memory per JVM instance; multi-node horizontal deployments should place rate limiting at the reverse proxy or shared store.
- `audit_events` is append-oriented via application permissions and database role design, not cryptographically hash-chained.
- Production deployments require HTTPS termination so `LEDGER_SESSION_COOKIE_SECURE=true` protects cookies in transit.
