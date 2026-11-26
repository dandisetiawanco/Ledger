import Link from "next/link";
import { PublicShell } from "@/components/PublicShell";

export const metadata = { title: "Invoicing" };

export default function InvoicingPage() {
  return (
    <PublicShell>
      <div className="mx-auto max-w-5xl px-4 py-14 sm:px-6 space-y-10">
        <div className="max-w-2xl">
          <h1 className="font-editorial text-4xl font-semibold text-[var(--text-primary)]">
            Invoicing engineered for accounting integrity
          </h1>
          <p className="mt-3 text-base text-[var(--text-secondary)] leading-relaxed">
            Create clean A4 printable invoices, handle discounts and tax-exclusive or tax-inclusive
            rates, apply partial or multi-invoice payments, and issue credit notes without corrupting
            posted history.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-6 md:grid-cols-3">
          {[
            {
              title: "Authoritative Backend Totals",
              body: "Decimal.js calculates live previews as you type, while the Spring Boot backend independently recomputes subtotal, discounts, tax, base currency conversion, and balance due.",
            },
            {
              title: "Atomic Issuance & PDF Export",
              body: "Issuing an invoice locks the organization's yearly DocumentSequence row for gap-free numbering, posts the AR/Revenue/Tax journal atomically, and renders A4 PDFs with OpenPDF.",
            },
            {
              title: "Controlled Corrections",
              body: "Issued amounts cannot be silently edited. Corrections use linked Credit Notes or voiding reversals (when unpaid), preserving a complete audit trail for Accounts Receivable.",
            },
          ].map((card) => (
            <div
              key={card.title}
              className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6"
            >
              <h2 className="text-base font-semibold text-[var(--text-primary)]">{card.title}</h2>
              <p className="mt-2 text-sm text-[var(--text-secondary)] leading-relaxed">{card.body}</p>
            </div>
          ))}
        </div>

        <Link
          href="/demo"
          className="inline-flex min-h-[44px] items-center rounded-md bg-[var(--brand-forest)] px-5 text-sm font-medium text-[var(--text-on-brand)] hover:bg-[var(--brand-forest-hover)]"
        >
          Preview invoices and A4 PDFs in the demo
        </Link>
      </div>
    </PublicShell>
  );
}
