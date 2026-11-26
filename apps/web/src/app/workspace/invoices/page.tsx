"use client";

import React, { useMemo, useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { canOperateFinance, formatMoney } from "@ledger/contracts";
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

interface InvoiceItem {
  id: string;
  invoiceNumber: string;
  customerId: string;
  customerName: string;
  status: string;
  displayStatus: string;
  overdue: boolean;
  issueDate: string;
  dueDate: string;
  currencyCode: string;
  totalAmount: string;
  amountPaid: string;
  outstandingBalance: string;
}

export default function InvoicesPage() {
  const { auth } = useAuth();
  const canCreate = canOperateFinance(auth?.activeOrganization?.role);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [search, setSearch] = useState("");

  const { data: invoices = [], isLoading, error, refetch } = useQuery<InvoiceItem[]>({
    queryKey: ["invoices"],
    queryFn: () => apiFetch<InvoiceItem[]>("/api/v1/invoices"),
  });

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    return invoices.filter((inv) => {
      if (statusFilter === "OVERDUE" && !inv.overdue) return false;
      if (statusFilter !== "ALL" && statusFilter !== "OVERDUE" && inv.status !== statusFilter) return false;
      if (q && !inv.invoiceNumber.toLowerCase().includes(q) && !inv.customerName.toLowerCase().includes(q)) return false;
      return true;
    });
  }, [invoices, statusFilter, search]);

  return (
    <div>
      <PageHeader
        title="Invoices"
        description="Draft, issue, and track customer invoices. Issuing an invoice posts balanced Accounts Receivable, Revenue, and Tax Payable journals atomically."
        actions={
          canCreate ? (
            <Link
              href="/workspace/invoices/new"
              className="inline-flex min-h-[42px] items-center rounded-md bg-[var(--brand-forest)] px-4 text-sm font-medium text-[var(--text-on-brand)] hover:bg-[var(--brand-forest-hover)]"
            >
              Create invoice
            </Link>
          ) : null
        }
      />

      <div className="mb-5 flex flex-wrap items-center gap-3">
        <div className="flex flex-wrap gap-1.5">
          {["ALL", "DRAFT", "ISSUED", "PARTIALLY_PAID", "OVERDUE", "PAID", "VOID"].map((st) => (
            <button
              key={st}
              type="button"
              onClick={() => setStatusFilter(st)}
              className={`min-h-[38px] rounded-md px-3 text-xs font-semibold border ${
                statusFilter === st
                  ? "bg-[var(--brand-forest)] text-[var(--text-on-brand)] border-[var(--brand-forest)]"
                  : "bg-[var(--bg-surface)] text-[var(--text-secondary)] border-[var(--border-subtle)]"
              }`}
            >
              {st.replaceAll("_", " ")}
            </button>
          ))}
        </div>
        <input
          type="search"
          aria-label="Search invoices"
          placeholder="Search invoice # or customer..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className={`${inputClass} !w-64`}
        />
      </div>

      {isLoading ? <LoadingState label="Loading invoices..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Unable to load invoices"}
          onRetry={() => refetch()}
        />
      ) : null}

      {!isLoading && !error ? (
        filtered.length === 0 ? (
          <EmptyState
            title="No invoices found"
            description="Create a new invoice or adjust your status filter."
          />
        ) : (
          <TableRegion label="Invoices list">
            <table className="ledger-table">
              <thead>
                <tr>
                  <th>Invoice #</th>
                  <th>Customer</th>
                  <th>Issue Date</th>
                  <th>Due Date</th>
                  <th>Status</th>
                  <th className="!text-right">Total</th>
                  <th className="!text-right">Balance Due</th>
                  <th>PDF</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((inv) => (
                  <tr key={inv.id}>
                    <td className="font-mono-code text-xs font-semibold">
                      <Link
                        href={`/workspace/invoices/${inv.id}`}
                        className="text-[var(--brand-forest)] hover:underline"
                      >
                        {inv.invoiceNumber}
                      </Link>
                    </td>
                    <td className="font-medium">{inv.customerName}</td>
                    <td className="num text-[var(--text-secondary)] whitespace-nowrap">{inv.issueDate}</td>
                    <td className="num text-[var(--text-secondary)] whitespace-nowrap">{inv.dueDate}</td>
                    <td><StatusBadge status={inv.displayStatus} /></td>
                    <td className="text-right num font-medium whitespace-nowrap">
                      {formatMoney(inv.totalAmount, inv.currencyCode)}
                    </td>
                    <td className="text-right num font-semibold whitespace-nowrap">
                      {formatMoney(inv.outstandingBalance, inv.currencyCode)}
                    </td>
                    <td>
                      <a
                        href={`/api/v1/invoices/${inv.id}/pdf`}
                        target="_blank"
                        rel="noreferrer"
                        className="text-xs font-semibold text-[var(--brand-forest)] hover:underline"
                      >
                        A4 PDF
                      </a>
                    </td>
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
