import Link from "next/link";
import { PublicShell } from "@/components/PublicShell";

export const metadata = { title: "Features" };

const CAPABILITY_GROUPS = [
  {
    group: "Core Bookkeeping",
    items: [
      "Five-classification Chart of Accounts with non-posting parent headers and contra accounts",
      "Double-entry journal engine enforcing balanced debits and credits inside ACID transactions",
      "Immutable posted journals corrected strictly through linked reversal entries",
      "Accounting period open, close, and audited reopen workflows",
      "General Ledger with opening balance, running balance, search, pagination, CSV, and PDF export",
    ],
  },
  {
    group: "Revenue & Payables Operations",
    items: [
      "Unified Customer and Supplier directory with ledger-derived balance summaries",
      "Service catalog linked to revenue accounts and configurable default tax rates",
      "Invoice lifecycle (Draft, Issued, Partially Paid, Paid, Overdue, Void) plus Credit Notes",
      "Customer receipts and supplier payments with idempotency keys and over-allocation locks",
      "Immediate operating expenses and unpaid supplier bills with configurable approval thresholds",
    ],
  },
  {
    group: "Banking, Budgets & Multi-Currency",
    items: [
      "Manual bank and cash accounts linked 1:1 to accounting accounts",
      "Configurable CSV bank statement importer with preview validation and SHA-256 deduplication",
      "Deterministic reconciliation matching with explicit user confirmation and adjustment entries",
      "Manual exchange rates preserved at posting time with realized FX gain and loss on settlement",
      "Annual and monthly account budgets comparing planned allocations against posted ledger actuals",
    ],
  },
  {
    group: "Financial Statements & Governance",
    items: [
      "Trial Balance verifying total debits equal total credits",
      "Profit and Loss Statement with comparative period columns",
      "Balance Sheet validating Assets equal Liabilities plus Equity",
      "Direct-method Cash Flow Statement excluding internal transfers between cash accounts",
      "Historical AR and AP Aging as of any reporting date reconciled to GL control accounts",
      "Role-based access control (6 roles), separation of duties, and append-oriented audit trail",
    ],
  },
];

export default function FeaturesPage() {
  return (
    <PublicShell>
      <div className="mx-auto max-w-5xl px-4 py-14 sm:px-6 space-y-10">
        <div className="max-w-2xl">
          <h1 className="font-editorial text-4xl font-semibold text-[var(--text-primary)]">
            Implemented platform capabilities
          </h1>
          <p className="mt-3 text-base text-[var(--text-secondary)]">
            Every item listed below is backed by tested Java Spring Boot domain services,
            versioned Flyway SQL migrations, and interactive workspace views.
          </p>
        </div>

        <div className="space-y-6">
          {CAPABILITY_GROUPS.map((section) => (
            <section
              key={section.group}
              className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6"
            >
              <h2 className="text-lg font-semibold text-[var(--text-primary)]">{section.group}</h2>
              <ul className="mt-4 grid grid-cols-1 gap-2.5 sm:grid-cols-2 text-sm text-[var(--text-secondary)]">
                {section.items.map((item) => (
                  <li key={item} className="flex items-start gap-2">
                    <span className="mt-1.5 h-1.5 w-1.5 rounded-full bg-[var(--brand-forest)] shrink-0" aria-hidden="true" />
                    <span>{item}</span>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>

        <div className="flex flex-wrap gap-3">
          <Link
            href="/demo"
            className="inline-flex min-h-[44px] items-center rounded-md bg-[var(--brand-forest)] px-5 text-sm font-medium text-[var(--text-on-brand)] hover:bg-[var(--brand-forest-hover)]"
          >
            Inspect live demo workspace
          </Link>
          <Link
            href="/documentation"
            className="inline-flex min-h-[44px] items-center rounded-md border border-[var(--border-strong)] bg-[var(--bg-surface)] px-5 text-sm font-medium text-[var(--text-primary)] hover:bg-[var(--bg-surface-subtle)]"
          >
            Read technical docs
          </Link>
        </div>
      </div>
    </PublicShell>
  );
}
