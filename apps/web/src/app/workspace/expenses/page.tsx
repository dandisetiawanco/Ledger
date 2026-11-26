"use client";

import React, { useMemo, useState } from "react";
import Link from "next/link";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { canOperateFinance, canWriteAccounting, formatMoney } from "@ledger/contracts";
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
  StatusBadge,
  TableRegion,
  inputClass,
} from "@/components/ui";

interface ExpenseRow {
  id: string;
  expenseNumber: string;
  expenseType: string;
  title: string;
  expenseDate: string;
  dueDate: string | null;
  overdue: boolean;
  supplierName: string | null;
  expenseAccountCode: string;
  expenseAccountName: string;
  currencyCode: string;
  totalAmount: string;
  outstandingBalance: string;
  status: string;
}

interface ContactOpt { id: string; name: string }
interface AccountOpt { id: string; code: string; name: string; accountType: string; allowPosting: boolean }

export default function ExpensesPage() {
  const { auth } = useAuth();
  const queryClient = useQueryClient();
  const canOperate = canOperateFinance(auth?.activeOrganization?.role);
  const canAccount = canWriteAccounting(auth?.activeOrganization?.role);
  const ccy = auth?.activeOrganization?.baseCurrency ?? "IDR";

  const [typeFilter, setTypeFilter] = useState("ALL");
  const [modalOpen, setModalOpen] = useState(false);
  const [expenseType, setExpenseType] = useState<"IMMEDIATE_EXPENSE" | "SUPPLIER_BILL">("IMMEDIATE_EXPENSE");
  const [title, setTitle] = useState("");
  const [expenseDate, setExpenseDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [dueDate, setDueDate] = useState("");
  const [supplierId, setSupplierId] = useState("");
  const [expenseAccountId, setExpenseAccountId] = useState("");
  const [netAmount, setNetAmount] = useState("");
  const [taxRate, setTaxRate] = useState("0");
  const [notes, setNotes] = useState("");
  const [postImmediately, setPostImmediately] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const { data: expenses = [], isLoading, error, refetch } = useQuery<ExpenseRow[]>({
    queryKey: ["expenses"],
    queryFn: () => apiFetch<ExpenseRow[]>("/api/v1/expenses"),
  });
  const { data: suppliers = [] } = useQuery<ContactOpt[]>({
    queryKey: ["contacts", "SUPPLIER"],
    queryFn: () => apiFetch<ContactOpt[]>("/api/v1/contacts?type=SUPPLIER"),
  });
  const { data: accounts = [] } = useQuery<AccountOpt[]>({
    queryKey: ["accounts"],
    queryFn: () => apiFetch<AccountOpt[]>("/api/v1/accounts"),
  });

  const expAccounts = useMemo(
    () => accounts.filter((a) => (a.accountType === "EXPENSE" || a.accountType === "ASSET") && a.allowPosting),
    [accounts]
  );

  const filtered = useMemo(
    () => expenses.filter((e) => typeFilter === "ALL" || e.expenseType === typeFilter),
    [expenses, typeFilter]
  );

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    setSaving(true);
    try {
      await apiFetch("/api/v1/expenses", {
        method: "POST",
        body: JSON.stringify({
          expenseType,
          title,
          expenseDate,
          dueDate: expenseType === "SUPPLIER_BILL" && dueDate ? dueDate : undefined,
          supplierId: supplierId || undefined,
          expenseAccountId: expenseAccountId || undefined,
          currencyCode: ccy,
          netAmount: Number(netAmount),
          taxRate: Number(taxRate || 0),
          notes: notes || undefined,
          postImmediately,
        }),
      });
      await queryClient.invalidateQueries({ queryKey: ["expenses"] });
      await queryClient.invalidateQueries({ queryKey: ["overview"] });
      setModalOpen(false);
      setTitle("");
      setNetAmount("");
      setNotes("");
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Failed to save expense");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Expenses & Supplier Bills"
        description="Track immediate operating expenses and unpaid supplier bills. Settling a bill relieves Accounts Payable without double-counting expenses."
        actions={
          canOperate ? <Button onClick={() => setModalOpen(true)}>Record expense or bill</Button> : null
        }
      />

      <div className="mb-5 flex flex-wrap gap-1.5">
        {[
          { key: "ALL", label: "All records" },
          { key: "IMMEDIATE_EXPENSE", label: "Immediate expenses" },
          { key: "SUPPLIER_BILL", label: "Supplier bills" },
        ].map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTypeFilter(t.key)}
            className={`min-h-[38px] rounded-md px-3 text-xs font-semibold border ${
              typeFilter === t.key
                ? "bg-[var(--brand-forest)] text-[var(--text-on-brand)] border-[var(--brand-forest)]"
                : "bg-[var(--bg-surface)] text-[var(--text-secondary)] border-[var(--border-subtle)]"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {isLoading ? <LoadingState label="Loading expenses and supplier bills..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Unable to load expenses"}
          onRetry={() => refetch()}
        />
      ) : null}

      {!isLoading && !error ? (
        filtered.length === 0 ? (
          <EmptyState
            title="No expenses recorded"
            description="Record an immediate operating expense or enter a supplier bill to track accounts payable."
          />
        ) : (
          <TableRegion label="Expenses and supplier bills">
            <table className="ledger-table">
              <thead>
                <tr>
                  <th>Number</th>
                  <th>Type</th>
                  <th>Title / Supplier</th>
                  <th>Account</th>
                  <th>Date</th>
                  <th>Status</th>
                  <th className="!text-right">Total</th>
                  <th className="!text-right">Payable Due</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((exp) => (
                  <tr key={exp.id}>
                    <td className="font-mono-code text-xs font-semibold">
                      <Link
                        href={`/workspace/expenses/${exp.id}`}
                        className="text-[var(--brand-forest)] hover:underline"
                      >
                        {exp.expenseNumber}
                      </Link>
                    </td>
                    <td><StatusBadge status={exp.expenseType} /></td>
                    <td>
                      <div className="font-medium">{exp.title}</div>
                      {exp.supplierName ? (
                        <div className="text-xs text-[var(--text-secondary)]">{exp.supplierName}</div>
                      ) : null}
                    </td>
                    <td className="text-xs text-[var(--text-secondary)]">
                      <span className="font-mono-code">{exp.expenseAccountCode}</span> {exp.expenseAccountName}
                    </td>
                    <td className="num text-[var(--text-secondary)] whitespace-nowrap">{exp.expenseDate}</td>
                    <td><StatusBadge status={exp.overdue ? "OVERDUE" : exp.status} /></td>
                    <td className="text-right num font-medium whitespace-nowrap">
                      {formatMoney(exp.totalAmount, exp.currencyCode)}
                    </td>
                    <td className="text-right num font-semibold whitespace-nowrap">
                      {exp.expenseType === "SUPPLIER_BILL"
                        ? formatMoney(exp.outstandingBalance, exp.currencyCode)
                        : ""}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableRegion>
        )
      ) : null}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Record expense or supplier bill">
        <form onSubmit={handleCreate} className="space-y-4">
          {formError ? (
            <div role="alert" className="rounded-md bg-[var(--status-overdue-bg)] px-3.5 py-2.5 text-sm text-[var(--status-overdue-text)]">
              {formError}
            </div>
          ) : null}
          <Field id="exp-type" label="Record type">
            <select
              id="exp-type"
              value={expenseType}
              onChange={(e) => setExpenseType(e.target.value as "IMMEDIATE_EXPENSE" | "SUPPLIER_BILL")}
              className={inputClass}
            >
              <option value="IMMEDIATE_EXPENSE">Immediate Expense (Paid now from Bank)</option>
              <option value="SUPPLIER_BILL">Supplier Bill (Unpaid, posts to Accounts Payable)</option>
            </select>
          </Field>
          <Field id="exp-title" label="Description">
            <input id="exp-title" required value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Cloud hosting subscription" className={inputClass} />
          </Field>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field id="exp-date" label="Date">
              <input id="exp-date" type="date" required value={expenseDate} onChange={(e) => setExpenseDate(e.target.value)} className={inputClass} />
            </Field>
            {expenseType === "SUPPLIER_BILL" ? (
              <Field id="exp-due" label="Due date">
                <input id="exp-due" type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} className={inputClass} />
              </Field>
            ) : null}
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field id="exp-sup" label={expenseType === "SUPPLIER_BILL" ? "Supplier (required)" : "Supplier (optional)"}>
              <select id="exp-sup" required={expenseType === "SUPPLIER_BILL"} value={supplierId} onChange={(e) => setSupplierId(e.target.value)} className={inputClass}>
                <option value="">Select supplier...</option>
                {suppliers.map((s) => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </select>
            </Field>
            <Field id="exp-acc" label="Expense / Asset account">
              <select id="exp-acc" value={expenseAccountId} onChange={(e) => setExpenseAccountId(e.target.value)} className={inputClass}>
                <option value="">Default Operating Expense</option>
                {expAccounts.map((a) => (
                  <option key={a.id} value={a.id}>{a.code} · {a.name}</option>
                ))}
              </select>
            </Field>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field id="exp-amt" label={`Net amount (${ccy})`}>
              <input id="exp-amt" type="number" step="0.01" min="0.01" required value={netAmount} onChange={(e) => setNetAmount(e.target.value)} className={`${inputClass} num`} />
            </Field>
            <Field id="exp-tax" label="Input tax %">
              <input id="exp-tax" type="number" step="0.01" min="0" value={taxRate} onChange={(e) => setTaxRate(e.target.value)} className={`${inputClass} num`} />
            </Field>
          </div>
          <Field id="exp-notes" label="Notes">
            <input id="exp-notes" value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Optional memo or vendor bill number" className={inputClass} />
          </Field>
          {canAccount ? (
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={postImmediately} onChange={(e) => setPostImmediately(e.target.checked)} />
              Post immediately to General Ledger (if below approval threshold)
            </label>
          ) : null}
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" disabled={saving}>{saving ? "Saving..." : "Save record"}</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
