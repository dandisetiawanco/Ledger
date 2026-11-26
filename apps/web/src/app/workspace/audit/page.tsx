"use client";

import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api";
import {
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  StatusBadge,
  TableRegion,
  inputClass,
} from "@/components/ui";

interface AuditEventItem {
  id: string;
  actorEmail: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  requestId: string | null;
  summary: string;
  occurredAt: string;
}

interface PageResp {
  content: AuditEventItem[];
  totalElements: number;
}

export default function AuditPage() {
  const [search, setSearch] = useState("");
  const [entityType, setEntityType] = useState("");

  const { data, isLoading, error, refetch } = useQuery<PageResp>({
    queryKey: ["audit", search, entityType],
    queryFn: () => {
      const p = new URLSearchParams({ size: "60" });
      if (search.trim()) p.set("search", search.trim());
      if (entityType) p.set("entityType", entityType);
      return apiFetch<PageResp>(`/api/v1/audit?${p.toString()}`);
    },
  });

  return (
    <div>
      <PageHeader
        title="Financial Audit Trail"
        description="Append-oriented record of authentication, postings, reversals, approvals, reconciliations, period locks, and document exports."
      />

      <div className="mb-5 flex flex-wrap gap-3">
        <select aria-label="Filter by entity" value={entityType} onChange={(e) => setEntityType(e.target.value)} className={`${inputClass} !w-52`}>
          <option value="">All entities</option>
          <option value="JOURNAL_ENTRY">Journal entries</option>
          <option value="INVOICE">Invoices</option>
          <option value="PAYMENT">Payments</option>
          <option value="EXPENSE">Expenses</option>
          <option value="RECONCILIATION">Reconciliation</option>
          <option value="ACCOUNTING_PERIOD">Accounting periods</option>
          <option value="REPORT">Report exports</option>
        </select>
        <input
          type="search"
          aria-label="Search audit events"
          placeholder="Search actor or summary..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className={`${inputClass} !w-72`}
        />
      </div>

      {isLoading ? <LoadingState label="Loading audit trail..." /> : null}
      {error ? (
        <ErrorState message={error instanceof Error ? error.message : "Unable to load audit log"} onRetry={() => refetch()} />
      ) : null}

      {data ? (
        data.content.length === 0 ? (
          <EmptyState title="No audit events found" description="Try clearing the active filter." />
        ) : (
          <TableRegion label="Financial audit events">
            <table className="ledger-table">
              <thead>
                <tr>
                  <th>Timestamp (UTC)</th>
                  <th>Actor</th>
                  <th>Action</th>
                  <th>Entity</th>
                  <th>Summary</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((e) => (
                  <tr key={e.id}>
                    <td className="font-mono-code text-xs num whitespace-nowrap text-[var(--text-secondary)]">
                      {e.occurredAt.replace("T", " ").slice(0, 19)}
                    </td>
                    <td className="text-xs font-medium">{e.actorEmail ?? "System"}</td>
                    <td><StatusBadge status={e.action} /></td>
                    <td className="font-mono-code text-xs text-[var(--text-secondary)]">{e.entityType}</td>
                    <td className="text-sm">{e.summary}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableRegion>
        )
      ) : null}
    </div>
  );
}
