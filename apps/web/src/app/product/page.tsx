import Link from "next/link";
import { PublicShell } from "@/components/PublicShell";

export const metadata = { title: "Product" };

export default function ProductPage() {
  return (
    <PublicShell>
      <div className="mx-auto max-w-5xl px-4 py-14 sm:px-6 space-y-12">
        <div className="max-w-2xl">
          <h1 className="font-editorial text-4xl font-semibold text-[var(--text-primary)]">
            One connected financial workspace for service businesses.
          </h1>
          <p className="mt-4 text-base text-[var(--text-secondary)] leading-relaxed">
            Ledger links day-to-day invoicing, expense tracking, payments, and bank reconciliation
            directly to an underlying double-entry general ledger. Every operational record updates
            financial statements deterministically.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
          {[
            {
              title: "General Ledger & Chart of Accounts",
              body: "Structured Assets, Liabilities, Equity, Revenue, and Expense accounts with normal balance conventions, parent headers, immutable posted journals, and linked reversal entries.",
              href: "/accounting",
              linkLabel: "Inspect accounting engine",
            },
            {
              title: "Invoicing, Credit Notes & Receivables",
              body: "Draft and issue numbered invoices, calculate discounts and exclusive or inclusive tax with BigDecimal, export A4 PDFs, allocate partial payments, and track historical AR aging.",
              href: "/invoicing",
              linkLabel: "Inspect invoicing",
            },
            {
              title: "Expenses, Supplier Bills & Approvals",
              body: "Distinguish immediate cash expenses from unpaid supplier bills so liabilities settle accurately through Accounts Payable without double-counting costs. Enforce separation of duties on approvals.",
              href: "/features",
              linkLabel: "View all modules",
            },
            {
              title: "Bank Statements, Budgets & Statements",
              body: "Import mapped CSV bank statements with deterministic SHA-256 duplicate protection, reconcile ledger lines, compare budgets against posted actuals, and export Trial Balance, P&L, Balance Sheet, and Cash Flow reports.",
              href: "/demo",
              linkLabel: "Open interactive demo",
            },
          ].map((item) => (
            <div
              key={item.title}
              className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6 flex flex-col justify-between"
            >
              <div>
                <h2 className="text-lg font-semibold text-[var(--text-primary)]">{item.title}</h2>
                <p className="mt-2.5 text-sm text-[var(--text-secondary)] leading-relaxed">
                  {item.body}
                </p>
              </div>
              <Link
                href={item.href}
                className="mt-5 inline-block text-sm font-semibold text-[var(--brand-forest)] hover:underline"
              >
                {item.linkLabel}
              </Link>
            </div>
          ))}
        </div>
      </div>
    </PublicShell>
  );
}
