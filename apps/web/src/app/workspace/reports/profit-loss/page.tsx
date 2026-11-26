"use client";

import React, { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { formatMoney } from "@ledger/contracts";
import { apiFetch } from "@/lib/api";
import {
  Button,
  ErrorState,
  LoadingState,
  PageHeader,
  TableRegion,
  inputClass,
} from "@/components/ui";

interface ProfitLossReport {
  organizationName: string;
  currencyCode: string;
  fromDate: string;
  toDate: string;
  revenueLines: Array<{ accountCode: string; accountName: string; amount: string }>;
  expenseLines: Array<{ accountCode: string; accountName: string; amount: string }>;
  totalRevenue: string;
  totalExpenses: string;
  netProfit: string;
}

export default function ProfitLossPage() {
  const today = useMemo(() => new Date(), []);
  const [fromDate, setFromDate] = useState(() =>
    new Date(today.getFullYear(), today.getMonth() - 5, 1).toISOString().slice(0, 10)
  );
  const [toDate, setToDate] = useState(() => today.toISOString().slice(0, 10));

  const { data, isLoading, error, refetch } = useQuery<ProfitLossReport>({
    queryKey: ["report", "profit-loss", fromDate, toDate],
    queryFn: () =>
      apiFetch<ProfitLossReport>(`/api/v1/reports/profit-loss?fromDate=${fromDate}&toDate=${toDate}`),
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title="Profit and Loss Statement"
        description="Income statement generated directly from posted Revenue and Expense journal lines."
        actions={
          <>
            <input type="date" aria-label="From date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} className={`${inputClass} !w-auto`} />
            <input type="date" aria-label="To date" value={toDate} onChange={(e) => setToDate(e.target.value)} className={`${inputClass} !w-auto`} />
            <a
              href={`/api/v1/reports/profit-loss/export?fromDate=${fromDate}&toDate=${toDate}&format=csv`}
              className="inline-flex min-h-[42px] items-center rounded-md border border-[var(--border-strong)] bg-[var(--bg-surface)] px-3.5 text-sm font-medium hover:bg-[var(--bg-surface-subtle)]"
            >
              CSV
            </a>
            <a
              href={`/api/v1/reports/profit-loss/export?fromDate=${fromDate}&toDate=${toDate}&format=pdf`}
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

      {isLoading ? <LoadingState label="Computing Profit and Loss statement..." /> : null}
      {error ? (
        <ErrorState message={error instanceof Error ? error.message : "Unable to load P&L"} onRetry={() => refetch()} />
      ) : null}

      {data ? (
        <TableRegion label="Profit and Loss Statement">
          <table className="ledger-table">
            <thead>
              <tr>
                <th>Code</th>
                <th>Account</th>
                <th className="!text-right">Amount ({data.currencyCode})</th>
              </tr>
            </thead>
            <tbody>
              <tr className="bg-[var(--bg-surface-subtle)] font-semibold">
                <td colSpan={3}>Revenue</td>
              </tr>
              {data.revenueLines.map((l) => (
                <tr key={l.accountCode}>
                  <td className="font-mono-code text-xs text-[var(--text-secondary)]">{l.accountCode}</td>
                  <td>{l.accountName}</td>
                  <td className="text-right num">{formatMoney(l.amount, data.currencyCode)}</td>
                </tr>
              ))}
              <tr className="font-semibold">
                <td colSpan={2}>Total Revenue</td>
                <td className="text-right num">{formatMoney(data.totalRevenue, data.currencyCode)}</td>
              </tr>

              <tr className="bg-[var(--bg-surface-subtle)] font-semibold">
                <td colSpan={3}>Operating &amp; Other Expenses</td>
              </tr>
              {data.expenseLines.map((l) => (
                <tr key={l.accountCode}>
                  <td className="font-mono-code text-xs text-[var(--text-secondary)]">{l.accountCode}</td>
                  <td>{l.accountName}</td>
                  <td className="text-right num">{formatMoney(l.amount, data.currencyCode)}</td>
                </tr>
              ))}
              <tr className="font-semibold">
                <td colSpan={2}>Total Expenses</td>
                <td className="text-right num">{formatMoney(data.totalExpenses, data.currencyCode)}</td>
              </tr>

              <tr className="bg-[var(--bg-accent-soft)] font-semibold text-base text-[var(--brand-forest)]">
                <td colSpan={2}>Net Profit / (Loss)</td>
                <td className="text-right num">{formatMoney(data.netProfit, data.currencyCode)}</td>
              </tr>
            </tbody>
          </table>
        </TableRegion>
      ) : null}
    </div>
  );
}
