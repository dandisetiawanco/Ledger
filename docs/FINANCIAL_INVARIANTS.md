# Financial Invariants & Arithmetic Conventions

Financial accuracy in Ledger is deterministic, reproducible, and independently verified by automated tests.

## 1. Decimal Arithmetic & Rounding Rules

- **No floating-point money**: Java `float` and `double` are strictly forbidden in financial domain models, services, and reports. All monetary values use `java.math.BigDecimal`.
- **Database precision**: Monetary columns use `NUMERIC(19, 4)`; tax and discount percentages use `NUMERIC(7, 4)`; foreign exchange rates use `NUMERIC(19, 8)`.
- **Currency-specific minor units (`MoneyUtil`)**:
  - Zero-decimal currencies (`IDR`, `JPY`) round transactional line results to `0` decimal places using `RoundingMode.HALF_UP`.
  - Two-decimal currencies (`USD`, `EUR`, `SGD`, `GBP`, `AUD`) round to `2` decimal places using `RoundingMode.HALF_UP`.
  - Canonical JSON transport serializes monetary strings with scale 2 (e.g. `"10000000.00"` or `"100.50"`), preventing JavaScript IEEE-754 floating-point precision loss.
- **Authoritative backend calculation**: The frontend uses `decimal.js` strictly for interactive previews. The backend independently recalculates invoice subtotals, discounts, tax-exclusive and tax-inclusive splits, totals, base currency conversions, outstanding balances, and financial reports.

## 2. Core Financial Invariants

1. **Double-Entry Equality**: For every `POSTED` journal entry, $\sum \text{debit\_amount} = \sum \text{credit\_amount} > 0$.
2. **Single-Sided Lines**: Enforced in Java and via SQL `CHECK (NOT (debit_amount > 0 AND credit_amount > 0))`.
3. **Trial Balance Equality**: Across all active posting accounts as of any date, total debit balances equal total credit balances.
4. **Balance Sheet Equation**: As of any reporting date, $\text{Assets} = \text{Liabilities} + \text{Equity}$ (including current-period unclosed earnings) with `equationDifference == "0.00"`.
5. **Direct Cash Flow Reconciliation**: $\text{Opening Cash} + \text{Net Operating} + \text{Net Investing} + \text{Net Financing} = \text{Closing Cash}$, with internal transfers between `CASH` accounts explicitly excluded from net cash flow.
6. **Subledger-to-GL Reconciliation**: Historical Accounts Receivable and Accounts Payable aging subledger totals reconcile directly against `1100 Accounts Receivable` and `2010 Accounts Payable` control accounts as of any reporting date.
7. **Non-Negative Outstanding Balances**: Enforced in domain logic and via SQL `CHECK (outstanding_balance >= 0)` on `invoices` and `expenses`. Legitimate customer excess receipts post to `2200 Customer Deposits & Unallocated Credits`.
8. **Tax Isolation**: Sales tax collected on invoices posts to `2100 Tax Payable`, never to a Revenue account. Recoverable tax on expenses posts to `1200 Input Tax Receivable`.

## 3. Concurrency & Idempotency Strategies

| Critical Operation | Concurrency Control | Reason |
|---|---|---|
| Document numbering (`INV-2026-0001`) | `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`) on `document_sequences` + `REQUIRES_NEW` bootstrap | Guarantees gap-free unique sequential numbers under parallel requests |
| Payment allocation | `PESSIMISTIC_WRITE` on target `invoices`/`expenses` + `@Version` optimistic locking | Prevents concurrent requests from over-allocating an invoice beyond its balance |
| Duplicate payment protection | `Idempotency-Key` header + `UNIQUE(organization_id, idempotency_key)` | Safely returns the original payment on network retries |
| Automated journal posting | `UNIQUE(organization_id, business_event_key)` | Guarantees an invoice, payment, or expense cannot generate duplicate journal entries |
| Bank reconciliation | `PESSIMISTIC_WRITE` on `bank_statement_lines` + `UNIQUE(active_bank_line_id)` & `UNIQUE(active_journal_line_id)` | Prevents double-matching while preserving historical unmatched records |
| Period closing | `PESSIMISTIC_WRITE` on `accounting_periods` + pending-journal count check | Prevents concurrent draft postings from racing period closure |

## 4. Multi-Currency Scope

Transactions store `currency_code`, `exchange_rate`, `exchange_rate_date`, and base-currency amounts. Historical exchange rates are preserved at posting time and never retroactively altered. When a foreign-currency invoice or supplier bill is settled at a different exchange rate, the realized difference posts atomically to `4100 Realized Foreign Exchange Gain` or `5100 Realized Foreign Exchange Loss`. Period-end **unrealized** foreign exchange revaluation is not implemented in this milestone.
