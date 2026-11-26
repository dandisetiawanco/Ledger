"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { formatMoney } from "@ledger/contracts";
import { apiFetch } from "@/lib/api";
import {
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  StatusBadge,
  TableRegion,
  inputClass,
} from "@/components/ui";

interface AgingReport {
  baseCurrency: string;
  asOfDate: string;
  current: string;
  days1To30: string;
  days31To60: string;
  days61To90: string;
  over90Days: string;
  totalOutstanding: string;
  glControlAccountBalance: string;
  reconciledWithGl: boolean;
  items: Array<{
    documentId: string;
    documentNumber: string;
    contactId: string;
    contactName: string;
    issueDate: string;
    dueDate: string;
    daysPastDue: number;
    baseOutstanding: string;
  }>;
}

export default function PayablesPage() {
  const [asOfDate, setAsOfDate] = useState(() => new Date().toISOString().slice(0, 10));

  const { data, isLoading, error, refetch } = useQuery<AgingReport>({
    queryKey: ["payables-aging", asOfDate],
    queryFn: () => apiFetch<AgingReport>(`/api/v1/reports/payables-aging?asOfDate=${asOfDate}`),
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title="Accounts Payable Aging"
        description="Unpaid supplier bills reconciled against the 2010 Accounts Payable general ledger control account."
        actions={
          <div className="flex items-center gap-2">
            <label htmlFor="ap-date" className="text-xs font-semibold text-[var(--text-secondary)]">As of</label>
            <input id="ap-date" type="date" value={asOfDate} onChange={(e) => setAsOfDate(e.target.value)} className={`${inputClass} !w-auto`} />
          </div>
        }
      />

      {isLoading ? <LoadingState label="Calculating historical payables aging..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Unable to load AP aging"}
          onRetry={() => refetch()}
        />
      ) : null}

      {data ? (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-px overflow-hidden rounded-lg border border-[var(--border-subtle)] bg-[var(--border-subtle)]">
            {[
              { label: "Current", value: data.current },
              { label: "1 to 30 days", value: data.days1To30 },
              { label: "31 to 60 days", value: data.days31To60 },
              { label: "61 to 90 days", value: data.days61To90 },
              { label: "Over 90 days", value: data.over90Days },
              { label: "Total AP", value: data.totalOutstanding },
            ].map((b) => (
              <div key={b.label} className="bg-[var(--bg-surface)] p-4">
                <p className="text-xs text-[var(--text-secondary)]">{b.label}</p>
                <p className="mt-1.5 text-base font-semibold num">{formatMoney(b.value, data.baseCurrency)}</p>
              </div>
            ))}
          </div>

          <div className="rounded-md border border-[var(--border-subtle)] bg-[var(--bg-surface)] px-4 py-3 flex flex-wrap items-center justify-between gap-2 text-xs">
            <span>
              GL Control Account (2010 Accounts Payable) balance as of {data.asOfDate}:{" "}
              <strong className="num">{formatMoney(data.glControlAccountBalance, data.baseCurrency)}</strong>
            </span>
            <StatusBadge status={data.reconciledWithGl ? "BALANCED" : "UNBALANCED"} />
          </div>

          {data.items.length === 0 ? (
            <EmptyState title="No open payables as of this date" description="All supplier bills dated on or before this reporting date have been settled." />
          ) : (
            <TableRegion label="Accounts payable aging detail">
              <table className="ledger-table">
                <thead>
                  <tr>
                    <th>Bill #</th>
                    <th>Supplier</th>
                    <th>Bill Date</th>
                    <th>Due Date</th>
                    <th className="!text-right">Days Past Due</th>
                    <th className="!text-right">Open ({data.baseCurrency})</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((item) => (
                    <tr key={item.documentId}>
                      <td className="font-mono-code text-xs font-semibold">
                        <Link href={`/workspace/expenses/${item.documentId}`} className="text-[var(--brand-forest)] hover:underline">
                          {item.documentNumber}
                        </Link>
                      </td>
                      <td className="font-medium">{item.contactName}</td>
                      <td className="num text-[var(--text-secondary)]">{item.issueDate}</td>
                      <td className="num text-[var(--text-secondary)]">{item.dueDate}</td>
                      <td className="text-right num">{item.daysPastDue}</td>
                      <td className="text-right num font-semibold whitespace-nowrap">
                        {formatMoney(item.baseOutstanding, data.baseCurrency)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </TableRegion>
          )}
        </>
      ) : null}
    </div>
  );
}
