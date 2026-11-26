"use client";

import React, { Suspense, useEffect, useState } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { formatMoney } from "@ledger/contracts";
import { apiFetch } from "@/lib/api";
import { useAuth } from "@/lib/providers";
import {
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  StatusBadge,
  TableRegion,
  inputClass,
} from "@/components/ui";

interface AccountOption {
  id: string;
  code: string;
  name: string;
  allowPosting: boolean;
}

interface GeneralLedgerResponse {
  accountId: string;
  accountCode: string;
  accountName: string;
  accountType: string;
  normalBalance: string;
  openingBalance: string;
  periodDebitTotal: string;
  periodCreditTotal: string;
  closingBalance: string;
  rows: Array<{
    lineId: string;
    journalEntryId: string;
    entryNumber: string;
    entryDate: string;
    entryDescription: string;
    reference: string | null;
    sourceType: string;
    debit: string;
    credit: string;
    runningBalance: string;
  }>;
}

function GeneralLedgerContent() {
  const searchParams = useSearchParams();
  const { auth } = useAuth();
  const ccy = auth?.activeOrganization?.baseCurrency ?? "IDR";

  const { data: accounts = [] } = useQuery<AccountOption[]>({
    queryKey: ["accounts"],
    queryFn: () => apiFetch<AccountOption[]>("/api/v1/accounts"),
  });

  const postingAccounts = accounts.filter((a) => a.allowPosting);
  const [accountId, setAccountId] = useState(searchParams.get("accountId") || "");
  const [fromDate, setFromDate] = useState("");
  const [toDate, setToDate] = useState("");
  const [search, setSearch] = useState("");

  useEffect(() => {
    if (!accountId && postingAccounts.length > 0) {
      const bank = postingAccounts.find((a) => a.code === "1020") ?? postingAccounts[0];
      setAccountId(bank.id);
    }
  }, [accountId, postingAccounts]);

  const { data, isLoading, error, refetch } = useQuery<GeneralLedgerResponse>({
    queryKey: ["general-ledger", accountId, fromDate, toDate, search],
    enabled: !!accountId,
    queryFn: () => {
      const params = new URLSearchParams({ accountId });
      if (fromDate) params.set("fromDate", fromDate);
      if (toDate) params.set("toDate", toDate);
      if (search.trim()) params.set("search", search.trim());
      return apiFetch<GeneralLedgerResponse>(`/api/v1/general-ledger?${params.toString()}`);
    },
  });

  const exportParams = new URLSearchParams({ accountId });
  if (fromDate) exportParams.set("fromDate", fromDate);
  if (toDate) exportParams.set("toDate", toDate);

  return (
    <div>
      <PageHeader
        title="General Ledger"
        description="Complete posted transaction register per account with opening balance, running normal balance, and closing balance. Draft entries are excluded."
        actions={
          accountId ? (
            <>
              <a
                href={`/api/v1/reports/general-ledger/export?${exportParams.toString()}&format=csv`}
                className="inline-flex min-h-[42px] items-center rounded-md border border-[var(--border-strong)] bg-[var(--bg-surface)] px-3.5 text-sm font-medium hover:bg-[var(--bg-surface-subtle)]"
              >
                Export CSV
              </a>
              <a
                href={`/api/v1/reports/general-ledger/export?${exportParams.toString()}&format=pdf`}
                target="_blank"
                rel="noreferrer"
                className="inline-flex min-h-[42px] items-center rounded-md bg-[var(--brand-forest)] px-3.5 text-sm font-medium text-[var(--text-on-brand)] hover:bg-[var(--brand-forest-hover)]"
              >
                Export PDF
              </a>
            </>
          ) : null
        }
      />

      <div className="mb-5 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
        <select
          aria-label="Select account"
          value={accountId}
          onChange={(e) => setAccountId(e.target.value)}
          className={inputClass}
        >
          {postingAccounts.map((a) => (
            <option key={a.id} value={a.id}>
              {a.code} · {a.name}
            </option>
          ))}
        </select>
        <input
          type="date"
          aria-label="From date"
          value={fromDate}
          onChange={(e) => setFromDate(e.target.value)}
          className={inputClass}
        />
        <input
          type="date"
          aria-label="To date"
          value={toDate}
          onChange={(e) => setToDate(e.target.value)}
          className={inputClass}
        />
        <input
          type="search"
          aria-label="Search ledger transactions"
          placeholder="Search journal or reference..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className={inputClass}
        />
      </div>

      {isLoading ? <LoadingState label="Computing account general ledger..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Unable to load general ledger"}
          onRetry={() => refetch()}
        />
      ) : null}

      {data ? (
        <div className="space-y-4">
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-4 text-sm">
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Account</p>
              <p className="mt-0.5 font-semibold">{data.accountCode} · {data.accountName}</p>
            </div>
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Opening balance</p>
              <p className="mt-0.5 font-semibold num">{formatMoney(data.openingBalance, ccy)}</p>
            </div>
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Period Debit / Credit</p>
              <p className="mt-0.5 text-xs font-semibold num">
                Dr {formatMoney(data.periodDebitTotal, ccy)} / Cr {formatMoney(data.periodCreditTotal, ccy)}
              </p>
            </div>
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Closing balance ({data.normalBalance})</p>
              <p className="mt-0.5 font-semibold num text-[var(--brand-forest)]">
                {formatMoney(data.closingBalance, ccy)}
              </p>
            </div>
          </div>

          {data.rows.length === 0 ? (
            <EmptyState
              title="No posted activity in this range"
              description="This account has no posted journal lines within the selected date range."
            />
          ) : (
            <TableRegion label="General ledger activity">
              <table className="ledger-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Journal</th>
                    <th>Source</th>
                    <th>Description</th>
                    <th className="!text-right">Debit</th>
                    <th className="!text-right">Credit</th>
                    <th className="!text-right">Running Balance</th>
                  </tr>
                </thead>
                <tbody>
                  <tr className="bg-[var(--bg-surface-subtle)]/60 text-xs font-semibold">
                    <td colSpan={6}>Opening Balance</td>
                    <td className="text-right num">{formatMoney(data.openingBalance, ccy)}</td>
                  </tr>
                  {data.rows.map((r) => (
                    <tr key={r.lineId}>
                      <td className="num whitespace-nowrap text-[var(--text-secondary)]">{r.entryDate}</td>
                      <td className="font-mono-code text-xs font-semibold">
                        <Link
                          href={`/workspace/journals/${r.journalEntryId}`}
                          className="text-[var(--brand-forest)] hover:underline"
                        >
                          {r.entryNumber}
                        </Link>
                      </td>
                      <td><StatusBadge status={r.sourceType} /></td>
                      <td>{r.entryDescription}</td>
                      <td className="text-right num whitespace-nowrap">
                        {Number(r.debit) > 0 ? formatMoney(r.debit, ccy) : ""}
                      </td>
                      <td className="text-right num whitespace-nowrap">
                        {Number(r.credit) > 0 ? formatMoney(r.credit, ccy) : ""}
                      </td>
                      <td className="text-right num font-semibold whitespace-nowrap">
                        {formatMoney(r.runningBalance, ccy)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </TableRegion>
          )}
        </div>
      ) : null}
    </div>
  );
}

export default function GeneralLedgerPage() {
  return (
    <Suspense fallback={<LoadingState label="Loading general ledger..." />}>
      <GeneralLedgerContent />
    </Suspense>
  );
}
