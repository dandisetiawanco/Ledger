"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { motion } from "motion/react";
import { Check, FileText, Landmark, Lock, Scale } from "lucide-react";
import { formatMoney } from "@ledger/contracts";
import { PublicShell } from "@/components/PublicShell";
import { Button, StatusBadge } from "@/components/ui";
import { apiFetch } from "@/lib/api";
import { useAuth } from "@/lib/providers";

interface DemoPreviewResponse {
  organizationName: string;
  demo: boolean;
  currencyCode: string;
  cashBalance: string;
  sixMonthRevenue: string;
  sixMonthExpenses: string;
  sixMonthNetProfit: string;
  outstandingReceivables: string;
  outstandingPayables: string;
  trialBalanceBalanced: boolean;
  trialBalanceTotal: string;
  balanceSheetBalanced: boolean;
  monthlyTrend: Array<{ month: string; revenue: string; expenses: string; netProfit: string }>;
}

const ACCOUNTING_EXAMPLES = [
  {
    id: "invoice",
    label: "Service invoice",
    summary: "Issuing an IDR 10,000,000 consulting invoice posts receivables and earned revenue atomically.",
    rows: [
      { code: "1100", account: "Accounts Receivable", debit: "IDR 10,000,000", credit: "" },
      { code: "4010", account: "Service Revenue", debit: "", credit: "IDR 10,000,000" },
    ],
    effect: "Outstanding receivable increases to IDR 10,000,000. Tax, when enabled, posts to 2100 Tax Payable and never to Revenue.",
  },
  {
    id: "payment",
    label: "Partial payment",
    summary: "Recording an IDR 4,000,000 bank transfer against the open invoice updates the balance to IDR 6,000,000.",
    rows: [
      { code: "1020", account: "Bank", debit: "IDR 4,000,000", credit: "" },
      { code: "1100", account: "Accounts Receivable", debit: "", credit: "IDR 4,000,000" },
    ],
    effect: "Invoice transitions to Partially Paid. Over-allocation is blocked by row-level transactional locking.",
  },
  {
    id: "expense",
    label: "Immediate expense",
    summary: "Recording a paid IDR 500,000 studio operating expense reduces the linked cash or bank account directly.",
    rows: [
      { code: "5070", account: "Operating Expense", debit: "IDR 500,000", credit: "" },
      { code: "1020", account: "Bank", debit: "", credit: "IDR 500,000" },
    ],
    effect: "Expense appears immediately on Profit and Loss and reduces closing cash on the Direct Cash Flow report.",
  },
  {
    id: "bill",
    label: "Supplier bill & settlement",
    summary: "Posting a supplier bill books the expense once and tracks liability in Accounts Payable until paid.",
    rows: [
      { code: "5010", account: "Contractor Expense (on bill post)", debit: "IDR 2,000,000", credit: "" },
      { code: "2010", account: "Accounts Payable (on bill post)", debit: "", credit: "IDR 2,000,000" },
      { code: "2010", account: "Accounts Payable (on payment)", debit: "IDR 2,000,000", credit: "" },
      { code: "1020", account: "Bank (on payment)", debit: "", credit: "IDR 2,000,000" },
    ],
    effect: "Settling the bill relieves Accounts Payable without double-counting the underlying operating expense.",
  },
];

