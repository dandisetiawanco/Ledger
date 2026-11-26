"use client";

import React, { useMemo, useState } from "react";
import Link from "next/link";
import { useQuery, useQueryClient } from "@tanstack/react-query";
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
  inputClass,
} from "@/components/ui";

interface BankAccountDto {
  id: string;
  name: string;
  institutionName: string;
  maskedAccountNumber: string | null;
  currencyCode: string;
  glAccountCode: string;
  glAccountName: string;
  ledgerBalance: string;
  statementBalance: string;
  reconciliationDifference: string;
  totalStatementLines: number;
  unmatchedLineCount: number;
}

interface AccountOpt {
  id: string;
  code: string;
  name: string;
  accountType: string;
  allowPosting: boolean;
}

export default function BankingPage() {
  const { auth } = useAuth();
  const queryClient = useQueryClient();
  const canCreate = canWriteAccounting(auth?.activeOrganization?.role);
  const orgCcy = auth?.activeOrganization?.baseCurrency ?? "IDR";

  const [modalOpen, setModalOpen] = useState(false);
  const [name, setName] = useState("");
  const [institution, setInstitution] = useState("");
  const [masked, setMasked] = useState("");
  const [glAccountId, setGlAccountId] = useState("");
  const [openingBalance, setOpeningBalance] = useState("0");
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const { data: bankAccounts = [], isLoading, error, refetch } = useQuery<BankAccountDto[]>({
    queryKey: ["bank-accounts"],
    queryFn: () => apiFetch<BankAccountDto[]>("/api/v1/banking/accounts"),
  });
  const { data: accounts = [] } = useQuery<AccountOpt[]>({
    queryKey: ["accounts"],
    queryFn: () => apiFetch<AccountOpt[]>("/api/v1/accounts"),
  });

  const assetAccounts = useMemo(
    () => accounts.filter((a) => a.accountType === "ASSET" && a.allowPosting),
    [accounts]
  );

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    setSaving(true);
    try {
      await apiFetch("/api/v1/banking/accounts", {
        method: "POST",
        body: JSON.stringify({
          name,
          institutionName: institution,
          maskedAccountNumber: masked || undefined,
          currencyCode: orgCcy,
          glAccountId,
          openingBalance: Number(openingBalance || 0),
        }),
      });
      await queryClient.invalidateQueries({ queryKey: ["bank-accounts"] });
      setModalOpen(false);
      setName("");
      setInstitution("");
      setMasked("");
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Failed to create bank account");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Bank & Cash Accounts"
        description="Manual bank and cash registers linked directly to Chart of Accounts posting accounts. Import CSV statements and reconcile without live bank credentials."
        actions={
          <>
            <a
              href="/api/v1/banking/sample-statement.csv"
              className="inline-flex min-h-[42px] items-center rounded-md border border-[var(--border-strong)] bg-[var(--bg-surface)] px-3.5 text-sm font-medium hover:bg-[var(--bg-surface-subtle)]"
            >
              Download sample CSV
            </a>
            {canCreate ? (
              <Button onClick={() => setModalOpen(true)}>Add bank account</Button>
            ) : null}
          </>
        }
      />

      {isLoading ? <LoadingState label="Loading bank accounts..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Unable to load bank accounts"}
          onRetry={() => refetch()}
        />
      ) : null}

      {!isLoading && !error ? (
        bankAccounts.length === 0 ? (
          <EmptyState
            title="No bank accounts configured"
            description="Link a bank or petty cash account to an Asset account in your Chart of Accounts to import CSV statements."
          />
        ) : (
          <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
            {bankAccounts.map((ba) => (
              <div
                key={ba.id}
                className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6 flex flex-col justify-between gap-4"
              >
                <div>
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <h2 className="text-base font-semibold text-[var(--text-primary)]">{ba.name}</h2>
                      <p className="text-xs text-[var(--text-secondary)]">
                        {ba.institutionName} {ba.maskedAccountNumber ? `· ${ba.maskedAccountNumber}` : ""}
                      </p>
                    </div>
                    <span className="font-mono-code text-xs rounded bg-[var(--bg-surface-subtle)] px-2 py-1 text-[var(--text-secondary)]">
                      GL {ba.glAccountCode}
                    </span>
                  </div>

                  <dl className="mt-5 grid grid-cols-2 gap-4 border-t border-[var(--border-subtle)] pt-4 text-sm">
                    <div>
                      <dt className="text-xs text-[var(--text-secondary)]">Posted ledger balance</dt>
                      <dd className="mt-1 font-semibold num text-[var(--text-primary)]">
                        {formatMoney(ba.ledgerBalance, ba.currencyCode)}
                      </dd>
                    </div>
                    <div>
                      <dt className="text-xs text-[var(--text-secondary)]">Statement balance</dt>
                      <dd className="mt-1 font-semibold num text-[var(--text-primary)]">
                        {formatMoney(ba.statementBalance, ba.currencyCode)}
                      </dd>
                    </div>
                  </dl>
                  <p className="mt-3 text-xs text-[var(--text-secondary)]">
                    {ba.totalStatementLines} imported statement line(s) ·{" "}
                    <strong className="text-[var(--text-primary)]">{ba.unmatchedLineCount} unmatched</strong>
                  </p>
                </div>

                <div className="flex flex-wrap gap-3 pt-2 border-t border-[var(--border-subtle)]">
                  <Link
                    href={`/workspace/banking/${ba.id}`}
                    className="inline-flex min-h-[40px] items-center rounded-md bg-[var(--brand-forest)] px-3.5 text-xs font-semibold text-[var(--text-on-brand)] hover:bg-[var(--brand-forest-hover)]"
                  >
                    Statements &amp; CSV import
                  </Link>
                  <Link
                    href={`/workspace/reconciliation?bankAccountId=${ba.id}`}
                    className="inline-flex min-h-[40px] items-center rounded-md border border-[var(--border-strong)] px-3.5 text-xs font-semibold text-[var(--text-primary)] hover:bg-[var(--bg-surface-subtle)]"
                  >
                    Reconcile ({ba.unmatchedLineCount})
                  </Link>
                </div>
              </div>
            ))}
          </div>
        )
      ) : null}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Link bank or cash account">
        <form onSubmit={handleCreate} className="space-y-4">
          {formError ? (
            <div role="alert" className="rounded-md bg-[var(--status-overdue-bg)] px-3.5 py-2.5 text-sm text-[var(--status-overdue-text)]">
              {formError}
            </div>
          ) : null}
          <Field id="ba-name" label="Account display name">
            <input id="ba-name" required value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Operating Checking Account" className={inputClass} />
          </Field>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field id="ba-inst" label="Institution">
              <input id="ba-inst" required value={institution} onChange={(e) => setInstitution(e.target.value)} placeholder="e.g. Bank Mandiri" className={inputClass} />
            </Field>
            <Field id="ba-mask" label="Masked reference">
              <input id="ba-mask" value={masked} onChange={(e) => setMasked(e.target.value)} placeholder="**** 1234" className={inputClass} />
            </Field>
          </div>
          <Field id="ba-gl" label="Linked Chart of Accounts asset">
            <select id="ba-gl" required value={glAccountId} onChange={(e) => setGlAccountId(e.target.value)} className={inputClass}>
              <option value="">Select asset account...</option>
              {assetAccounts.map((a) => (
                <option key={a.id} value={a.id}>{a.code} · {a.name}</option>
              ))}
            </select>
          </Field>
          <Field id="ba-open" label={`Opening balance (${orgCcy})`} hint="Creates a balanced opening journal against Owner Capital if > 0">
            <input id="ba-open" type="number" step="0.01" min="0" value={openingBalance} onChange={(e) => setOpeningBalance(e.target.value)} className={`${inputClass} num`} />
          </Field>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" disabled={saving}>{saving ? "Saving..." : "Create bank account"}</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
