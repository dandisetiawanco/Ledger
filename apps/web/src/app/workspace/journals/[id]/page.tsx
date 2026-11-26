"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { canWriteAccounting, formatMoney } from "@ledger/contracts";
import { apiFetch, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/providers";
import {
  Button,
  ErrorState,
  LoadingState,
  PageHeader,
  StatusBadge,
  TableRegion,
} from "@/components/ui";

interface JournalDetail {
  id: string;
  entryNumber: string;
  entryDate: string;
  description: string;
  reference: string | null;
  status: string;
  sourceType: string;
  currencyCode: string;
  exchangeRate: string;
  totalDebit: string;
  totalCredit: string;
  reversedEntryId: string | null;
  reversalEntryId: string | null;
  postedAt: string | null;
  lines: Array<{
    id: string;
    lineNumber: number;
    accountId: string;
    accountCode: string;
    accountName: string;
    description: string | null;
    debitAmount: string;
    creditAmount: string;
  }>;
}

export default function JournalDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { auth } = useAuth();
  const ccy = auth?.activeOrganization?.baseCurrency ?? "IDR";
  const canEdit = canWriteAccounting(auth?.activeOrganization?.role);

  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const { data: journal, isLoading, error, refetch } = useQuery<JournalDetail>({
    queryKey: ["journal", params.id],
    queryFn: () => apiFetch<JournalDetail>(`/api/v1/journals/${params.id}`),
  });

  async function handlePost() {
    setActionError(null);
    setBusy(true);
    try {
      await apiFetch(`/api/v1/journals/${params.id}/post`, { method: "POST" });
      await queryClient.invalidateQueries({ queryKey: ["journal", params.id] });
      await queryClient.invalidateQueries({ queryKey: ["journals"] });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to post journal");
    } finally {
      setBusy(false);
    }
  }

  async function handleReverse() {
    setActionError(null);
    setBusy(true);
    try {
      const reversal = await apiFetch<JournalDetail>(`/api/v1/journals/${params.id}/reverse`, {
        method: "POST",
        body: JSON.stringify({ reason: "Manual reversal from journal detail" }),
      });
      await queryClient.invalidateQueries({ queryKey: ["journals"] });
      router.push(`/workspace/journals/${reversal.id}`);
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to reverse journal");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <PageHeader
        title={journal ? `Journal ${journal.entryNumber}` : "Journal entry"}
        description={journal?.description}
        actions={
          journal && canEdit ? (
            <>
              {journal.status === "DRAFT" ? (
                <Button onClick={handlePost} disabled={busy}>
                  Post journal
                </Button>
              ) : null}
              {journal.status === "POSTED" && !journal.reversalEntryId ? (
                <Button variant="secondary" onClick={handleReverse} disabled={busy}>
                  Create reversal entry
                </Button>
              ) : null}
            </>
          ) : null
        }
      />

      {actionError ? <ErrorState message={actionError} /> : null}
      {isLoading ? <LoadingState label="Loading journal entry..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Journal not found"}
          onRetry={() => refetch()}
        />
      ) : null}

      {journal ? (
        <div className="space-y-6">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-5 text-sm">
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Status</p>
              <div className="mt-1"><StatusBadge status={journal.status} /></div>
            </div>
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Entry date</p>
              <p className="mt-1 font-semibold num">{journal.entryDate}</p>
            </div>
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Source</p>
              <p className="mt-1 font-semibold">{journal.sourceType.replaceAll("_", " ")}</p>
            </div>
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Currency / FX rate</p>
              <p className="mt-1 font-semibold num">
                {journal.currencyCode} (Rate {journal.exchangeRate})
              </p>
            </div>
          </div>

          {journal.reversalEntryId ? (
            <div className="rounded-md border border-[var(--border-subtle)] bg-[var(--bg-surface-subtle)] px-4 py-3 text-sm">
              This journal was reversed by{" "}
              <Link
                href={`/workspace/journals/${journal.reversalEntryId}`}
                className="font-semibold text-[var(--brand-forest)] hover:underline"
              >
                linked reversal entry
              </Link>
              .
            </div>
          ) : null}

          {journal.reversedEntryId ? (
            <div className="rounded-md border border-[var(--border-subtle)] bg-[var(--bg-surface-subtle)] px-4 py-3 text-sm">
              Reversal entry offsetting{" "}
              <Link
                href={`/workspace/journals/${journal.reversedEntryId}`}
                className="font-semibold text-[var(--brand-forest)] hover:underline"
              >
                original journal entry
              </Link>
              .
            </div>
          ) : null}

          <TableRegion label="Journal lines">
            <table className="ledger-table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Account</th>
                  <th>Memo</th>
                  <th className="!text-right">Debit ({ccy})</th>
                  <th className="!text-right">Credit ({ccy})</th>
                </tr>
              </thead>
              <tbody>
                {journal.lines.map((l) => (
                  <tr key={l.id}>
                    <td className="num text-[var(--text-secondary)]">{l.lineNumber}</td>
                    <td>
                      <Link
                        href={`/workspace/general-ledger?accountId=${l.accountId}`}
                        className="font-medium text-[var(--text-primary)] hover:text-[var(--brand-forest)] hover:underline"
                      >
                        <span className="font-mono-code text-xs text-[var(--text-secondary)] mr-2">
                          {l.accountCode}
                        </span>
                        {l.accountName}
                      </Link>
                    </td>
                    <td className="text-[var(--text-secondary)]">{l.description || ""}</td>
                    <td className="text-right num font-semibold">
                      {Number(l.debitAmount) > 0 ? formatMoney(l.debitAmount, ccy) : ""}
                    </td>
                    <td className="text-right num font-semibold">
                      {Number(l.creditAmount) > 0 ? formatMoney(l.creditAmount, ccy) : ""}
                    </td>
                  </tr>
                ))}
                <tr className="bg-[var(--bg-surface-subtle)] font-semibold">
                  <td colSpan={3}>Total (Base Currency)</td>
                  <td className="text-right num">{formatMoney(journal.totalDebit, ccy)}</td>
                  <td className="text-right num">{formatMoney(journal.totalCredit, ccy)}</td>
                </tr>
              </tbody>
            </table>
          </TableRegion>
        </div>
      ) : null}
    </div>
  );
}
