# Changelog

All notable changes to Ledger are documented in this file.

## [0.1.0] - 2026-11-26

### Added
- Enterprise modular monolith backend using Java 21, Spring Boot, Flyway, Spring Security, and JPA/Hibernate.
- Double-entry accounting engine with BigDecimal arithmetic, immutable posted journals, reversals, period locking, and general ledger.
- Invoicing, credit notes, A4 PDF generation, payment allocations, expense and supplier bill workflows, and configurable approvals.
- Bank account management, CSV statement imports with duplicate fingerprinting, and deterministic bank reconciliation.
- Financial reports (Trial Balance, Profit and Loss, Balance Sheet, Direct Cash Flow, Historical AR/AP Aging, Budget vs Actual) with CSV and PDF export.
- Multi-currency transaction support with preserved historical exchange rates and realized foreign exchange gains/losses.
- Next.js App Router frontend with original Ledger brand identity, public website, full accounting workspace, and isolated synthetic demo mode.
