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

interface BalanceSheetReport {
  organizationName: string;
  currencyCode: string;
  asOfDate: string;
  assets: Array<{ accountCode: string; accountName: string; amount: string }>;
  liabilities: Array<{ accountCode: string; accountName: string; amount: string }>;
  equity: Array<{ accountCode: string; accountName: string; amount: string }>;
  totalAssets: string;
  totalLiabilities: string;
  totalEquity: string;
  totalLiabilitiesAndEquity: string;
  equationDifference: string;
  balanced: boolean;
}

export default function BalanceSheetPage() {
  const [asOfDate, setAsOfDate] = useState(() => new Date().toISOString().slice(0, 10));

  const { data, isLoading, error, refetch } = useQuery<BalanceSheetReport>({
    queryKey: ["report", "balance-sheet", asOfDate],
    queryFn: () => apiFetch<BalanceSheetReport>(`/api/v1/reports/balance-sheet?asOfDate=${asOfDate}`),
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title="Balance Sheet"
        description="Statement of financial position verifying the accounting equation: Assets = Liabilities + Equity."
        actions={
          <>
            <input type="date" aria-label="As of date" value={asOfDate} onChange={(e) => setAsOfDate(e.target.value)} className={`${inputClass} !w-auto`} />
            <a
              href={`/api/v1/reports/balance-sheet/export?asOfDate=${asOfDate}&format=csv`}
              className="inline-flex min-h-[42px] items-center rounded-md border border-[var(--border-strong)] bg-[var(--bg-surface)] px-3.5 text-sm font-medium hover:bg-[var(--bg-surface-subtle)]"
            >
              CSV
            </a>
            <a
              href={`/api/v1/reports/balance-sheet/export?asOfDate=${asOfDate}&format=pdf`}
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

      {isLoading ? <LoadingState label="Computing Balance Sheet..." /> : null}
      {error ? (
        <ErrorState message={error instanceof Error ? error.message : "Unable to load Balance Sheet"} onRetry={() => refetch()} />
      ) : null}

      {data ? (
        <>
          <div className="rounded-md border border-[var(--border-subtle)] bg-[var(--bg-surface)] px-4 py-3 flex flex-wrap items-center justify-between gap-2 text-sm">
            <span className="num">
              Assets ({formatMoney(data.totalAssets, data.currencyCode)}) = Liabilities + Equity (
              {formatMoney(data.totalLiabilitiesAndEquity, data.currencyCode)}) · Difference:{" "}
              {formatMoney(data.equationDifference, data.currencyCode)}
            </span>
            <StatusBadge status={data.balanced ? "BALANCED" : "UNBALANCED"} />
          </div>

          <TableRegion label="Balance Sheet">
            <table className="ledger-table">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Account</th>
                  <th className="!text-right">Balance ({data.currencyCode})</th>
                </tr>
              </thead>
              <tbody>
                <tr className="bg-[var(--bg-surface-subtle)] font-semibold"><td colSpan={3}>Assets</td></tr>
                {data.assets.map((l) => (
                  <tr key={l.accountCode}>
                    <td className="font-mono-code text-xs text-[var(--text-secondary)]">{l.accountCode}</td>
                    <td>{l.accountName}</td>
                    <td className="text-right num">{formatMoney(l.amount, data.currencyCode)}</td>
                  </tr>
                ))}
                <tr className="font-semibold bg-[var(--bg-accent-soft)]/60">
                  <td colSpan={2}>Total Assets</td>
                  <td className="text-right num">{formatMoney(data.totalAssets, data.currencyCode)}</td>
                </tr>

                <tr className="bg-[var(--bg-surface-subtle)] font-semibold"><td colSpan={3}>Liabilities</td></tr>
                {data.liabilities.map((l) => (
                  <tr key={l.accountCode}>
                    <td className="font-mono-code text-xs text-[var(--text-secondary)]">{l.accountCode}</td>
                    <td>{l.accountName}</td>
                    <td className="text-right num">{formatMoney(l.amount, data.currencyCode)}</td>
                  </tr>
                ))}
                <tr className="font-semibold">
                  <td colSpan={2}>Total Liabilities</td>
                  <td className="text-right num">{formatMoney(data.totalLiabilities, data.currencyCode)}</td>
                </tr>

                <tr className="bg-[var(--bg-surface-subtle)] font-semibold"><td colSpan={3}>Equity</td></tr>
                {data.equity.map((l) => (
                  <tr key={l.accountCode}>
                    <td className="font-mono-code text-xs text-[var(--text-secondary)]">{l.accountCode}</td>
                    <td>{l.accountName}</td>
                    <td className="text-right num">{formatMoney(l.amount, data.currencyCode)}</td>
                  </tr>
                ))}
                <tr className="font-semibold">
                  <td colSpan={2}>Total Equity</td>
                  <td className="text-right num">{formatMoney(data.totalEquity, data.currencyCode)}</td>
                </tr>

                <tr className="bg-[var(--bg-accent-soft)] font-semibold text-base text-[var(--brand-forest)]">
                  <td colSpan={2}>Total Liabilities &amp; Equity</td>
                  <td className="text-right num">{formatMoney(data.totalLiabilitiesAndEquity, data.currencyCode)}</td>
                </tr>
              </tbody>
            </table>
          </TableRegion>
        </>
      ) : null}
    </div>
  );
}
