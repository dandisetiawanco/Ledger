"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { canOperateFinance, canWriteAccounting, formatMoney } from "@ledger/contracts";
import { apiFetch, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/providers";
import {
  Button,
  ErrorState,
  LoadingState,
  PageHeader,
  StatusBadge,
} from "@/components/ui";

interface ExpenseDetail {
  id: string;
  expenseNumber: string;
  expenseType: string;
  title: string;
  expenseDate: string;
  dueDate: string | null;
  supplierName: string | null;
  expenseAccountCode: string;
  expenseAccountName: string;
  paymentAccountName: string | null;
  currencyCode: string;
  netAmount: string;
  taxAmount: string;
  totalAmount: string;
  amountPaid: string;
  outstandingBalance: string;
  status: string;
  notes: string | null;
  journalEntryId: string | null;
}

interface AttachmentDto {
  id: string;
  originalFilename: string;
  contentType: string;
  fileSize: number;
  uploadedAt: string;
}

export default function ExpenseDetailPage() {
  const params = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const { auth } = useAuth();
  const canOperate = canOperateFinance(auth?.activeOrganization?.role);
  const canAccount = canWriteAccounting(auth?.activeOrganization?.role);

  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const { data: expense, isLoading, error, refetch } = useQuery<ExpenseDetail>({
    queryKey: ["expense", params.id],
    queryFn: () => apiFetch<ExpenseDetail>(`/api/v1/expenses/${params.id}`),
  });

  const { data: attachments = [] } = useQuery<AttachmentDto[]>({
    queryKey: ["attachments", "EXPENSE", params.id],
    queryFn: () =>
      apiFetch<AttachmentDto[]>(`/api/v1/attachments?entityType=EXPENSE&entityId=${params.id}`),
  });

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ["expense", params.id] });
    await queryClient.invalidateQueries({ queryKey: ["expenses"] });
    await queryClient.invalidateQueries({ queryKey: ["attachments", "EXPENSE", params.id] });
  }

  async function submitApproval() {
    if (!expense) return;
    setBusy(true);
    setActionError(null);
    try {
      await apiFetch("/api/v1/approvals/submit", {
        method: "POST",
        body: JSON.stringify({ entityType: expense.expenseType === "SUPPLIER_BILL" ? "SUPPLIER_BILL" : "EXPENSE", entityId: expense.id }),
      });
      await refresh();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to submit for approval");
    } finally {
      setBusy(false);
    }
  }

  async function postNow() {
    setBusy(true);
    setActionError(null);
    try {
      await apiFetch(`/api/v1/expenses/${params.id}/post`, { method: "POST" });
      await refresh();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Unable to post expense");
    } finally {
      setBusy(false);
    }
  }

  async function reverseNow() {
    setBusy(true);
    setActionError(null);
    try {
      await apiFetch(`/api/v1/expenses/${params.id}/reverse`, {
        method: "POST",
        body: JSON.stringify({ reason: "Reversed from expense detail" }),
      });
      await refresh();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Unable to reverse expense");
    } finally {
      setBusy(false);
    }
  }

  async function handleUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setBusy(true);
    setActionError(null);
    try {
      const form = new FormData();
      form.append("entityType", "EXPENSE");
      form.append("entityId", params.id);
      form.append("file", file);
      await apiFetch("/api/v1/attachments", { method: "POST", body: form });
      await refresh();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Upload failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="max-w-4xl">
      <PageHeader
        title={expense ? `${expense.expenseNumber} · ${expense.title}` : "Expense detail"}
        actions={
          expense ? (
            <>
              {canOperate && (expense.status === "DRAFT" || expense.status === "REJECTED") ? (
                <Button variant="secondary" onClick={submitApproval} disabled={busy}>
                  Submit for approval
                </Button>
              ) : null}
              {canAccount && (expense.status === "DRAFT" || expense.status === "APPROVED") ? (
                <Button onClick={postNow} disabled={busy}>
                  Post to ledger
                </Button>
              ) : null}
              {canAccount && expense.status === "POSTED" ? (
                <Button variant="danger" onClick={reverseNow} disabled={busy}>
                  Reverse entry
                </Button>
              ) : null}
            </>
          ) : null
        }
      />

      {actionError ? <div className="mb-4"><ErrorState message={actionError} /></div> : null}
      {isLoading ? <LoadingState label="Loading expense..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Expense not found"}
          onRetry={() => refetch()}
        />
      ) : null}

      {expense ? (
        <div className="space-y-6">
          <div className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5 text-sm">
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Status &amp; type</p>
              <div className="mt-1 flex gap-2">
                <StatusBadge status={expense.status} />
                <StatusBadge status={expense.expenseType} />
              </div>
            </div>
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Expense date</p>
              <p className="mt-1 font-semibold num">{expense.expenseDate}</p>
            </div>
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Supplier</p>
              <p className="mt-1 font-semibold">{expense.supplierName ?? "Direct expense"}</p>
            </div>
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Accounting account</p>
              <p className="mt-1 font-semibold">
                <span className="font-mono-code text-xs mr-1.5">{expense.expenseAccountCode}</span>
                {expense.expenseAccountName}
              </p>
            </div>
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Total ({expense.currencyCode})</p>
              <p className="mt-1 font-semibold num">{formatMoney(expense.totalAmount, expense.currencyCode)}</p>
            </div>
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Outstanding balance</p>
              <p className="mt-1 font-semibold num">{formatMoney(expense.outstandingBalance, expense.currencyCode)}</p>
            </div>
            {expense.journalEntryId ? (
              <div className="sm:col-span-2">
                <Link
                  href={`/workspace/journals/${expense.journalEntryId}`}
                  className="text-xs font-semibold text-[var(--brand-forest)] hover:underline"
                >
                  Inspect posted journal entry
                </Link>
              </div>
            ) : null}
          </div>

          <section className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-sm font-semibold">Receipt &amp; bill attachments</h2>
                <p className="text-xs text-[var(--text-secondary)]">
                  Files are stored privately per organization and served through authorized download endpoints.
                </p>
              </div>
              {canOperate ? (
                <label className="inline-flex min-h-[40px] items-center rounded-md border border-[var(--border-strong)] bg-[var(--bg-canvas)] px-3.5 text-xs font-semibold hover:bg-[var(--bg-surface-subtle)]">
                  <span>Upload receipt (PDF/PNG/JPG)</span>
                  <input type="file" accept=".pdf,.png,.jpg,.jpeg,.webp" onChange={handleUpload} className="sr-only" />
                </label>
              ) : null}
            </div>
            {attachments.length === 0 ? (
              <p className="text-xs text-[var(--text-secondary)]">No attachments uploaded for this record yet.</p>
            ) : (
              <ul className="divide-y divide-[var(--border-subtle)] text-sm">
                {attachments.map((att) => (
                  <li key={att.id} className="py-2.5 flex items-center justify-between gap-3">
                    <span>{att.originalFilename}</span>
                    <a
                      href={`/api/v1/attachments/${att.id}/download`}
                      className="text-xs font-semibold text-[var(--brand-forest)] hover:underline"
                    >
                      Download ({Math.max(1, Math.round(att.fileSize / 1024))} KB)
                    </a>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      ) : null}
    </div>
  );
}
