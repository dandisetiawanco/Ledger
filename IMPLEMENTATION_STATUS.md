# Ledger Implementation Status

Verified implementation ledger and test record for **Ledger**.

## Current Milestone

v0.1.0 complete, locally verified (H2 profile), published to `https://github.com/dandisetiawanco/Ledger`, and verified green across all four GitHub Actions CI jobs (H2 tests, PostgreSQL 16 Testcontainers integration tests, frontend suite/build, secret hygiene, and Docker builds).

## Environment Facts (Verified)

- OS: macOS (Intel x86_64)
- Java: OpenJDK 21.0.13.1 (Temurin-21) at `/Users/dandisetiawan/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`
- Maven: Bundled Maven Wrapper (`backend/mvnw` -> Apache Maven 3.9.9, only-script distribution)
- Node.js: v22.23.5
- pnpm: 12.21.0
- Git: 2.50.1 (branch `main`)
- GitHub CLI: `/Users/dandisetiawan/.local/bin/gh` v2.105.0 (`gh auth status` not logged in; pushes authenticated via macOS `osxkeychain` credential helper)
- PostgreSQL: Verified in GitHub Actions CI via real PostgreSQL 16 (`postgres:16-alpine`) + Flyway + Testcontainers 1.21.4 (`PostgresIntegrationIT`)
- Docker: Verified in GitHub Actions CI (`Dockerfile.backend` and `Dockerfile.frontend` builds)
- Running Local Services: Frontend `http://localhost:3002`, Backend `http://localhost:8082` (H2 file-backed profile)

## Completed Implementation

- **Backend Modular Monolith (`backend/src/main/java/com/ledger/`)**: All 18 domain modules (`identity`, `organization`, `accounting`, `journal`, `contact`, `invoice`, `expense`, `payment`, `banking`, `reconciliation`, `budget`, `reporting`, `approval`, `audit`, `document`, `notification`, `demo`, `shared`).
- **Database & Migrations**: 34 normalized relational tables in `V1__init_schema.sql` and 7 seeded currencies in `V2__seed_currencies.sql` with Hibernate `ddl-auto: validate` enforced across H2 and PostgreSQL profiles.
- **PDF & CSV Exports**: Server-side A4 PDF generation (OpenPDF) for invoices, credit notes, payment receipts, and financial reports plus RFC-compliant CSV exports.
- **Frontend & Shared Packages (`apps/web/`, `packages/contracts/`, `packages/ui/`)**: 10 public pages and 27 authenticated workspace routes, WCAG AA light and dark themes, `decimal.js` client previews, Recharts visualizations, native `<dialog>` modals, and keyboard-scrollable tables.
- **Branding, Diagrams & Documentation**: 8 brand SVGs in `assets/branding/`, 3 architecture/ERD/workflow SVG diagrams in `assets/diagrams/`, 14 real browser screenshots in `assets/screenshots/`, 13 engineering documents in `docs/`, `CHANGELOG.md`, and `README.md`.
- **Infrastructure & CI**: `Dockerfile.backend`, `Dockerfile.frontend`, `.dockerignore`, `docker-compose.yml`, `infrastructure/nginx/nginx.conf`, `Makefile`, `scripts/dev.sh`, `scripts/stop.sh`, and `.github/workflows/ci.yml`.

## Verified Functionality

