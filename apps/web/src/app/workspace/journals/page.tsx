"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { canWriteAccounting, formatMoney } from "@ledger/contracts";
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

interface JournalSummary {
  id: string;
  entryNumber: string;
  entryDate: string;
  description: string;
  reference: string | null;
  status: string;
  sourceType: string;
  currencyCode: string;
  totalDebit: string;
  totalCredit: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export default function JournalsListPage() {
  const { auth } = useAuth();
  const ccy = auth?.activeOrganization?.baseCurrency ?? "IDR";
  const canCreate = canWriteAccounting(auth?.activeOrganization?.role);

  const [status, setStatus] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);

  const { data, isLoading, error, refetch } = useQuery<PageResponse<JournalSummary>>({
    queryKey: ["journals", status, search, page],
    queryFn: () => {
      const params = new URLSearchParams({ page: String(page), size: "25" });
      if (status) params.set("status", status);
      if (search.trim()) params.set("search", search.trim());
      return apiFetch<PageResponse<JournalSummary>>(`/api/v1/journals?${params.toString()}`);
    },
  });

  return (
    <div>
      <PageHeader
        title="Journal Entries"
        description="Double-entry accounting register covering automated invoice, payment, and expense postings alongside manual journals."
        actions={
          canCreate ? (
            <Link
              href="/workspace/journals/new"
              className="inline-flex min-h-[42px] items-center rounded-md bg-[var(--brand-forest)] px-4 text-sm font-medium text-[var(--text-on-brand)] hover:bg-[var(--brand-forest-hover)]"
            >
              New manual journal
            </Link>
          ) : null
        }
      />

      <div className="mb-5 flex flex-wrap items-center gap-3">
        <label htmlFor="je-status" className="sr-only">Filter by status</label>
        <select
          id="je-status"
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(0);
          }}
          className={`${inputClass} !w-48`}
        >
          <option value="">All statuses</option>
          <option value="POSTED">Posted</option>
          <option value="DRAFT">Draft</option>
          <option value="PENDING_APPROVAL">Pending approval</option>
          <option value="REVERSED">Reversed</option>
        </select>
        <label htmlFor="je-search" className="sr-only">Search journals</label>
        <input
          id="je-search"
          type="search"
          placeholder="Search entry number, description, or reference..."
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          className={`${inputClass} !w-80`}
        />
      </div>

      {isLoading ? <LoadingState label="Loading journal entries..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Failed to load journals"}
          onRetry={() => refetch()}
        />
      ) : null}

      {data ? (
        data.content.length === 0 ? (
          <EmptyState
            title="No journal entries found"
            description="No journal entries match the active filters, or no accounting transactions have been recorded yet."
          />
        ) : (
          <>
            <TableRegion label="Journal entries table">
              <table className="ledger-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Entry #</th>
                    <th>Source</th>
                    <th>Description</th>
                    <th>Status</th>
                    <th className="!text-right">Total Debit ({ccy})</th>
                    <th className="!text-right">Total Credit ({ccy})</th>
                  </tr>
                </thead>
                <tbody>
                  {data.content.map((j) => (
                    <tr key={j.id}>
                      <td className="num whitespace-nowrap text-[var(--text-secondary)]">{j.entryDate}</td>
                      <td className="font-mono-code text-xs font-semibold">
                        <Link
                          href={`/workspace/journals/${j.id}`}
                          className="text-[var(--brand-forest)] hover:underline"
                        >
                          {j.entryNumber}
                        </Link>
                      </td>
                      <td><StatusBadge status={j.sourceType} /></td>
                      <td>
                        <div className="font-medium text-[var(--text-primary)]">{j.description}</div>
                        {j.reference ? (
                          <div className="text-xs text-[var(--text-secondary)]">Ref: {j.reference}</div>
                        ) : null}
                      </td>
                      <td><StatusBadge status={j.status} /></td>
                      <td className="text-right num font-semibold whitespace-nowrap">
                        {formatMoney(j.totalDebit, ccy)}
                      </td>
                      <td className="text-right num font-semibold whitespace-nowrap">
                        {formatMoney(j.totalCredit, ccy)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </TableRegion>

            <div className="mt-4 flex items-center justify-between text-xs text-[var(--text-secondary)]">
              <span>
                Showing page {data.number + 1} of {Math.max(data.totalPages, 1)} ({data.totalElements} entries)
              </span>
              <div className="flex gap-2">
                <button
                  type="button"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                  className="min-h-[36px] rounded border border-[var(--border-strong)] bg-[var(--bg-surface)] px-3 font-medium text-[var(--text-primary)]"
                >
                  Previous
                </button>
                <button
                  type="button"
                  disabled={page + 1 >= data.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                  className="min-h-[36px] rounded border border-[var(--border-strong)] bg-[var(--bg-surface)] px-3 font-medium text-[var(--text-primary)]"
                >
                  Next
                </button>
              </div>
            </div>
          </>
        )
      ) : null}
    </div>
  );
}
