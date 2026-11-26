# Roadmap & Known Limitations

## Verified Current Scope (v0.1.0)

- Double-entry bookkeeping engine with immutable posted journals, reversals, and period locking
- Invoicing, discounts, tax-exclusive/inclusive calculations, credit notes, and OpenPDF A4 exports
- Customer receipts, supplier bill settlements, idempotency keys, and realized FX gain/loss
- Expenses, supplier bills, configurable approval thresholds, and separation of duties
- Manual bank accounts, mapped CSV imports with SHA-256 deduplication, and deterministic reconciliation
- Trial Balance, Profit & Loss, Balance Sheet, Direct Cash Flow, Historical AR/AP Aging, and Budgets
- Multi-tenant RBAC, HTTP-only sessions, CSRF protection, private attachments, and audit trail

## Documented Limitations & Future Milestones

1. **Unrealized Foreign Exchange Revaluation**: Historical exchange rates and realized FX gains/losses on settlement are implemented. Period-end unrealized FX revaluation for open foreign-currency receivables/payables is planned for a future milestone.
2. **Statutory Year-End Closing Entries**: Current-period net earnings are automatically aggregated onto the Balance Sheet; automated closing journals transferring revenue/expense accounts into Retained Earnings and statutory tax filings are out of scope for v0.1.0.
3. **Inventory Valuation & COGS**: The product catalog targets service-based businesses; perpetual inventory and FIFO/weighted-average COGS are intentionally omitted.
4. **External Integrations**: Live banking feeds, SMTP/transactional email delivery, and distributed Redis rate limiting are optional future integrations rather than local prerequisites.