- **Backend Unit, Invariant & Security Suite (H2 PostgreSQL-compatibility mode)**: `cd backend && ./mvnw -B test` -> **12/12 passed, 0 failures, 0 errors, 0 skipped**:
  - `AccountingEngineTest` (6 tests): balanced posting, unbalanced rejection, header-account rejection, immutable posted edit rejection, linked reversal trails, closed-period guard + audited reopening.
  - `InvoiceAndPaymentWorkflowTest` (3 tests): tax-exclusive/tax-inclusive invoice math, partial payments, over-allocation blocking, idempotency replay/conflict rejection, unallocated customer credit liability, credit notes, concurrent 6-thread gap-free numbering, A4 PDF generation.
  - `FinancialReportingAndBankingTest` (2 tests): USD invoice + realized FX gain/loss, historical AR/AP aging reconciled with GL control accounts, Direct Cash Flow excluding internal bank transfers, CSV import SHA-256 duplicate blocking, bank reconciliation + unmatch, full 6-month synthetic demo seed reconciliation (`Dr = Cr = IDR 412,679,000`, `Assets = Liabilities + Equity = IDR 326,229,000`).
  - `SecurityAndIsolationTest` (1 MockMvc end-to-end test): registration, session cookie auth, CSRF enforcement, RBAC denial, cross-organization isolation, separation of duties on approvals, logout revocation.
- **PostgreSQL 16 Integration Suite (GitHub Actions CI Run `45797543103`, commit `d21fc0f`)**: `PostgresIntegrationIT` PASSED against `postgres:16-alpine` via Testcontainers 1.21.4 + `@ActiveProfiles("postgres")`, verifying Flyway migrations, Hibernate `ddl-auto: validate`, demo seed postings, Trial Balance, Balance Sheet, Direct Cash Flow, AR/AP GL reconciliation, and nullable JPQL search queries.
- **Backend Production JAR & Docker Image**: Local `./mvnw -B package -DskipTests` -> `BUILD SUCCESS`; GitHub Actions `Docker Image Build Verification` (`Dockerfile.backend` & `Dockerfile.frontend`) -> **SUCCESS**.
- **Frontend Quality & Build (Local & CI Run `45797543103`)**:
  - `pnpm typecheck:web` -> PASS (0 errors)
  - `pnpm lint:web` -> PASS (0 ESLint warnings or errors)
  - `pnpm test:web` -> PASS (5/5 Vitest tests)
  - `pnpm build:web` -> PASS (all 42 App Router routes compiled cleanly)
- **Secret & Dependency Hygiene Check (Local & CI Run `45797543103`)**: **SUCCESS** (no committed `.env`, private keys, or secrets).
- **Live Browser Walkthrough & Responsive Verification**:
  - Verified running application at `http://localhost:3002` backed by `http://localhost:8082` (H2 profile).
  - Verified Trial Balance (`Dr = Cr = IDR 412,679,000`), Balance Sheet (`Assets = Liabilities + Equity = IDR 326,229,000`), Cash Flow reconciliation, live A4 PDF download (`%PDF-1.5` HTTP 200) for invoices and reports, dark/light theme toggling with cross-tab storage sync, manual journal live `UNBALANCED` -> `BALANCED` state transition, mobile menu drawer, tap targets >= 44px, and zero horizontal overflow across `1440px`, `1024px`, `768px`, and `390px`.
  - Captured and visually inspected all 14 real screenshots in `assets/screenshots/`.
- **WCAG AA Color Contrast**: Verified all light and dark semantic color pairings (`7.18:1` to `15.66:1`, all PASS).

## Verified GitHub Actions CI Run

- Workflow: `Ledger CI` (`.github/workflows/ci.yml`)
- Verified Run: `https://github.com/dandisetiawanco/Ledger/actions/runs/45797543103` (commit `d21fc0f`)
  - `Backend Build, Unit & PostgreSQL Integration Tests`: `success`
  - `Frontend Typecheck, Lint, Unit Tests & Production Build`: `success`
  - `Secret & Dependency Hygiene Check`: `success`
  - `Docker Image Build Verification`: `success`

## Remaining Manual Configuration

- Repository description and GitHub topics could not be applied via `gh repo edit` because `gh auth status` is not signed in locally; run `gh auth login` and apply the description and 13 topics (command included in final delivery report) or update About settings on GitHub.

## Last Verified CI Commit

`d21fc0f` (`fix(ci): upgrade Testcontainers to 1.21.4 for Docker API 1.44 compatibility`)

## Development Commands

- `make setup`
- `make dev`
- `make test`
- `make lint`
- `make build`
- `make stop`
