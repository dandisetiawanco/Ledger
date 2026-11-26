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
  StatusBadge,
  TableRegion,
  inputClass,
} from "@/components/ui";

interface CashFlowReport {
  organizationName: string;
  currencyCode: string;
  fromDate: string;
  toDate: string;
  operatingActivities: Array<{ label: string; amount: string }>;
  investingActivities: Array<{ label: string; amount: string }>;
  financingActivities: Array<{ label: string; amount: string }>;
  netOperatingCash: string;
  netInvestingCash: string;
  netFinancingCash: string;
  netCashChange: string;
  openingCashBalance: string;
  closingCashBalance: string;
  excludedInternalTransfers: string;
  reconciled: boolean;
}

export default function CashFlowPage() {
  const today = useMemo(() => new Date(), []);
  const [fromDate, setFromDate] = useState(() =>
    new Date(today.getFullYear(), today.getMonth() - 5, 1).toISOString().slice(0, 10)
  );
  const [toDate, setToDate] = useState(() => today.toISOString().slice(0, 10));

  const { data, isLoading, error, refetch } = useQuery<CashFlowReport>({
    queryKey: ["report", "cash-flow", fromDate, toDate],
    queryFn: () =>
      apiFetch<CashFlowReport>(`/api/v1/reports/cash-flow?fromDate=${fromDate}&toDate=${toDate}`),
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title="Direct Cash Flow Statement"
        description="Classified movements across Cash and Bank accounts. Internal transfers between cash accounts are excluded from net cash flow."
        actions={
          <>
            <input type="date" aria-label="From date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} className={`${inputClass} !w-auto`} />
            <input type="date" aria-label="To date" value={toDate} onChange={(e) => setToDate(e.target.value)} className={`${inputClass} !w-auto`} />
            <Button variant="secondary" onClick={() => window.print()}>Print</Button>
          </>
        }
      />

      {isLoading ? <LoadingState label="Computing Direct Cash Flow statement..." /> : null}
      {error ? (
        <ErrorState message={error instanceof Error ? error.message : "Unable to load Cash Flow"} onRetry={() => refetch()} />
      ) : null}

      {data ? (
        <>
          <div className="rounded-md border border-[var(--border-subtle)] bg-[var(--bg-surface)] px-4 py-3 flex flex-wrap items-center justify-between gap-2 text-sm">
            <span className="num">
              Opening ({formatMoney(data.openingCashBalance, data.currencyCode)}) + Net Change (
              {formatMoney(data.netCashChange, data.currencyCode)}) = Closing (
              {formatMoney(data.closingCashBalance, data.currencyCode)}) · Excluded internal transfers:{" "}
              {formatMoney(data.excludedInternalTransfers, data.currencyCode)}
            </span>
            <StatusBadge status={data.reconciled ? "BALANCED" : "UNBALANCED"} />
          </div>

          <TableRegion label="Direct Cash Flow Statement">
            <table className="ledger-table">
              <thead>
                <tr>
                  <th>Activity</th>
                  <th className="!text-right">Cash Movement ({data.currencyCode})</th>
                </tr>
              </thead>
              <tbody>
                <tr className="bg-[var(--bg-surface-subtle)] font-semibold"><td colSpan={2}>Operating Activities</td></tr>
                {data.operatingActivities.map((l) => (
                  <tr key={`op-${l.label}`}>
                    <td>{l.label}</td>
                    <td className="text-right num">{formatMoney(l.amount, data.currencyCode)}</td>
                  </tr>
                ))}
                <tr className="font-semibold">
                  <td>Net Cash from Operating Activities</td>
                  <td className="text-right num">{formatMoney(data.netOperatingCash, data.currencyCode)}</td>
                </tr>

                <tr className="bg-[var(--bg-surface-subtle)] font-semibold"><td colSpan={2}>Investing Activities</td></tr>
                {data.investingActivities.map((l) => (
                  <tr key={`inv-${l.label}`}>
                    <td>{l.label}</td>
                    <td className="text-right num">{formatMoney(l.amount, data.currencyCode)}</td>
                  </tr>
                ))}
                <tr className="font-semibold">
                  <td>Net Cash from Investing Activities</td>
                  <td className="text-right num">{formatMoney(data.netInvestingCash, data.currencyCode)}</td>
                </tr>

                <tr className="bg-[var(--bg-surface-subtle)] font-semibold"><td colSpan={2}>Financing Activities</td></tr>
                {data.financingActivities.map((l) => (
                  <tr key={`fin-${l.label}`}>
                    <td>{l.label}</td>
                    <td className="text-right num">{formatMoney(l.amount, data.currencyCode)}</td>
                  </tr>
                ))}
                <tr className="font-semibold">
                  <td>Net Cash from Financing Activities</td>
                  <td className="text-right num">{formatMoney(data.netFinancingCash, data.currencyCode)}</td>
                </tr>

                <tr className="bg-[var(--bg-accent-soft)] font-semibold text-base text-[var(--brand-forest)]">
                  <td>Closing Cash &amp; Bank Balance</td>
                  <td className="text-right num">{formatMoney(data.closingCashBalance, data.currencyCode)}</td>
                </tr>
              </tbody>
            </table>
          </TableRegion>
        </>
      ) : null}
    </div>
  );
}
