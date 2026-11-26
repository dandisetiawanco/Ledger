import Link from "next/link";
import { PageHeader } from "@/components/ui";

const REPORTS = [
  {
    href: "/workspace/reports/trial-balance",
    title: "Trial Balance",
    desc: "Verifies total posted debits equal total posted credits across every active account as of any date.",
  },
  {
    href: "/workspace/reports/profit-loss",
    title: "Profit and Loss Statement",
    desc: "Summarizes posted revenue, operating expenses, and net profit over any date range with comparative periods.",
  },
  {
    href: "/workspace/reports/balance-sheet",
    title: "Balance Sheet",
    desc: "Presents Assets, Liabilities, Equity, and current-period unclosed earnings, validating Assets = Liabilities + Equity.",
  },
  {
    href: "/workspace/reports/cash-flow",
    title: "Direct Cash Flow Statement",
    desc: "Classifies cash and bank inflows and outflows into Operating, Investing, and Financing while excluding internal transfers.",
  },
  {
    href: "/workspace/general-ledger",
    title: "General Ledger",
    desc: "Detailed account-level posted transaction register with opening balance, running balance, CSV, and PDF exports.",
  },
  {
    href: "/workspace/receivables",
    title: "Accounts Receivable Aging",
    desc: "Historical customer aging buckets reconciled directly to the 1100 Accounts Receivable GL control account.",
  },
  {
    href: "/workspace/payables",
    title: "Accounts Payable Aging",
    desc: "Historical supplier payable obligations reconciled directly to the 2010 Accounts Payable GL control account.",
  },
  {
    href: "/workspace/budgets",
    title: "Budget vs Actual",
    desc: "Account-level planned spending compared against actual posted general ledger expense totals.",
  },
];

export default function ReportsHubPage() {
  return (
    <div>
      <PageHeader
        title="Financial Reports"
        description="Every statement is calculated dynamically from posted double-entry journal lines and supports CSV and A4 PDF export."
      />
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {REPORTS.map((r) => (
          <Link
            key={r.href}
            href={r.href}
            className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6 hover:border-[var(--brand-forest)] transition-colors block"
          >
            <h2 className="text-base font-semibold text-[var(--text-primary)]">{r.title}</h2>
            <p className="mt-1.5 text-sm text-[var(--text-secondary)] leading-relaxed">{r.desc}</p>
            <span className="mt-4 inline-block text-xs font-semibold text-[var(--brand-forest)]">
              Open statement
            </span>
          </Link>
        ))}
      </div>
    </div>
  );
}