export default function HomePage() {
  const router = useRouter();
  const { refreshAuth } = useAuth();
  const [selectedExample, setSelectedExample] = useState(ACCOUNTING_EXAMPLES[0].id);
  const [launchingDemo, setLaunchingDemo] = useState(false);

  const { data: preview } = useQuery<DemoPreviewResponse>({
    queryKey: ["public", "demo-preview"],
    queryFn: () => apiFetch<DemoPreviewResponse>("/api/v1/public/demo-preview"),
    retry: false,
  });

  const activeExample =
    ACCOUNTING_EXAMPLES.find((e) => e.id === selectedExample) ?? ACCOUNTING_EXAMPLES[0];

  async function handleLaunchDemo() {
    setLaunchingDemo(true);
    try {
      await apiFetch("/api/v1/demo/start", { method: "POST" });
      await refreshAuth();
      router.push("/workspace");
    } finally {
      setLaunchingDemo(false);
    }
  }

  return (
    <PublicShell>
      {/* Asymmetric Editorial Hero */}
      <section className="mx-auto max-w-6xl px-4 pt-12 pb-16 sm:px-6 lg:pt-20 lg:pb-24">
        <div className="grid grid-cols-1 gap-12 lg:grid-cols-12 lg:items-start">
          <motion.div
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.35 }}
            className="lg:col-span-7"
          >
            <h1 className="font-editorial text-4xl sm:text-5xl lg:text-[3.5rem] font-semibold leading-[1.08] tracking-tight text-[var(--text-primary)]">
              Clarity in every transaction.
            </h1>
            <p className="mt-5 text-lg text-[var(--text-secondary)] leading-relaxed max-w-xl">
              Manage invoices, track expenses, and understand your business finances through one
              connected accounting workspace.
            </p>

            <div className="mt-8 flex flex-wrap items-center gap-3">
              <Link
                href="/product"
                className="inline-flex min-h-[46px] items-center justify-center rounded-md bg-[var(--brand-forest)] px-5 text-sm font-medium text-[var(--text-on-brand)] hover:bg-[var(--brand-forest-hover)] transition-colors"
              >
                Explore Ledger
              </Link>
              <Button
                variant="secondary"
                onClick={handleLaunchDemo}
                disabled={launchingDemo}
                className="min-h-[46px] px-5"
              >
                {launchingDemo ? "Opening demo workspace..." : "Try the demo"}
              </Button>
            </div>

            <dl className="mt-10 grid grid-cols-1 gap-4 border-t border-[var(--border-subtle)] pt-6 sm:grid-cols-3 text-sm">
              <div>
                <dt className="text-xs font-semibold text-[var(--text-secondary)]">Accounting Core</dt>
                <dd className="mt-1 font-medium text-[var(--text-primary)]">Double-entry immutable ledger</dd>
              </div>
              <div>
                <dt className="text-xs font-semibold text-[var(--text-secondary)]">Arithmetic</dt>
                <dd className="mt-1 font-medium text-[var(--text-primary)]">Java BigDecimal &amp; Decimal.js</dd>
              </div>
              <div>
                <dt className="text-xs font-semibold text-[var(--text-secondary)]">Persistence</dt>
                <dd className="mt-1 font-medium text-[var(--text-primary)]">ACID PostgreSQL &amp; Flyway</dd>
              </div>
            </dl>
          </motion.div>

          {/* Live Ledger Verification Preview (derived from seeded synthetic demo org) */}
          <motion.div
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, delay: 0.08 }}
            className="lg:col-span-5 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6 shadow-sm"
          >
            <div className="flex items-start justify-between gap-3 border-b border-[var(--border-subtle)] pb-4">
              <div>
                <p className="text-xs font-semibold text-[var(--text-secondary)]">
                  Live demonstration ledger
                </p>
                <p className="mt-0.5 text-base font-semibold text-[var(--text-primary)]">
                  {preview?.organizationName ?? "Nusa Karsa Studio"}
                </p>
              </div>
              <StatusBadge status={preview?.trialBalanceBalanced !== false ? "BALANCED" : "UNBALANCED"} />
            </div>

            {preview ? (
              <div className="mt-4 space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div className="rounded-md bg-[var(--bg-surface-subtle)] p-3.5">
                    <p className="text-xs text-[var(--text-secondary)]">Cash &amp; bank balance</p>
                    <p className="mt-1 text-base font-semibold num text-[var(--text-primary)]">
                      {formatMoney(preview.cashBalance, preview.currencyCode)}
                    </p>
                  </div>
                  <div className="rounded-md bg-[var(--bg-surface-subtle)] p-3.5">
                    <p className="text-xs text-[var(--text-secondary)]">6-month net profit</p>
                    <p className="mt-1 text-base font-semibold num text-[var(--text-primary)]">
                      {formatMoney(preview.sixMonthNetProfit, preview.currencyCode)}
                    </p>
                  </div>
                </div>

                <div className="space-y-2 text-sm border-t border-[var(--border-subtle)] pt-4">
                  <div className="flex justify-between">
                    <span className="text-[var(--text-secondary)]">6-month posted revenue</span>
                    <span className="num font-medium">{formatMoney(preview.sixMonthRevenue, preview.currencyCode)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-[var(--text-secondary)]">6-month posted expenses</span>
                    <span className="num font-medium">{formatMoney(preview.sixMonthExpenses, preview.currencyCode)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-[var(--text-secondary)]">Open accounts receivable</span>
                    <span className="num font-medium">{formatMoney(preview.outstandingReceivables, preview.currencyCode)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-[var(--text-secondary)]">Open accounts payable</span>
                    <span className="num font-medium">{formatMoney(preview.outstandingPayables, preview.currencyCode)}</span>
                  </div>
                </div>

                <div className="rounded-md border border-[var(--border-subtle)] bg-[var(--bg-accent-soft)]/50 px-3.5 py-2.5 text-xs text-[var(--text-primary)] flex items-center justify-between gap-2">
                  <span>Trial balance &amp; balance sheet</span>
                  <span className="font-mono-code font-semibold num">
                    Dr = Cr ({formatMoney(preview.trialBalanceTotal, preview.currencyCode)})
                  </span>
                </div>
              </div>
            ) : (
              <div className="mt-4 space-y-3 text-sm text-[var(--text-secondary)]">
                <p>
                  Connects directly to the seeded Nusa Karsa Studio synthetic agency ledger when
                  the backend service is running on port 8082.
                </p>
                <p className="text-xs">
                  Figures are calculated live from posted double-entry journal lines.
                </p>
              </div>
            )}

            <div className="mt-5 pt-4 border-t border-[var(--border-subtle)] flex items-center justify-between text-xs text-[var(--text-secondary)]">
              <span>Synthetic demonstration data (6 months)</span>
              <Link href="/demo" className="font-semibold text-[var(--brand-forest)] hover:underline">
                Inspect demo details
              </Link>
            </div>
          </motion.div>
        </div>
      </section>

      {/* Interactive Double-Entry Walkthrough (Varied Rhythm: tabbed ledger inspector) */}
      <section className="border-y border-[var(--border-subtle)] bg-[var(--bg-surface)] py-16">
        <div className="mx-auto max-w-6xl px-4 sm:px-6">
          <div className="max-w-2xl">
            <h2 className="font-editorial text-3xl font-semibold tracking-tight text-[var(--text-primary)]">
              Every invoice, bill, and payment writes balanced journal lines.
            </h2>
            <p className="mt-3 text-base text-[var(--text-secondary)]">
              Account balances are never manually overwritten. Every business document posts an
              atomic double-entry journal, and errors are corrected through linked reversal entries.
            </p>
          </div>

          <div
            role="tablist"
            aria-label="Accounting workflow examples"
            className="mt-8 flex flex-wrap gap-2"
          >
            {ACCOUNTING_EXAMPLES.map((ex) => {
              const active = ex.id === activeExample.id;
              return (
                <button
                  key={ex.id}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  onClick={() => setSelectedExample(ex.id)}
                  className={`min-h-[44px] rounded-md px-4 text-sm font-medium transition-colors border ${
                    active
                      ? "bg-[var(--brand-forest)] text-[var(--text-on-brand)] border-[var(--brand-forest)]"
                      : "bg-[var(--bg-canvas)] text-[var(--text-primary)] border-[var(--border-subtle)] hover:bg-[var(--bg-surface-subtle)]"
                  }`}
                >
                  {ex.label}
                </button>
              );
            })}
          </div>

          <div className="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-12">
            <div className="lg:col-span-8 ledger-table-wrap">
              <table className="ledger-table">
                <thead>
                  <tr>
                    <th>Code</th>
                    <th>Account</th>
                    <th className="!text-right">Debit</th>
                    <th className="!text-right">Credit</th>
                  </tr>
                </thead>
                <tbody>
                  {activeExample.rows.map((r, idx) => (
                    <tr key={`${r.code}-${idx}`}>
                      <td className="font-mono-code text-xs text-[var(--text-secondary)]">{r.code}</td>
                      <td className="font-medium text-[var(--text-primary)]">{r.account}</td>
                      <td className="text-right num font-medium text-[var(--text-primary)]">{r.debit}</td>
                      <td className="text-right num font-medium text-[var(--text-primary)]">{r.credit}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="lg:col-span-4 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-canvas)] p-5 flex flex-col justify-between">
              <div>
                <p className="text-sm font-semibold text-[var(--text-primary)]">{activeExample.label}</p>
                <p className="mt-2 text-sm text-[var(--text-secondary)] leading-relaxed">
                  {activeExample.summary}
                </p>
                <p className="mt-4 text-xs text-[var(--text-secondary)] leading-relaxed border-t border-[var(--border-subtle)] pt-3">
                  {activeExample.effect}
                </p>
              </div>
              <Link
                href="/accounting"
                className="mt-5 text-sm font-semibold text-[var(--brand-forest)] hover:underline"
              >
                Read the accounting engine specification
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Editorial Capability Deep-Dives (Asymmetric two-column composition, not repetitive card grid) */}
      <section className="mx-auto max-w-6xl px-4 py-16 sm:px-6 space-y-14">
        <div className="grid grid-cols-1 gap-8 lg:grid-cols-12 items-start">
          <div className="lg:col-span-5">
            <div className="inline-flex h-10 w-10 items-center justify-center rounded-md bg-[var(--bg-accent-soft)] text-[var(--brand-forest)]">
              <FileText className="h-5 w-5" aria-hidden="true" />
            </div>
            <h2 className="mt-4 font-editorial text-2xl sm:text-3xl font-semibold text-[var(--text-primary)]">
              Invoicing, credit notes, and multi-currency settlements
            </h2>
            <p className="mt-3 text-sm sm:text-base text-[var(--text-secondary)] leading-relaxed">
              Create draft invoices with discounts and tax-exclusive or tax-inclusive pricing,
              generate A4 PDFs, allocate partial payments across invoices, and settle foreign
              currency invoices with automatic realized FX gain and loss postings.
            </p>
            <Link
              href="/invoicing"
              className="mt-4 inline-block text-sm font-semibold text-[var(--brand-forest)] hover:underline"
            >
              Inspect invoicing workflow
            </Link>
          </div>

          <div className="lg:col-span-7 grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-5">
              <Landmark className="h-5 w-5 text-[var(--brand-forest)]" aria-hidden="true" />
              <h3 className="mt-3 text-base font-semibold text-[var(--text-primary)]">
                Bank CSV imports &amp; reconciliation
              </h3>
              <p className="mt-1.5 text-sm text-[var(--text-secondary)]">
                Map custom CSV columns, preview validation errors, block duplicate lines using
                SHA-256 fingerprints, and reconcile against posted bank ledger lines with user
                confirmation.
              </p>
            </div>
            <div className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-5">
              <Scale className="h-5 w-5 text-[var(--brand-forest)]" aria-hidden="true" />
              <h3 className="mt-3 text-base font-semibold text-[var(--text-primary)]">
                Reconciled financial statements
              </h3>
              <p className="mt-1.5 text-sm text-[var(--text-secondary)]">
                Generate Trial Balance, Profit and Loss, Balance Sheet (verifying Assets equal
                Liabilities plus Equity), Direct Cash Flow, and historical AR/AP aging reconciled
                to GL control accounts.
              </p>
            </div>
          </div>
        </div>

        {/* Forest Integrity & Security Band */}
        <div className="rounded-xl bg-[#183D30] text-[#EDF2ED] p-8 sm:p-10">
          <div className="grid grid-cols-1 gap-8 lg:grid-cols-12 items-start">
            <div className="lg:col-span-5">
              <div className="inline-flex h-10 w-10 items-center justify-center rounded-md bg-[#234F3F] text-[#D6E8D4]">
                <Lock className="h-5 w-5" aria-hidden="true" />
              </div>
              <h2 className="mt-4 font-editorial text-2xl sm:text-3xl font-semibold text-white">
                Built around financial invariants and access control
              </h2>
              <p className="mt-3 text-sm text-[#D6E8D4] leading-relaxed">
                Ledger enforces multi-tenant organization isolation, role-based permissions,
                separation of duties on approvals, CSRF protection, and idempotent payment
                processing on the backend.
              </p>
            </div>
            <ul className="lg:col-span-7 grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
              {[
                "Zero float or double arithmetic in monetary calculations",
                "Immutable posted journals with linked reversal trails",
                "Row-level locks for concurrent numbering and payment allocation",
                "Accounting period closing guards with audited reopening",
                "HTTP-only session cookies, BCrypt hashing, and CSRF validation",
                "Authorized private document downloads with path-traversal protection",
              ].map((item) => (
                <li key={item} className="flex items-start gap-2.5 rounded-md bg-[#122E24] p-3.5">
                  <Check className="h-4 w-4 mt-0.5 shrink-0 text-[#B9D9B9]" aria-hidden="true" />
                  <span className="text-[#EDF2ED]">{item}</span>
                </li>
              ))}
            </ul>
          </div>
          <div className="mt-8 pt-6 border-t border-[#2A5948] flex flex-wrap items-center justify-between gap-4">
            <p className="text-xs text-[#D6E8D4]">
              Portfolio engineering project. Review the threat model and automated test suite in documentation.
            </p>
            <div className="flex flex-wrap gap-3">
              <Link
                href="/security"
                className="inline-flex min-h-[42px] items-center rounded-md border border-[#B9D9B9] px-4 text-sm font-medium text-white hover:bg-[#122E24]"
              >
                Security architecture
              </Link>
              <Link
                href="/register"
                className="inline-flex min-h-[42px] items-center rounded-md bg-[#F6F4EE] px-4 text-sm font-semibold text-[#183D30] hover:bg-white"
              >
                Create workspace
              </Link>
            </div>
          </div>
        </div>
      </section>
    </PublicShell>
  );
}
