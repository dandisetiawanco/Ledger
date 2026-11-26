# Automated Testing Strategy

Ledger relies on meaningful automated verification across accounting invariants, business workflows, security controls, PostgreSQL integration, and frontend financial math.

## Backend Test Suites (`backend/src/test/java/com/ledger/`)

1. **`AccountingEngineTest`** (`accounting/AccountingEngineTest.java`):
   - Balanced journal posting and normal balance derivation
   - Rejection and atomic rollback of unbalanced journals
   - Rejection of non-posting header accounts and cross-organization accounts
   - Posted journal immutability (edit/delete blocked) and linked reversal zeroing effect
   - Closed accounting period rejection and audited reopening
   - Duplicate `businessEventKey` prevention and deletion guard on referenced accounts
2. **`InvoiceAndPaymentWorkflowTest`** (`invoice/InvoiceAndPaymentWorkflowTest.java`):
   - All five canonical accounting scenarios (Service Invoice IDR 10M, Partial Payment IDR 4M, Immediate Expense IDR 500k, Supplier Bill IDR 2M, Supplier Payment IDR 2M without double counting)
   - Line discounts, tax-exclusive and tax-inclusive calculations (tax isolated in Tax Payable)
   - Credit notes, over-allocation rejection, legitimate overpayment credited to Customer Deposits
   - Duplicate payment idempotency via `Idempotency-Key`
   - Multithreaded concurrent invoice issuance verifying gap-free unique numbering under `PESSIMISTIC_WRITE`
   - Valid A4 `%PDF-` document rendering via OpenPDF
3. **`FinancialReportingAndBankingTest`** (`reporting/FinancialReportingAndBankingTest.java`):
   - Multi-currency USD invoice settled at a changed IDR exchange rate with Realized FX Gain posting
   - Historical AR Aging on past dates reconciled to GL `1100 Accounts Receivable`
   - Trial Balance equality (`Dr == Cr`), Balance Sheet equation (`Assets == Liabilities + Equity`), Profit & Loss accuracy
   - Direct Cash Flow reconciliation and exclusion of internal Bank <-> Cash transfers
   - Zero-budget category handling without division errors
   - Bank CSV import, SHA-256 duplicate detection, deterministic matching, duplicate match rejection, and adjustment journal reconciliation
   - Full 6-month `DemoSeedService` reconciliation across all financial statements
4. **`SecurityAndIsolationTest`** (`security/SecurityAndIsolationTest.java`, Spring `MockMvc`):
   - Registration, login, HttpOnly cookie issuance, logout revocation
   - CSRF rejection (403) without `X-XSRF-TOKEN` and success with valid token
   - Viewer RBAC read-only enforcement
   - Cross-organization isolation on private attachment downloads and spoofed `X-Organization-Id` headers
   - Separation of duties blocking self-approval
5. **`PostgresIntegrationIT`** (`integration/PostgresIntegrationIT.java`, `@Tag("postgres-integration")`):
   - Runs Flyway migrations, Hibernate `ddl-auto: validate`, `DemoSeedService`, and financial reconciliation checks against a real `postgres:16-alpine` Testcontainers instance in Docker-enabled environments and GitHub Actions CI.

## Frontend Unit Tests (`apps/web/src/test/financial.test.tsx`)

Executed with Vitest + React Testing Library:
- Decimal.js invoice previews (discounts, tax-exclusive, tax-inclusive, `0.10 + 0.20 == 0.30` USD precision)
- RBAC role permission helpers (`Viewer` blocked from accounting/finance/approval actions)
- Accessible UI states (`LoadingState`, `EmptyState`, `ErrorState`, `StatusBadge`)

## Running Locally

```bash
make test          # Backend H2 tests + Frontend Vitest suite
make lint          # TypeScript strict check + ESLint
make build         # Spring Boot jar + Next.js production build
```
