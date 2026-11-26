"use client";

import React, { useMemo, useState } from "react";
import Link from "next/link";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { canWriteAccounting, formatMoney, type AccountType } from "@ledger/contracts";
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

interface AccountDto {
  id: string;
  code: string;
  name: string;
  accountType: AccountType;
  normalBalance: "DEBIT" | "CREDIT";
  parentAccountId: string | null;
  systemCode: string | null;
  cashFlowCategory: string | null;
  description: string | null;
  allowPosting: boolean;
  active: boolean;
  archived: boolean;
  totalDebit: string;
  totalCredit: string;
  balance: string;
}

const ACCOUNT_TYPES: AccountType[] = ["ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE"];

export default function AccountsPage() {
  const { auth } = useAuth();
  const queryClient = useQueryClient();
  const ccy = auth?.activeOrganization?.baseCurrency ?? "IDR";
  const canEdit = canWriteAccounting(auth?.activeOrganization?.role);

  const [typeFilter, setTypeFilter] = useState<string>("ALL");
  const [search, setSearch] = useState("");
  const [modalOpen, setModalOpen] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [accountType, setAccountType] = useState<AccountType>("EXPENSE");
  const [description, setDescription] = useState("");
  const [allowPosting, setAllowPosting] = useState(true);

  const { data: accounts = [], isLoading, error, refetch } = useQuery<AccountDto[]>({
    queryKey: ["accounts"],
    queryFn: () => apiFetch<AccountDto[]>("/api/v1/accounts"),
  });

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return accounts.filter((a) => {
      if (typeFilter !== "ALL" && a.accountType !== typeFilter) return false;
      if (q && !a.code.toLowerCase().includes(q) && !a.name.toLowerCase().includes(q)) return false;
      return true;
    });
  }, [accounts, typeFilter, search]);

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    setSaving(true);
    try {
      await apiFetch("/api/v1/accounts", {
        method: "POST",
        body: JSON.stringify({ code, name, accountType, description, allowPosting }),
      });
      await queryClient.invalidateQueries({ queryKey: ["accounts"] });
      setModalOpen(false);
      setCode("");
      setName("");
      setDescription("");
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Could not create account");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Chart of Accounts"
        description="Normalized account hierarchy across Assets, Liabilities, Equity, Revenue, and Expenses. Balances are derived strictly from posted journal lines."
        actions={
          canEdit ? (
            <Button onClick={() => setModalOpen(true)}>Add account</Button>
          ) : null
        }
      />

      <div className="mb-5 flex flex-wrap items-center gap-3">
        <div className="flex flex-wrap gap-1.5" role="group" aria-label="Filter by account type">
          {["ALL", ...ACCOUNT_TYPES].map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => setTypeFilter(t)}
              className={`min-h-[38px] rounded-md px-3 text-xs font-semibold border transition-colors ${
                typeFilter === t
                  ? "bg-[var(--brand-forest)] text-[var(--text-on-brand)] border-[var(--brand-forest)]"
                  : "bg-[var(--bg-surface)] text-[var(--text-secondary)] border-[var(--border-subtle)] hover:text-[var(--text-primary)]"
              }`}
            >
              {t === "ALL" ? "All types" : t}
            </button>
          ))}
        </div>
        <label htmlFor="acct-search" className="sr-only">Search accounts</label>
        <input
          id="acct-search"
          type="search"
          placeholder="Filter by code or name..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className={`${inputClass} !w-64`}
        />
      </div>

      {isLoading ? <LoadingState label="Loading Chart of Accounts..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Unable to load accounts"}
          onRetry={() => refetch()}
        />
      ) : null}

      {!isLoading && !error ? (
        filtered.length === 0 ? (
          <EmptyState
            title="No matching accounts"
            description="Adjust your account classification filter or search text."
          />
        ) : (
          <TableRegion label="Chart of Accounts">
            <table className="ledger-table">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Account Name</th>
                  <th>Classification</th>
                  <th>Normal</th>
                  <th>Posting</th>
                  <th className="!text-right">Posted Balance ({ccy})</th>
                  <th>Ledger</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((acc) => (
                  <tr key={acc.id} className={!acc.allowPosting ? "bg-[var(--bg-surface-subtle)]/55 font-semibold" : ""}>
                    <td className="font-mono-code text-xs">{acc.code}</td>
                    <td>
                      <div className={!acc.allowPosting ? "font-semibold" : "pl-3"}>
                        {acc.name}
                      </div>
                      {acc.description ? (
                        <div className={`text-xs text-[var(--text-secondary)] ${acc.allowPosting ? "pl-3" : ""}`}>
                          {acc.description}
                        </div>
                      ) : null}
                    </td>
                    <td>
                      <StatusBadge status={acc.accountType} />
                    </td>
                    <td className="text-xs text-[var(--text-secondary)]">{acc.normalBalance}</td>
                    <td className="text-xs text-[var(--text-secondary)]">
                      {acc.allowPosting ? "Posting" : "Header"}
                    </td>
                    <td className="text-right num font-semibold whitespace-nowrap">
                      {acc.allowPosting ? formatMoney(acc.balance, ccy) : ""}
                    </td>
                    <td>
                      {acc.allowPosting ? (
                        <Link
                          href={`/workspace/general-ledger?accountId=${acc.id}`}
                          className="text-xs font-semibold text-[var(--brand-forest)] hover:underline"
                        >
                          General ledger
                        </Link>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableRegion>
        )
      ) : null}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Create posting account">
        <form onSubmit={handleCreate} className="space-y-4">
          {formError ? (
            <div role="alert" className="rounded-md bg-[var(--status-overdue-bg)] px-3.5 py-2.5 text-sm text-[var(--status-overdue-text)]">
              {formError}
            </div>
          ) : null}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field id="new-acc-code" label="Account code">
              <input id="new-acc-code" required value={code} onChange={(e) => setCode(e.target.value)} placeholder="e.g. 5080" className={inputClass} />
            </Field>
            <Field id="new-acc-type" label="Classification">
              <select id="new-acc-type" value={accountType} onChange={(e) => setAccountType(e.target.value as AccountType)} className={inputClass}>
                {ACCOUNT_TYPES.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </Field>
          </div>
          <Field id="new-acc-name" label="Account name">
            <input id="new-acc-name" required value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Travel & Client Meetings" className={inputClass} />
          </Field>
          <Field id="new-acc-desc" label="Description">
            <input id="new-acc-desc" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Optional accounting note" className={inputClass} />
          </Field>
          <label className="flex items-center gap-2 text-sm text-[var(--text-primary)]">
            <input type="checkbox" checked={allowPosting} onChange={(e) => setAllowPosting(e.target.checked)} />
            Allow direct journal postings
          </label>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" disabled={saving}>{saving ? "Creating..." : "Create account"}</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
