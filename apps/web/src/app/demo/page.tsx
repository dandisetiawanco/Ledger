"use client";

import React, { useState } from "react";
import { useRouter } from "next/navigation";
import { PublicShell } from "@/components/PublicShell";
import { Button } from "@/components/ui";
import { apiFetch, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/providers";

export default function DemoPage() {
  const router = useRouter();
  const { refreshAuth } = useAuth();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function launchDemo() {
    setLoading(true);
    setError(null);
    try {
      await apiFetch("/api/v1/demo/start", { method: "POST" });
      await refreshAuth();
      router.push("/workspace");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not launch demo session");
    } finally {
      setLoading(false);
    }
  }

  return (
    <PublicShell>
      <div className="mx-auto max-w-4xl px-4 py-14 sm:px-6">
        <div className="max-w-2xl">
          <h1 className="font-editorial text-4xl font-semibold text-[var(--text-primary)]">
            Interactive demonstration workspace
          </h1>
          <p className="mt-3 text-base text-[var(--text-secondary)] leading-relaxed">
            Inspect six months of deterministic accounting data for Nusa Karsa Studio, a fictional
            Jakarta digital agency. Every balance, chart, invoice, and financial statement is
            generated through the real double-entry backend.
          </p>
        </div>

        {error ? (
          <div
            role="alert"
            className="mt-6 rounded-md border border-[var(--status-overdue-text)] bg-[var(--status-overdue-bg)] px-4 py-3 text-sm text-[var(--status-overdue-text)]"
          >
            {error}
          </div>
        ) : null}

        <div className="mt-8 grid grid-cols-1 gap-6 md:grid-cols-2">
          <div className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6">
            <h2 className="text-base font-semibold text-[var(--text-primary)]">
              What is included in the sample dataset
            </h2>
            <ul className="mt-4 space-y-2.5 text-sm text-[var(--text-secondary)]">
              <li>Complete Chart of Accounts (Assets, Liabilities, Equity, Revenue, Expenses)</li>
              <li>Six months of paid, partially paid, overdue, draft, and multi-currency USD invoices</li>
              <li>Realized foreign exchange gain posted automatically on USD settlement</li>
              <li>Credit note adjustment reducing Accounts Receivable and Service Revenue</li>
              <li>Immediate operating expenses, unpaid supplier bills, and partial bill settlements</li>
              <li>Pending approval request submitted by a separate finance officer for review</li>
              <li>Imported CSV bank statement lines with deterministic reconciliation matches</li>
              <li>Operating expense budget comparing planned allocations against posted ledger actuals</li>
            </ul>
          </div>

          <div className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6 flex flex-col justify-between">
            <div>
              <h2 className="text-base font-semibold text-[var(--text-primary)]">
                Isolation guarantees
              </h2>
              <p className="mt-3 text-sm text-[var(--text-secondary)] leading-relaxed">
                Demo sessions authenticate as an isolated demonstration user restricted strictly to
                the synthetic Nusa Karsa Studio organization. Demo users cannot access private user
                organizations, download private attachments, or send external emails or payments.
              </p>
              <div className="mt-5 rounded-md bg-[var(--bg-surface-subtle)] p-4 text-xs text-[var(--text-secondary)] space-y-1">
                <p><span className="font-semibold text-[var(--text-primary)]">Organization:</span> Nusa Karsa Studio (Synthetic Demo)</p>
                <p><span className="font-semibold text-[var(--text-primary)]">Base Currency:</span> IDR (Asia/Jakarta, Jan to Dec)</p>
                <p><span className="font-semibold text-[var(--text-primary)]">Role:</span> Demo Owner</p>
              </div>
            </div>

            <div className="mt-6">
              <Button onClick={launchDemo} disabled={loading} className="w-full">
                {loading ? "Starting isolated demo session..." : "Launch Nusa Karsa Studio demo"}
              </Button>
            </div>
          </div>
        </div>
      </div>
    </PublicShell>
  );
}
