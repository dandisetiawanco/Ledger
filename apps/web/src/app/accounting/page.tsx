import Link from "next/link";
import { PublicShell } from "@/components/PublicShell";

export const metadata = { title: "Accounting Engine" };

export default function AccountingPage() {
  return (
    <PublicShell>
      <div className="mx-auto max-w-5xl px-4 py-14 sm:px-6 space-y-10">
        <div className="max-w-2xl">
          <h1 className="font-editorial text-4xl font-semibold text-[var(--text-primary)]">
            Deterministic double-entry accounting
          </h1>
          <p className="mt-3 text-base text-[var(--text-secondary)] leading-relaxed">
            Ledger enforces financial truth inside relational database transactions. Calculations
            use Java BigDecimal with explicit currency minor-unit rounding and never rely on
            floating-point numbers or editable balance fields.
          </p>
        </div>

        <div className="ledger-table-wrap">
          <table className="ledger-table">
            <thead>
              <tr>
                <th>Invariant</th>
                <th>Enforcement Layer</th>
                <th>Behavior on Violation</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td className="font-medium">Total Debits = Total Credits</td>
                <td>JournalService domain check inside ACID transaction</td>
                <td>Rejects with HTTP 422 UNBALANCED_JOURNAL and rolls back completely</td>
              </tr>
              <tr>
                <td className="font-medium">Single-sided journal lines</td>
                <td>Domain validation + SQL CHECK constraint</td>
                <td>Rejects lines with both positive debit and positive credit</td>
              </tr>
              <tr>
                <td className="font-medium">Posting account eligibility</td>
                <td>AccountService org-scoped lookup</td>
                <td>Blocks header accounts, archived accounts, and cross-organization IDs</td>
              </tr>
              <tr>
                <td className="font-medium">Posted immutability</td>
                <td>JournalService status guard</td>
                <td>Blocks editing or deleting posted entries; requires a linked reversal entry</td>
              </tr>
              <tr>
                <td className="font-medium">Period lock</td>
                <td>AccountingPeriod lookup with PESSIMISTIC_WRITE on close</td>
                <td>Rejects postings dated inside CLOSED periods until an Admin reopens with reason</td>
              </tr>
              <tr>
                <td className="font-medium">Idempotent automated posting</td>
                <td>UNIQUE(organization_id, business_event_key)</td>
                <td>Prevents duplicate journal creation for the same invoice, payment, or expense</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6 space-y-3 text-sm text-[var(--text-secondary)]">
          <h2 className="text-base font-semibold text-[var(--text-primary)]">
            Multi-currency &amp; rounding conventions
          </h2>
          <p>
            Database columns use NUMERIC(19, 4) for monetary amounts and NUMERIC(19, 8) for
            exchange rates. Zero-decimal currencies (IDR, JPY) round with HALF_UP to integer units;
            two-decimal currencies (USD, EUR, SGD, GBP, AUD) round to 2 decimal places. Exchange
            rates are preserved at transaction posting time, and realized FX differences post
            automatically to Realized FX Gain (4100) or Realized FX Loss (5100) on settlement.
          </p>
          <p>
            Documented scope boundary: period-end unrealized FX revaluation and statutory tax
            filing submissions are intentionally omitted from this milestone and documented in
            the engineering roadmap.
          </p>
        </div>

        <Link
          href="/demo"
          className="inline-flex min-h-[44px] items-center rounded-md bg-[var(--brand-forest)] px-5 text-sm font-medium text-[var(--text-on-brand)] hover:bg-[var(--brand-forest-hover)]"
        >
          Inspect posted journals in the demo
        </Link>
      </div>
    </PublicShell>
  );
}
