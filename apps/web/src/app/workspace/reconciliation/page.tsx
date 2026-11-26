"use client";

import React, { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { canOperateFinance, canWriteAccounting, formatMoney } from "@ledger/contracts";
import { apiFetch, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/providers";
import {
  Button,
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  StatusBadge,
  TableRegion,
  inputClass,
} from "@/components/ui";

interface BankAccountOpt { id: string; name: string; currencyCode: string }
interface AccountOpt { id: string; code: string; name: string; allowPosting: boolean }
interface ReconWorkspace {
  bankAccountId: string;
  bankAccountName: string;
  currencyCode: string;
  unmatchedStatementLines: Array<{
    id: string;
    transactionDate: string;
    description: string;
    reference: string | null;
    amount: string;
  }>;
  unmatchedJournalLines: Array<{
    journalLineId: string;
    entryNumber: string;
    entryDate: string;
    description: string;
    reference: string | null;
    amount: string;
  }>;
  suggestedMatches: Array<{
    bankStatementLineId: string;
    transactionDate: string;
    description: string;
    reference: string | null;
    amount: string;
    bestCandidate: {
      journalLineId: string;
      entryNumber: string;
      entryDate: string;
      description: string;
      confidenceScore: number;
      matchReason: string;
    };
  }>;
  history: Array<{
    id: string;
    matchedAmount: string;
    matchType: string;
    status: string;
    notes: string | null;
    reconciledAt: string;
  }>;
}

function ReconciliationContent() {
  const searchParams = useSearchParams();
  const queryClient = useQueryClient();
  const { auth } = useAuth();
  const canOperate = canOperateFinance(auth?.activeOrganization?.role);
  const canAccount = canWriteAccounting(auth?.activeOrganization?.role);

  const { data: bankAccounts = [] } = useQuery<BankAccountOpt[]>({
    queryKey: ["bank-accounts"],
    queryFn: () => apiFetch<BankAccountOpt[]>("/api/v1/banking/accounts"),
  });
  const { data: accounts = [] } = useQuery<AccountOpt[]>({
    queryKey: ["accounts"],
    queryFn: () => apiFetch<AccountOpt[]>("/api/v1/accounts"),
  });

  const [bankId, setBankId] = useState(searchParams.get("bankAccountId") || "");
  const [offsetAccountId, setOffsetAccountId] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!bankId && bankAccounts.length > 0) {
      setBankId(bankAccounts[0].id);
    }
  }, [bankId, bankAccounts]);

  useEffect(() => {
    if (!offsetAccountId && accounts.length > 0) {
      const fee = accounts.find((a) => a.code === "5060") ?? accounts.find((a) => a.allowPosting);
      if (fee) setOffsetAccountId(fee.id);
    }
  }, [offsetAccountId, accounts]);

  const { data: ws, isLoading, error, refetch } = useQuery<ReconWorkspace>({
    queryKey: ["reconciliation", bankId],
    enabled: !!bankId,
    queryFn: () => apiFetch<ReconWorkspace>(`/api/v1/reconciliation/bank-accounts/${bankId}`),
  });

  async function confirmMatch(stmtLineId: string, journalLineId: string, matchType: string) {
    setBusy(true);
    setActionError(null);
    try {
      await apiFetch("/api/v1/reconciliation/match", {
        method: "POST",
        body: JSON.stringify({ bankStatementLineId: stmtLineId, journalLineId, matchType }),
      });
      await queryClient.invalidateQueries({ queryKey: ["reconciliation", bankId] });
      await queryClient.invalidateQueries({ queryKey: ["bank-accounts"] });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to reconcile line");
    } finally {
      setBusy(false);
    }
  }

  async function createAdjustment(stmtLineId: string, description: string) {
    if (!offsetAccountId) return;
    setBusy(true);
    setActionError(null);
    try {
      await apiFetch("/api/v1/reconciliation/create-journal-and-match", {
        method: "POST",
        body: JSON.stringify({ bankStatementLineId: stmtLineId, offsetAccountId, description }),
      });
      await queryClient.invalidateQueries({ queryKey: ["reconciliation", bankId] });
      await queryClient.invalidateQueries({ queryKey: ["bank-accounts"] });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to post bank adjustment");
    } finally {
      setBusy(false);
    }
  }

  async function unmatch(reconId: string) {
    setBusy(true);
    setActionError(null);
    try {
      await apiFetch(`/api/v1/reconciliation/${reconId}/unmatch`, { method: "POST" });
      await queryClient.invalidateQueries({ queryKey: ["reconciliation", bankId] });
      await queryClient.invalidateQueries({ queryKey: ["bank-accounts"] });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to unmatch");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Bank Reconciliation"
        description="Deterministic matching between imported bank statement lines and posted GL bank entries. Matches require explicit confirmation and never duplicate entries."
        actions={
          <select
            aria-label="Select bank account"
            value={bankId}
            onChange={(e) => setBankId(e.target.value)}
            className={`${inputClass} !w-72`}
          >
            {bankAccounts.map((ba) => (
              <option key={ba.id} value={ba.id}>{ba.name}</option>
            ))}
          </select>
        }
      />

      {actionError ? <ErrorState message={actionError} /> : null}
      {isLoading ? <LoadingState label="Evaluating reconciliation matches..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Unable to load reconciliation workspace"}
          onRetry={() => refetch()}
        />
      ) : null}

      {ws ? (
        <>
          <section className="space-y-3">
            <h2 className="text-base font-semibold">
              Suggested deterministic matches ({ws.suggestedMatches.length})
            </h2>
            {ws.suggestedMatches.length === 0 ? (
              <EmptyState
                title="No suggested matches pending"
                description="All exact-amount matches within the date window have been reconciled."
              />
            ) : (
              <div className="space-y-3">
                {ws.suggestedMatches.map((sug) => (
                  <div
                    key={sug.bankStatementLineId}
                    className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-4 flex flex-col lg:flex-row lg:items-center justify-between gap-4"
                  >
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 flex-1 text-sm">
                      <div>
                        <p className="text-xs font-semibold text-[var(--text-secondary)]">Bank statement line</p>
                        <p className="font-semibold">{sug.description}</p>
                        <p className="text-xs text-[var(--text-secondary)] num">
                          {sug.transactionDate} · {formatMoney(sug.amount, ws.currencyCode)}
                        </p>
                      </div>
                      <div className="md:border-l md:border-[var(--border-subtle)] md:pl-4">
                        <p className="text-xs font-semibold text-[var(--brand-forest)]">
                          Suggested ledger match ({sug.bestCandidate.confidenceScore}%)
                        </p>
                        <p className="font-semibold">
                          {sug.bestCandidate.entryNumber} · {sug.bestCandidate.description}
                        </p>
                        <p className="text-xs text-[var(--text-secondary)]">
                          {sug.bestCandidate.matchReason}
                        </p>
                      </div>
                    </div>
                    {canOperate ? (
                      <Button
                        size="sm"
                        disabled={busy}
                        onClick={() =>
                          confirmMatch(sug.bankStatementLineId, sug.bestCandidate.journalLineId, "EXACT")
                        }
                      >
                        Confirm match
                      </Button>
                    ) : null}
                  </div>
                ))}
              </div>
            )}
          </section>

          <section className="space-y-3">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-base font-semibold">
                Unmatched bank statement lines ({ws.unmatchedStatementLines.length})
              </h2>
              {canAccount ? (
                <div className="flex items-center gap-2 text-xs">
                  <label htmlFor="recon-offset" className="text-[var(--text-secondary)]">
                    Adjustment offset account:
                  </label>
                  <select
                    id="recon-offset"
                    value={offsetAccountId}
                    onChange={(e) => setOffsetAccountId(e.target.value)}
                    className={`${inputClass} !w-60 !min-h-[36px] text-xs`}
                  >
                    {accounts.filter((a) => a.allowPosting).map((a) => (
                      <option key={a.id} value={a.id}>{a.code} · {a.name}</option>
                    ))}
                  </select>
                </div>
              ) : null}
            </div>

            {ws.unmatchedStatementLines.length === 0 ? (
              <EmptyState
                title="All imported statement lines are reconciled"
                description="Import a new CSV statement under Bank Accounts to reconcile additional periods."
              />
            ) : (
              <TableRegion label="Unmatched bank statement lines">
                <table className="ledger-table">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Description</th>
                      <th>Reference</th>
                      <th className="!text-right">Amount</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {ws.unmatchedStatementLines.map((line) => (
                      <tr key={line.id}>
                        <td className="num whitespace-nowrap">{line.transactionDate}</td>
                        <td className="font-medium">{line.description}</td>
                        <td className="font-mono-code text-xs text-[var(--text-secondary)]">{line.reference || ""}</td>
                        <td className="text-right num font-semibold whitespace-nowrap">
                          {formatMoney(line.amount, ws.currencyCode)}
                        </td>
                        <td>
                          {canAccount ? (
                            <Button
                              variant="secondary"
                              size="sm"
                              disabled={busy}
                              onClick={() => createAdjustment(line.id, line.description)}
                            >
                              Post journal &amp; reconcile
                            </Button>
                          ) : null}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </TableRegion>
            )}
          </section>

          <section className="space-y-3">
            <h2 className="text-base font-semibold">Reconciliation history ({ws.history.length})</h2>
            {ws.history.length === 0 ? (
              <p className="text-sm text-[var(--text-secondary)]">No reconciliation records yet.</p>
            ) : (
              <TableRegion label="Reconciliation history">
                <table className="ledger-table">
                  <thead>
                    <tr>
                      <th>Status</th>
                      <th>Match Type</th>
                      <th className="!text-right">Amount</th>
                      <th>Notes</th>
                      <th>Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {ws.history.map((h) => (
                      <tr key={h.id}>
                        <td><StatusBadge status={h.status} /></td>
                        <td className="text-xs">{h.matchType}</td>
                        <td className="text-right num font-semibold">{formatMoney(h.matchedAmount, ws.currencyCode)}</td>
                        <td className="text-xs text-[var(--text-secondary)]">{h.notes || ""}</td>
                        <td>
                          {canAccount && h.status === "MATCHED" ? (
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() => unmatch(h.id)}
                              className="text-xs font-semibold text-[var(--status-overdue-text)] hover:underline"
                            >
                              Unmatch
                            </button>
                          ) : null}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </TableRegion>
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}

export default function ReconciliationPage() {
  return (
    <Suspense fallback={<LoadingState label="Loading reconciliation workspace..." />}>
      <ReconciliationContent />
    </Suspense>
  );
}
