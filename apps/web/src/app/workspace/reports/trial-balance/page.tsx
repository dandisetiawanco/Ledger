"use client";

import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { formatMoney } from "@ledger/contracts";
import { apiFetch } from "@/lib/api";
import {
  Button,
  ErrorState,
  LoadingState,
  PageHeader,
  StatusBadge,
  TableRegion,
  inputClass,
} from "@/components/ui";

interface TrialBalanceReport {
  organizationName: string;
  currencyCode: string;
  asOfDate: string;
  totalDebit: string;
  totalCredit: string;
  balanced: boolean;
  rows: Array<{
    accountId: string;
    accountCode: string;
    accountName: string;
    accountType: string;
    debit: string;
    credit: string;
  }>;
}

export default function TrialBalancePage() {
  const [asOfDate, setAsOfDate] = useState(() => new Date().toISOString().slice(0, 10));

  const { data, isLoading, error, refetch } = useQuery<TrialBalanceReport>({
    queryKey: ["report", "trial-balance", asOfDate],
    queryFn: () => apiFetch<TrialBalanceReport>(`/api/v1/reports/trial-balance?asOfDate=${asOfDate}`),
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title="Trial Balance"
        description="Summary of net posted debit and credit balances across all accounts as of the selected date."
        actions={
          <>
            <input type="date" aria-label="As of date" value={asOfDate} onChange={(e) => setAsOfDate(e.target.value)} className={`${inputClass} !w-auto`} />
            <a
              href={`/api/v1/reports/trial-balance/export?asOfDate=${asOfDate}&format=csv`}
              className="inline-flex min-h-[42px] items-center rounded-md border border-[var(--border-strong)] bg-[var(--bg-surface)] px-3.5 text-sm font-medium hover:bg-[var(--bg-surface-subtle)]"
            >
              CSV
            </a>
            <a
              href={`/api/v1/reports/trial-balance/export?asOfDate=${asOfDate}&format=pdf`}
              target="_blank"
              rel="noreferrer"
              className="inline-flex min-h-[42px] items-center rounded-md bg-[var(--brand-forest)] px-3.5 text-sm font-medium text-[var(--text-on-brand)] hover:bg-[var(--brand-forest-hover)]"
            >
              A4 PDF
            </a>
            <Button variant="secondary" onClick={() => window.print()}>Print</Button>
          </>
        }
      />

      {isLoading ? <LoadingState label="Computing trial balance..." /> : null}
      {error ? (
        <ErrorState message={error instanceof Error ? error.message : "Failed to load trial balance"} onRetry={() => refetch()} />
      ) : null}

      {data ? (
        <>
          <div className="rounded-md border border-[var(--border-subtle)] bg-[var(--bg-surface)] px-4 py-3 flex flex-wrap items-center justify-between gap-2 text-sm">
            <span>
              <strong>{data.organizationName}</strong> · As of {data.asOfDate} ({data.currencyCode})
            </span>
            <StatusBadge status={data.balanced ? "BALANCED" : "UNBALANCED"} />
          </div>

          <TableRegion label="Trial Balance table">
            <table className="ledger-table">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Account</th>
                  <th>Classification</th>
                  <th className="!text-right">Debit ({data.currencyCode})</th>
                  <th className="!text-right">Credit ({data.currencyCode})</th>
                </tr>
              </thead>
              <tbody>
                {data.rows.map((r) => (
                  <tr key={r.accountId}>
                    <td className="font-mono-code text-xs text-[var(--text-secondary)]">{r.accountCode}</td>
                    <td className="font-medium">{r.accountName}</td>
                    <td className="text-xs text-[var(--text-secondary)]">{r.accountType}</td>
                    <td className="text-right num whitespace-nowrap">
                      {Number(r.debit) > 0 ? formatMoney(r.debit, data.currencyCode) : ""}
                    </td>
                    <td className="text-right num whitespace-nowrap">
                      {Number(r.credit) > 0 ? formatMoney(r.credit, data.currencyCode) : ""}
                    </td>
                  </tr>
                ))}
                <tr className="bg-[var(--bg-surface-subtle)] font-semibold">
                  <td colSpan={3}>Total Trial Balance</td>
                  <td className="text-right num">{formatMoney(data.totalDebit, data.currencyCode)}</td>
                  <td className="text-right num">{formatMoney(data.totalCredit, data.currencyCode)}</td>
                </tr>
              </tbody>
            </table>
          </TableRegion>
        </>
      ) : null}
    </div>
  );
}
