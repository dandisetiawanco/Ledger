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
  reportType: string;
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
    bucket: string;
    originalCurrency: string;
    originalOutstanding: string;
    baseOutstanding: string;
  }>;
}

export default function ReceivablesPage() {
  const [asOfDate, setAsOfDate] = useState(() => new Date().toISOString().slice(0, 10));

  const { data, isLoading, error, refetch } = useQuery<AgingReport>({
    queryKey: ["receivables-aging", asOfDate],
    queryFn: () => apiFetch<AgingReport>(`/api/v1/reports/receivables-aging?asOfDate=${asOfDate}`),
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title="Accounts Receivable Aging"
        description="Historical customer receivables calculated using payment allocations and credit notes dated on or before the selected reporting date."
        actions={
          <div className="flex items-center gap-2">
            <label htmlFor="ar-date" className="text-xs font-semibold text-[var(--text-secondary)]">As of</label>
            <input id="ar-date" type="date" value={asOfDate} onChange={(e) => setAsOfDate(e.target.value)} className={`${inputClass} !w-auto`} />
          </div>
        }
      />

      {isLoading ? <LoadingState label="Calculating historical receivables aging..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Unable to load AR aging"}
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
              { label: "Total AR", value: data.totalOutstanding },
            ].map((b) => (
              <div key={b.label} className="bg-[var(--bg-surface)] p-4">
                <p className="text-xs text-[var(--text-secondary)]">{b.label}</p>
                <p className="mt-1.5 text-base font-semibold num">{formatMoney(b.value, data.baseCurrency)}</p>
              </div>
            ))}
          </div>

          <div className="rounded-md border border-[var(--border-subtle)] bg-[var(--bg-surface)] px-4 py-3 flex flex-wrap items-center justify-between gap-2 text-xs">
            <span>
              GL Control Account (1100 Accounts Receivable) balance as of {data.asOfDate}:{" "}
              <strong className="num">{formatMoney(data.glControlAccountBalance, data.baseCurrency)}</strong>
            </span>
            <StatusBadge status={data.reconciledWithGl ? "BALANCED" : "UNBALANCED"} />
          </div>

          {data.items.length === 0 ? (
            <EmptyState title="No open receivables as of this date" description="All invoices issued on or before this date were settled." />
          ) : (
            <TableRegion label="Accounts receivable aging detail">
              <table className="ledger-table">
                <thead>
                  <tr>
                    <th>Invoice #</th>
                    <th>Customer</th>
                    <th>Issue Date</th>
                    <th>Due Date</th>
                    <th className="!text-right">Days Past Due</th>
                    <th>Bucket</th>
                    <th className="!text-right">Open ({data.baseCurrency})</th>
                  </tr>
                </thead>
                <tbody>
                  {data.items.map((item) => (
                    <tr key={item.documentId}>
                      <td className="font-mono-code text-xs font-semibold">
                        <Link href={`/workspace/invoices/${item.documentId}`} className="text-[var(--brand-forest)] hover:underline">
                          {item.documentNumber}
                        </Link>
                      </td>
                      <td className="font-medium">
                        <Link href={`/workspace/contacts/${item.contactId}`} className="hover:underline">
                          {item.contactName}
                        </Link>
                      </td>
                      <td className="num text-[var(--text-secondary)]">{item.issueDate}</td>
                      <td className="num text-[var(--text-secondary)]">{item.dueDate}</td>
                      <td className="text-right num">{item.daysPastDue}</td>
                      <td><StatusBadge status={item.daysPastDue > 0 ? "OVERDUE" : "CURRENT"} /></td>
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
