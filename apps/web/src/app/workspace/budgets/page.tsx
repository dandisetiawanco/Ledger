"use client";

import React, { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { canWriteAccounting, formatMoney } from "@ledger/contracts";
import { apiFetch, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/providers";
import {
  Button,
  EmptyState,
  ErrorState,
  Field,
  LoadingState,
  Modal,
  PageHeader,
  TableRegion,
  inputClass,
} from "@/components/ui";

interface BudgetDetail {
  id: string;
  name: string;
  periodType: string;
  startDate: string;
  endDate: string;
  currencyCode: string;
  revisionNumber: number;
  totalPlanned: string;
  totalActual: string;
  totalVariance: string;
  overallUtilizationPercent: string;
  lines: Array<{
    id: string;
    accountCode: string;
    accountName: string;
    plannedAmount: string;
    actualAmount: string;
    variance: string;
    utilizationPercent: string;
  }>;
  revisions: Array<{ id: string; revisionNumber: number; changeSummary: string; revisedAt: string }>;
}

interface AccountOpt { id: string; code: string; name: string; accountType: string; allowPosting: boolean }

export default function BudgetsPage() {
  const { auth } = useAuth();
  const queryClient = useQueryClient();
  const canCreate = canWriteAccounting(auth?.activeOrganization?.role);

  const [modalOpen, setModalOpen] = useState(false);
  const [name, setName] = useState("");
  const [startDate, setStartDate] = useState(() => `${new Date().getFullYear()}-01-01`);
  const [endDate, setEndDate] = useState(() => `${new Date().getFullYear()}-12-31`);
  const [accountId, setAccountId] = useState("");
  const [plannedAmount, setPlannedAmount] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const { data: budgets = [], isLoading, error, refetch } = useQuery<BudgetDetail[]>({
    queryKey: ["budgets"],
    queryFn: () => apiFetch<BudgetDetail[]>("/api/v1/budgets"),
  });
  const { data: accounts = [] } = useQuery<AccountOpt[]>({
    queryKey: ["accounts"],
    queryFn: () => apiFetch<AccountOpt[]>("/api/v1/accounts"),
  });

  const expenseAccounts = useMemo(
    () => accounts.filter((a) => a.accountType === "EXPENSE" && a.allowPosting),
    [accounts]
  );

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    setSaving(true);
    try {
      await apiFetch("/api/v1/budgets", {
        method: "POST",
        body: JSON.stringify({
          name,
          periodType: "ANNUAL",
          startDate,
          endDate,
          lines: [{ accountId, plannedAmount: Number(plannedAmount) }],
        }),
      });
      await queryClient.invalidateQueries({ queryKey: ["budgets"] });
      setModalOpen(false);
      setName("");
      setPlannedAmount("");
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Could not create budget");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Budgets & Variance"
        description="Compare planned account allocations against actual posted journal spending across your budget period."
        actions={canCreate ? <Button onClick={() => setModalOpen(true)}>Create budget</Button> : null}
      />

      {isLoading ? <LoadingState label="Computing budget versus actuals..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Unable to load budgets"}
          onRetry={() => refetch()}
        />
      ) : null}

      {!isLoading && !error ? (
        budgets.length === 0 ? (
          <EmptyState
            title="No budgets created yet"
            description="Create an account-based operating budget to track actual spending from posted journals."
          />
        ) : (
          budgets.map((budget) => {
            const chartData = budget.lines.map((l) => ({
              account: l.accountName.replace(" Expense", ""),
              Planned: Number(l.plannedAmount),
              Actual: Number(l.actualAmount),
            }));
            return (
              <section key={budget.id} className="space-y-4 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6">
                <div className="flex flex-wrap items-baseline justify-between gap-3 border-b border-[var(--border-subtle)] pb-4">
                  <div>
                    <h2 className="text-lg font-semibold">{budget.name}</h2>
                    <p className="text-xs text-[var(--text-secondary)] num">
                      {budget.startDate} to {budget.endDate} · Revision {budget.revisionNumber}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-6 text-sm num">
                    <div>
                      <span className="text-xs text-[var(--text-secondary)] block">Planned</span>
                      <strong>{formatMoney(budget.totalPlanned, budget.currencyCode)}</strong>
                    </div>
                    <div>
                      <span className="text-xs text-[var(--text-secondary)] block">Posted Actual</span>
                      <strong>{formatMoney(budget.totalActual, budget.currencyCode)}</strong>
                    </div>
                    <div>
                      <span className="text-xs text-[var(--text-secondary)] block">Remaining</span>
                      <strong>{formatMoney(budget.totalVariance, budget.currencyCode)}</strong>
                    </div>
                    <div>
                      <span className="text-xs text-[var(--text-secondary)] block">Utilization</span>
                      <strong className="text-[var(--brand-forest)]">{budget.overallUtilizationPercent}%</strong>
                    </div>
                  </div>
                </div>

                <div className="h-60 w-full">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart data={chartData} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                      <CartesianGrid strokeDasharray="3 3" stroke="var(--border-subtle)" vertical={false} />
                      <XAxis dataKey="account" tick={{ fontSize: 11, fill: "var(--text-secondary)" }} />
                      <YAxis
                        tick={{ fontSize: 11, fill: "var(--text-secondary)" }}
                        tickFormatter={(v: number) => (v >= 1_000_000 ? `${(v / 1_000_000).toFixed(0)}M` : `${v}`)}
                      />
                      <Tooltip formatter={(val: number) => formatMoney(val, budget.currencyCode)} />
                      <Bar dataKey="Planned" fill="#B9CBBF" radius={[3, 3, 0, 0]} />
                      <Bar dataKey="Actual" fill="#183D30" radius={[3, 3, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>

                <TableRegion label={`${budget.name} account allocations`}>
                  <table className="ledger-table">
                    <thead>
                      <tr>
                        <th>Code</th>
                        <th>Account</th>
                        <th className="!text-right">Planned</th>
                        <th className="!text-right">Posted Actual</th>
                        <th className="!text-right">Variance</th>
                        <th className="!text-right">Utilization</th>
                      </tr>
                    </thead>
                    <tbody>
                      {budget.lines.map((line) => (
                        <tr key={line.id}>
                          <td className="font-mono-code text-xs text-[var(--text-secondary)]">{line.accountCode}</td>
                          <td className="font-medium">{line.accountName}</td>
                          <td className="text-right num">{formatMoney(line.plannedAmount, budget.currencyCode)}</td>
                          <td className="text-right num font-semibold">{formatMoney(line.actualAmount, budget.currencyCode)}</td>
                          <td className="text-right num">{formatMoney(line.variance, budget.currencyCode)}</td>
                          <td className="text-right num font-semibold">{line.utilizationPercent}%</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </TableRegion>
              </section>
            );
          })
        )
      ) : null}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Create budget">
        <form onSubmit={handleCreate} className="space-y-4">
          {formError ? (
            <div role="alert" className="rounded-md bg-[var(--status-overdue-bg)] px-3.5 py-2.5 text-sm text-[var(--status-overdue-text)]">
              {formError}
            </div>
          ) : null}
          <Field id="bg-name" label="Budget name">
            <input id="bg-name" required value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. FY 2026 Operating Budget" className={inputClass} />
          </Field>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field id="bg-start" label="Start date">
              <input id="bg-start" type="date" required value={startDate} onChange={(e) => setStartDate(e.target.value)} className={inputClass} />
            </Field>
            <Field id="bg-end" label="End date">
              <input id="bg-end" type="date" required value={endDate} onChange={(e) => setEndDate(e.target.value)} className={inputClass} />
            </Field>
          </div>
          <Field id="bg-acc" label="Expense account">
            <select id="bg-acc" required value={accountId} onChange={(e) => setAccountId(e.target.value)} className={inputClass}>
              <option value="">Select expense account...</option>
              {expenseAccounts.map((a) => (
                <option key={a.id} value={a.id}>{a.code} · {a.name}</option>
              ))}
            </select>
          </Field>
          <Field id="bg-amt" label="Planned budget amount">
            <input id="bg-amt" type="number" step="0.01" min="0" required value={plannedAmount} onChange={(e) => setPlannedAmount(e.target.value)} className={`${inputClass} num`} />
          </Field>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" disabled={saving}>{saving ? "Creating..." : "Create budget"}</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
