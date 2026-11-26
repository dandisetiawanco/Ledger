"use client";

import React, { useMemo, useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
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

interface OverviewResponse {
  organizationName: string;
  currencyCode: string;
  demo: boolean;
  fromDate: string;
  toDate: string;
  cashBalance: string;
  periodRevenue: string;
  periodExpenses: string;
  periodNetProfit: string;
  outstandingReceivables: string;
  outstandingPayables: string;
  periodNetCashMovement: string;
  monthlyTrend: Array<{ month: string; revenue: string; expenses: string; netProfit: string }>;
  expenseBreakdown: Array<{ accountCode: string; accountName: string; amount: string }>;
  upcomingInvoices: Array<{
    id: string;
    invoiceNumber: string;
    customerName: string;
    dueDate: string;
    overdue: boolean;
    currencyCode: string;
    outstandingBalance: string;
  }>;
  recentTransactions: Array<{
    id: string;
    entryNumber: string;
    entryDate: string;
    description: string;
    sourceType: string;
    amount: string;
  }>;
  activeBudget: {
    id: string;
    name: string;
    totalPlanned: string;
    totalActual: string;
    utilizationPercent: string;
  } | null;
}

function isoDate(d: Date) {
  return d.toISOString().slice(0, 10);
}

export default function WorkspaceOverviewPage() {
  const today = useMemo(() => new Date(), []);
  const sixMonthsStart = useMemo(() => {
    const d = new Date(today.getFullYear(), today.getMonth() - 5, 1);
    return isoDate(d);
  }, [today]);

  const [fromDate, setFromDate] = useState(sixMonthsStart);
  const [toDate, setToDate] = useState(isoDate(today));

  const { data, isLoading, error, refetch } = useQuery<OverviewResponse>({
    queryKey: ["overview", fromDate, toDate],
    queryFn: () =>
      apiFetch<OverviewResponse>(
        `/api/v1/reports/overview?fromDate=${fromDate}&toDate=${toDate}`
      ),
  });

  const chartData = useMemo(
    () =>
      (data?.monthlyTrend ?? []).map((pt) => ({
        month: pt.month,
        Revenue: Number(pt.revenue),
        Expenses: Number(pt.expenses),
      })),
    [data]
  );

  return (
    <div>
      <PageHeader
        title="Financial overview"
        description={
          data
            ? `Posted ledger position for ${data.organizationName} (${data.fromDate} to ${data.toDate}).`
            : "Live cash position, revenue, expenses, receivables, and payables derived from posted journals."
        }
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <label htmlFor="ov-from" className="sr-only">From date</label>
            <input
              id="ov-from"
              type="date"
              value={fromDate}
              onChange={(e) => setFromDate(e.target.value)}
              className={`${inputClass} !w-auto`}
            />
            <span className="text-xs text-[var(--text-secondary)]">to</span>
            <label htmlFor="ov-to" className="sr-only">To date</label>
            <input
              id="ov-to"
              type="date"
              value={toDate}
              onChange={(e) => setToDate(e.target.value)}
              className={`${inputClass} !w-auto`}
            />
            <Link
              href="/workspace/invoices/new"
              className="inline-flex min-h-[42px] items-center rounded-md bg-[var(--brand-forest)] px-4 text-sm font-medium text-[var(--text-on-brand)] hover:bg-[var(--brand-forest-hover)]"
            >
              New invoice
            </Link>
          </div>
        }
      />

      {isLoading ? <LoadingState label="Computing posted financial overview..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Failed to load financial overview"}
          onRetry={() => refetch()}
        />
      ) : null}

      {data ? (
        <div className="space-y-8">
          {/* Primary Financial Position Strip (clean typographic hierarchy, not cluttered nested cards) */}
          <section
            aria-label="Key posted financial balances"
            className="grid grid-cols-1 gap-px overflow-hidden rounded-lg border border-[var(--border-subtle)] bg-[var(--border-subtle)] sm:grid-cols-2 lg:grid-cols-3"
          >
            {[
              {
                label: `Cash & bank balance (as of ${data.toDate})`,
                value: formatMoney(data.cashBalance, data.currencyCode),
                sub: `Period cash change: ${formatMoney(data.periodNetCashMovement, data.currencyCode)}`,
              },
              {
                label: `Posted revenue (${data.fromDate} to ${data.toDate})`,
                value: formatMoney(data.periodRevenue, data.currencyCode),
                sub: `Posted expenses: ${formatMoney(data.periodExpenses, data.currencyCode)}`,
              },
              {
                label: `Net profit (${data.fromDate} to ${data.toDate})`,
                value: formatMoney(data.periodNetProfit, data.currencyCode),
                sub: "Derived exclusively from posted journals",
              },
              {
                label: `Accounts receivable (as of ${data.toDate})`,
                value: formatMoney(data.outstandingReceivables, data.currencyCode),
                sub: `${data.upcomingInvoices.length} open invoice(s)`,
              },
              {
                label: `Accounts payable (as of ${data.toDate})`,
                value: formatMoney(data.outstandingPayables, data.currencyCode),
                sub: "Unpaid posted supplier bills",
              },
              {
                label: data.activeBudget ? data.activeBudget.name : "Budget utilization",
                value: data.activeBudget ? `${data.activeBudget.utilizationPercent}%` : "No budget",
                sub: data.activeBudget
                  ? `${formatMoney(data.activeBudget.totalActual, data.currencyCode)} of ${formatMoney(data.activeBudget.totalPlanned, data.currencyCode)}`
                  : "Create an expense budget in Budgets",
              },
            ].map((metric) => (
              <div key={metric.label} className="bg-[var(--bg-surface)] p-5">
                <p className="text-xs font-medium text-[var(--text-secondary)]">{metric.label}</p>
                <p className="mt-2 text-2xl font-semibold tracking-tight num text-[var(--text-primary)]">
                  {metric.value}
                </p>
                <p className="mt-1.5 text-xs text-[var(--text-secondary)] num">{metric.sub}</p>
              </div>
            ))}
          </section>

          {/* Chart & Upcoming Invoices */}
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
            <div className="lg:col-span-7 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-5">
              <div className="flex items-baseline justify-between gap-2 mb-4">
                <div>
                  <h2 className="text-base font-semibold text-[var(--text-primary)]">
                    Monthly revenue compared to operating expenses
                  </h2>
                  <p className="text-xs text-[var(--text-secondary)]">
                    Six-month posted Profit &amp; Loss series ({data.currencyCode})
                  </p>
                </div>
                <Link
                  href="/workspace/reports/profit-loss"
                  className="text-xs font-semibold text-[var(--brand-forest)] hover:underline"
                >
                  Open P&amp;L report
                </Link>
              </div>
              <div className="h-64 w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border-subtle)" vertical={false} />
                    <XAxis dataKey="month" tick={{ fontSize: 11, fill: "var(--text-secondary)" }} />
                    <YAxis
                      tick={{ fontSize: 11, fill: "var(--text-secondary)" }}
                      tickFormatter={(v: number) =>
                        v >= 1_000_000 ? `${(v / 1_000_000).toFixed(0)}M` : `${v}`
                      }
                    />
                    <Tooltip
                      formatter={(val: number) => formatMoney(val, data.currencyCode)}
                      contentStyle={{
                        backgroundColor: "var(--bg-surface)",
                        borderColor: "var(--border-subtle)",
                        borderRadius: "6px",
                        fontSize: "12px",
                      }}
                    />
                    <Bar dataKey="Revenue" fill="#183D30" radius={[3, 3, 0, 0]} />
                    <Bar dataKey="Expenses" fill="#8EA696" radius={[3, 3, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>

            <div className="lg:col-span-5 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-5 flex flex-col">
              <div className="flex items-baseline justify-between gap-2 mb-4">
                <h2 className="text-base font-semibold text-[var(--text-primary)]">
                  Open invoices &amp; due dates
                </h2>
                <Link
                  href="/workspace/invoices"
                  className="text-xs font-semibold text-[var(--brand-forest)] hover:underline"
                >
                  View all invoices
                </Link>
              </div>
              {data.upcomingInvoices.length === 0 ? (
                <EmptyState
                  title="No open invoices"
                  description="All issued customer invoices are settled, or no invoices have been issued yet."
                />
              ) : (
                <ul className="divide-y divide-[var(--border-subtle)] text-sm">
                  {data.upcomingInvoices.map((inv) => (
                    <li key={inv.id} className="py-3 flex items-center justify-between gap-3">
                      <div className="min-w-0">
                        <Link
                          href={`/workspace/invoices/${inv.id}`}
                          className="font-semibold text-[var(--text-primary)] hover:text-[var(--brand-forest)] hover:underline block truncate"
                        >
                          {inv.invoiceNumber} · {inv.customerName}
                        </Link>
                        <p className="text-xs text-[var(--text-secondary)]">
                          Due {inv.dueDate}
                        </p>
                      </div>
                      <div className="text-right shrink-0">
                        <p className="font-semibold num text-[var(--text-primary)]">
                          {formatMoney(inv.outstandingBalance, inv.currencyCode)}
                        </p>
                        {inv.overdue ? <StatusBadge status="OVERDUE" /> : null}
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>

          {/* Recent Posted Ledger Entries */}
          <section>
            <div className="flex items-baseline justify-between mb-3">
              <h2 className="text-base font-semibold text-[var(--text-primary)]">
                Recent posted journal entries
              </h2>
              <Link
                href="/workspace/journals"
                className="text-xs font-semibold text-[var(--brand-forest)] hover:underline"
              >
                Open journal register
              </Link>
            </div>
            {data.recentTransactions.length === 0 ? (
              <EmptyState
                title="No posted financial transactions yet"
                description="Create your first invoice, expense, or manual journal entry to begin building your ledger."
                action={
                  <Link
                    href="/workspace/journals/new"
                    className="inline-flex min-h-[42px] items-center rounded-md bg-[var(--brand-forest)] px-4 text-sm font-medium text-[var(--text-on-brand)]"
                  >
                    Record journal entry
                  </Link>
                }
              />
            ) : (
              <TableRegion label="Recent posted journal entries">
                <table className="ledger-table">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Entry</th>
                      <th>Source</th>
                      <th>Description</th>
                      <th className="!text-right">Amount ({data.currencyCode})</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.recentTransactions.map((tx) => (
                      <tr key={tx.id}>
                        <td className="num whitespace-nowrap text-[var(--text-secondary)]">
                          {tx.entryDate}
                        </td>
                        <td className="font-mono-code text-xs font-semibold">
                          <Link
                            href={`/workspace/journals/${tx.id}`}
                            className="text-[var(--brand-forest)] hover:underline"
                          >
                            {tx.entryNumber}
                          </Link>
                        </td>
                        <td>
                          <StatusBadge status={tx.sourceType} />
                        </td>
                        <td className="text-[var(--text-primary)]">{tx.description}</td>
                        <td className="text-right num font-semibold text-[var(--text-primary)] whitespace-nowrap">
                          {formatMoney(tx.amount, data.currencyCode)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </TableRegion>
            )}
          </section>
        </div>
      ) : null}
    </div>
  );
}
