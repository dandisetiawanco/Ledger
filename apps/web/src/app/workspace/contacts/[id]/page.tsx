"use client";

import React from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { formatMoney } from "@ledger/contracts";
import { apiFetch } from "@/lib/api";
import { useAuth } from "@/lib/providers";
import { ErrorState, LoadingState, PageHeader, StatusBadge, TableRegion } from "@/components/ui";

interface ContactDetail {
  contact: {
    id: string;
    name: string;
    contactType: string;
    contactPerson: string | null;
    email: string | null;
    phone: string | null;
    billingAddress: string | null;
    currencyCode: string;
    totalInvoiced: string;
    totalReceived: string;
    receivableOutstanding: string;
    totalBilled: string;
    totalPaid: string;
    payableOutstanding: string;
  };
  invoices: Array<{ id: string; reference: string; date: string; dueDate: string | null; status: string; currencyCode: string; totalAmount: string; balanceAmount: string }>;
  expenses: Array<{ id: string; reference: string; date: string; dueDate: string | null; status: string; currencyCode: string; totalAmount: string; balanceAmount: string }>;
  payments: Array<{ id: string; reference: string; date: string; status: string; currencyCode: string; totalAmount: string }>;
}

export default function ContactDetailPage() {
  const params = useParams<{ id: string }>();
  const { auth } = useAuth();
  const ccy = auth?.activeOrganization?.baseCurrency ?? "IDR";

  const { data, isLoading, error, refetch } = useQuery<ContactDetail>({
    queryKey: ["contact", params.id],
    queryFn: () => apiFetch<ContactDetail>(`/api/v1/contacts/${params.id}`),
  });

  return (
    <div className="space-y-6">
      <PageHeader
        title={data ? data.contact.name : "Contact detail"}
        description={data ? `${data.contact.contactType} · ${data.contact.email || "No email"}` : undefined}
      />
      {isLoading ? <LoadingState label="Loading contact financial summary..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Contact not found"}
          onRetry={() => refetch()}
        />
      ) : null}

      {data ? (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-5 text-sm">
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Total invoiced</p>
              <p className="mt-1 font-semibold num">{formatMoney(data.contact.totalInvoiced, ccy)}</p>
            </div>
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Receivable outstanding</p>
              <p className="mt-1 font-semibold num">{formatMoney(data.contact.receivableOutstanding, ccy)}</p>
            </div>
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Total supplier bills</p>
              <p className="mt-1 font-semibold num">{formatMoney(data.contact.totalBilled, ccy)}</p>
            </div>
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Payable outstanding</p>
              <p className="mt-1 font-semibold num">{formatMoney(data.contact.payableOutstanding, ccy)}</p>
            </div>
          </div>

          {data.invoices.length > 0 ? (
            <section className="space-y-2">
              <h2 className="text-base font-semibold">Invoices</h2>
              <TableRegion label="Contact invoices">
                <table className="ledger-table">
                  <thead>
                    <tr>
                      <th>Invoice #</th>
                      <th>Date</th>
                      <th>Due Date</th>
                      <th>Status</th>
                      <th className="!text-right">Total</th>
                      <th className="!text-right">Balance</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.invoices.map((inv) => (
                      <tr key={inv.id}>
                        <td className="font-mono-code text-xs font-semibold">
                          <Link href={`/workspace/invoices/${inv.id}`} className="text-[var(--brand-forest)] hover:underline">
                            {inv.reference}
                          </Link>
                        </td>
                        <td className="num">{inv.date}</td>
                        <td className="num">{inv.dueDate || ""}</td>
                        <td><StatusBadge status={inv.status} /></td>
                        <td className="text-right num">{formatMoney(inv.totalAmount, inv.currencyCode)}</td>
                        <td className="text-right num font-semibold">{formatMoney(inv.balanceAmount, inv.currencyCode)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </TableRegion>
            </section>
          ) : null}

          {data.expenses.length > 0 ? (
            <section className="space-y-2">
              <h2 className="text-base font-semibold">Expenses &amp; Supplier Bills</h2>
              <TableRegion label="Contact bills">
                <table className="ledger-table">
                  <thead>
                    <tr>
                      <th>Reference</th>
                      <th>Date</th>
                      <th>Status</th>
                      <th className="!text-right">Total</th>
                      <th className="!text-right">Payable Due</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.expenses.map((ex) => (
                      <tr key={ex.id}>
                        <td className="font-mono-code text-xs font-semibold">
                          <Link href={`/workspace/expenses/${ex.id}`} className="text-[var(--brand-forest)] hover:underline">
                            {ex.reference}
                          </Link>
                        </td>
                        <td className="num">{ex.date}</td>
                        <td><StatusBadge status={ex.status} /></td>
                        <td className="text-right num">{formatMoney(ex.totalAmount, ex.currencyCode)}</td>
                        <td className="text-right num font-semibold">{formatMoney(ex.balanceAmount, ex.currencyCode)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </TableRegion>
            </section>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
