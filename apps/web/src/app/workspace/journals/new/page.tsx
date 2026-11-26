"use client";

import React, { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import Decimal from "decimal.js";
import { formatMoney, safeDecimal } from "@ledger/contracts";
import { apiFetch, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/providers";
import { Button, Field, PageHeader, StatusBadge, inputClass } from "@/components/ui";

interface AccountOption {
  id: string;
  code: string;
  name: string;
  allowPosting: boolean;
  active: boolean;
}

interface DraftLine {
  accountId: string;
  description: string;
  debit: string;
  credit: string;
}

export default function NewJournalPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { auth } = useAuth();
  const ccy = auth?.activeOrganization?.baseCurrency ?? "IDR";

  const { data: accounts = [] } = useQuery<AccountOption[]>({
    queryKey: ["accounts"],
    queryFn: () => apiFetch<AccountOption[]>("/api/v1/accounts"),
  });

  const postingAccounts = useMemo(
    () => accounts.filter((a) => a.allowPosting && a.active),
    [accounts]
  );

  const [entryDate, setEntryDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [description, setDescription] = useState("");
  const [reference, setReference] = useState("");
  const [lines, setLines] = useState<DraftLine[]>([
    { accountId: "", description: "", debit: "", credit: "" },
    { accountId: "", description: "", debit: "", credit: "" },
  ]);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const totals = useMemo(() => {
    let debit = new Decimal(0);
    let credit = new Decimal(0);
    for (const l of lines) {
      debit = debit.add(safeDecimal(l.debit));
      credit = credit.add(safeDecimal(l.credit));
    }
    const diff = debit.sub(credit).abs();
    return {
      debit: debit.toFixed(2),
      credit: credit.toFixed(2),
      diff: diff.toFixed(2),
      balanced: debit.gt(0) && debit.equals(credit),
    };
  }, [lines]);

  function updateLine(idx: number, patch: Partial<DraftLine>) {
    setLines((prev) => prev.map((line, i) => (i === idx ? { ...line, ...patch } : line)));
  }

  function addLine() {
    setLines((prev) => [...prev, { accountId: "", description: "", debit: "", credit: "" }]);
  }

  function removeLine(idx: number) {
    if (lines.length <= 2) return;
    setLines((prev) => prev.filter((_, i) => i !== idx));
  }

  async function submitJournal(postImmediately: boolean) {
    setError(null);
    setSubmitting(true);
    try {
      const payload = {
        entryDate,
        description,
        reference: reference || undefined,
        currencyCode: ccy,
        postImmediately,
        lines: lines.map((l) => ({
          accountId: l.accountId,
          description: l.description || undefined,
          debit: safeDecimal(l.debit).toNumber(),
          credit: safeDecimal(l.credit).toNumber(),
        })),
      };
      const created = await apiFetch<{ id: string }>("/api/v1/journals", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      await queryClient.invalidateQueries({ queryKey: ["journals"] });
      await queryClient.invalidateQueries({ queryKey: ["accounts"] });
      router.push(`/workspace/journals/${created.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to save journal entry");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="max-w-4xl">
      <PageHeader
        title="New manual journal entry"
        description="Every posted entry requires at least two posting accounts with equal debit and credit totals."
      />

      {error ? (
        <div
          role="alert"
          className="mb-5 rounded-md border border-[var(--status-overdue-text)] bg-[var(--status-overdue-bg)] px-4 py-3 text-sm text-[var(--status-overdue-text)]"
        >
          {error}
        </div>
      ) : null}

      <div className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6 space-y-6">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <Field id="je-date" label="Entry date">
            <input
              id="je-date"
              type="date"
              required
              value={entryDate}
              onChange={(e) => setEntryDate(e.target.value)}
              className={inputClass}
            />
          </Field>
          <Field id="je-ref" label="Reference">
            <input
              id="je-ref"
              value={reference}
              onChange={(e) => setReference(e.target.value)}
              placeholder="e.g. ADJ-2026-11"
              className={inputClass}
            />
          </Field>
          <Field id="je-ccy" label="Currency">
            <input id="je-ccy" value={ccy} disabled className={inputClass} />
          </Field>
        </div>

        <Field id="je-desc" label="Description">
          <input
            id="je-desc"
            required
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Describe the accounting purpose of this journal entry"
            className={inputClass}
          />
        </Field>

        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold text-[var(--text-primary)]">Journal lines</h2>
            <Button variant="secondary" size="sm" onClick={addLine}>
              Add line
            </Button>
          </div>

          <div className="ledger-table-wrap">
            <table className="ledger-table">
              <thead>
                <tr>
                  <th className="min-w-[220px]">Posting Account</th>
                  <th className="min-w-[180px]">Line Memo</th>
                  <th className="!text-right min-w-[140px]">Debit ({ccy})</th>
                  <th className="!text-right min-w-[140px]">Credit ({ccy})</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {lines.map((line, idx) => (
                  <tr key={idx}>
                    <td>
                      <select
                        aria-label={`Line ${idx + 1} account`}
                        value={line.accountId}
                        onChange={(e) => updateLine(idx, { accountId: e.target.value })}
                        className={inputClass}
                      >
                        <option value="">Select account...</option>
                        {postingAccounts.map((a) => (
                          <option key={a.id} value={a.id}>
                            {a.code} · {a.name}
                          </option>
                        ))}
                      </select>
                    </td>
                    <td>
                      <input
                        aria-label={`Line ${idx + 1} memo`}
                        value={line.description}
                        onChange={(e) => updateLine(idx, { description: e.target.value })}
                        placeholder="Line detail"
                        className={inputClass}
                      />
                    </td>
                    <td>
                      <input
                        aria-label={`Line ${idx + 1} debit`}
                        type="number"
                        step="0.01"
                        min="0"
                        value={line.debit}
                        onChange={(e) => updateLine(idx, { debit: e.target.value, credit: e.target.value ? "" : line.credit })}
                        placeholder="0.00"
                        className={`${inputClass} text-right num`}
                      />
                    </td>
                    <td>
                      <input
                        aria-label={`Line ${idx + 1} credit`}
                        type="number"
                        step="0.01"
                        min="0"
                        value={line.credit}
                        onChange={(e) => updateLine(idx, { credit: e.target.value, debit: e.target.value ? "" : line.debit })}
                        placeholder="0.00"
                        className={`${inputClass} text-right num`}
                      />
                    </td>
                    <td>
                      {lines.length > 2 ? (
                        <button
                          type="button"
                          onClick={() => removeLine(idx)}
                          className="text-xs font-medium text-[var(--status-overdue-text)] hover:underline"
                        >
                          Remove
                        </button>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-4 rounded-md bg-[var(--bg-surface-subtle)] px-4 py-3 text-sm">
            <div className="flex items-center gap-3">
              <StatusBadge status={totals.balanced ? "BALANCED" : "UNBALANCED"} />
              {!totals.balanced ? (
                <span className="text-xs text-[var(--text-secondary)] num">
                  Out of balance by {formatMoney(totals.diff, ccy)}
                </span>
              ) : null}
            </div>
            <div className="flex gap-6 num font-semibold">
              <span>Debit: {formatMoney(totals.debit, ccy)}</span>
              <span>Credit: {formatMoney(totals.credit, ccy)}</span>
            </div>
          </div>
        </div>

        <div className="flex flex-wrap justify-end gap-3 pt-2">
          <Button variant="secondary" onClick={() => router.push("/workspace/journals")}>
            Cancel
          </Button>
          <Button
            variant="secondary"
            disabled={submitting}
            onClick={() => submitJournal(false)}
          >
            Save as draft
          </Button>
          <Button
            disabled={submitting || !totals.balanced}
            onClick={() => submitJournal(true)}
          >
            {submitting ? "Posting..." : "Post journal entry"}
          </Button>
        </div>
      </div>
    </div>
  );
}
