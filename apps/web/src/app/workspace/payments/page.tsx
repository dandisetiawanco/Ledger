"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { canOperateFinance, canWriteAccounting, formatMoney } from "@ledger/contracts";
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

interface PaymentRow {
  id: string;
  paymentNumber: string;
  paymentType: string;
  contactName: string;
  paymentDate: string;
  paymentMethod: string;
  bankAccountName: string;
  currencyCode: string;
  amount: string;
  allocatedAmount: string;
  unallocatedAmount: string;
  referenceNumber: string | null;
  status: string;
  journalEntryId: string | null;
}

interface ContactOpt { id: string; name: string; contactType: string; currencyCode: string }
interface OpenInvoice { id: string; invoiceNumber: string; customerId: string; currencyCode: string; outstandingBalance: string; status: string }
interface OpenBill { id: string; expenseNumber: string; supplierId: string | null; currencyCode: string; outstandingBalance: string; expenseType: string; status: string }

export default function PaymentsPage() {
  const { auth } = useAuth();
  const queryClient = useQueryClient();
  const canOperate = canOperateFinance(auth?.activeOrganization?.role);
  const canAccount = canWriteAccounting(auth?.activeOrganization?.role);

  const [modalOpen, setModalOpen] = useState(false);
  const [paymentType, setPaymentType] = useState<"CUSTOMER_RECEIPT" | "SUPPLIER_PAYMENT">("CUSTOMER_RECEIPT");
  const [contactId, setContactId] = useState("");
  const [targetDocId, setTargetDocId] = useState("");
  const [paymentDate, setPaymentDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState("BANK_TRANSFER");
  const [reference, setReference] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const { data: payments = [], isLoading, error, refetch } = useQuery<PaymentRow[]>({
    queryKey: ["payments"],
    queryFn: () => apiFetch<PaymentRow[]>("/api/v1/payments"),
  });
  const { data: contacts = [] } = useQuery<ContactOpt[]>({
    queryKey: ["contacts"],
    queryFn: () => apiFetch<ContactOpt[]>("/api/v1/contacts"),
  });
  const { data: invoices = [] } = useQuery<OpenInvoice[]>({
    queryKey: ["invoices"],
    queryFn: () => apiFetch<OpenInvoice[]>("/api/v1/invoices"),
  });
  const { data: expenses = [] } = useQuery<OpenBill[]>({
    queryKey: ["expenses"],
    queryFn: () => apiFetch<OpenBill[]>("/api/v1/expenses"),
  });

  const matchingContacts = contacts.filter((c) =>
    paymentType === "CUSTOMER_RECEIPT"
      ? c.contactType === "CUSTOMER" || c.contactType === "BOTH"
      : c.contactType === "SUPPLIER" || c.contactType === "BOTH"
  );

  const openDocs =
    paymentType === "CUSTOMER_RECEIPT"
      ? invoices.filter(
          (i) => i.customerId === contactId && (i.status === "ISSUED" || i.status === "PARTIALLY_PAID") && Number(i.outstandingBalance) > 0
        )
      : expenses.filter(
          (e) => e.supplierId === contactId && e.expenseType === "SUPPLIER_BILL" && e.status === "POSTED" && Number(e.outstandingBalance) > 0
        );

  async function handleRecord(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    setSaving(true);
    try {
      const contact = contacts.find((c) => c.id === contactId);
      const amtNum = Number(amount);
      const allocations = targetDocId
        ? [
            paymentType === "CUSTOMER_RECEIPT"
              ? { invoiceId: targetDocId, amount: amtNum }
              : { expenseId: targetDocId, amount: amtNum },
          ]
        : [];
      await apiFetch("/api/v1/payments", {
        method: "POST",
        idempotencyKey: `ui-pay-${contactId}-${Date.now()}`,
        body: JSON.stringify({
          paymentType,
          contactId,
          paymentDate,
          paymentMethod: method,
          currencyCode: contact?.currencyCode ?? "IDR",
          amount: amtNum,
          referenceNumber: reference || undefined,
          allocations,
        }),
      });
      await queryClient.invalidateQueries({ queryKey: ["payments"] });
      await queryClient.invalidateQueries({ queryKey: ["invoices"] });
      await queryClient.invalidateQueries({ queryKey: ["expenses"] });
      setModalOpen(false);
      setAmount("");
      setTargetDocId("");
      setReference("");
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Failed to record payment");
    } finally {
      setSaving(false);
    }
  }

  async function handleReverse(id: string) {
    await apiFetch(`/api/v1/payments/${id}/reverse`, {
      method: "POST",
      body: JSON.stringify({ reason: "Reversed from payments register" }),
    });
    await queryClient.invalidateQueries({ queryKey: ["payments"] });
    await queryClient.invalidateQueries({ queryKey: ["invoices"] });
    await queryClient.invalidateQueries({ queryKey: ["expenses"] });
  }

  return (
    <div>
      <PageHeader
        title="Payments"
        description="Record customer receipts and supplier bill settlements with idempotency protection, partial allocation, and linked journal entries."
        actions={
          canOperate ? <Button onClick={() => setModalOpen(true)}>Record payment</Button> : null
        }
      />

      {isLoading ? <LoadingState label="Loading payments..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Unable to load payments"}
          onRetry={() => refetch()}
        />
      ) : null}

      {!isLoading && !error ? (
        payments.length === 0 ? (
          <EmptyState
            title="No payments recorded"
            description="Record customer receipts or supplier bill settlements to update bank balances and receivables."
          />
        ) : (
          <TableRegion label="Payments register">
            <table className="ledger-table">
              <thead>
                <tr>
                  <th>Payment #</th>
                  <th>Type</th>
                  <th>Contact</th>
                  <th>Date</th>
                  <th>Account</th>
                  <th>Status</th>
                  <th className="!text-right">Amount</th>
                  <th className="!text-right">Unallocated</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {payments.map((p) => (
                  <tr key={p.id}>
                    <td className="font-mono-code text-xs font-semibold">{p.paymentNumber}</td>
                    <td><StatusBadge status={p.paymentType} /></td>
                    <td>
                      <div className="font-medium">{p.contactName}</div>
                      {p.referenceNumber ? (
                        <div className="text-xs text-[var(--text-secondary)]">Ref: {p.referenceNumber}</div>
                      ) : null}
                    </td>
                    <td className="num text-[var(--text-secondary)] whitespace-nowrap">{p.paymentDate}</td>
                    <td className="text-xs text-[var(--text-secondary)]">{p.bankAccountName}</td>
                    <td><StatusBadge status={p.status} /></td>
                    <td className="text-right num font-semibold whitespace-nowrap">
                      {formatMoney(p.amount, p.currencyCode)}
                    </td>
                    <td className="text-right num whitespace-nowrap">
                      {formatMoney(p.unallocatedAmount, p.currencyCode)}
                    </td>
                    <td className="whitespace-nowrap space-x-3 text-xs">
                      <a
                        href={`/api/v1/payments/${p.id}/receipt.pdf`}
                        target="_blank"
                        rel="noreferrer"
                        className="font-semibold text-[var(--brand-forest)] hover:underline"
                      >
                        Receipt PDF
                      </a>
                      {p.journalEntryId ? (
                        <Link
                          href={`/workspace/journals/${p.journalEntryId}`}
                          className="text-[var(--text-secondary)] hover:underline"
                        >
                          Journal
                        </Link>
                      ) : null}
                      {canAccount && p.status === "POSTED" ? (
                        <button
                          type="button"
                          onClick={() => handleReverse(p.id)}
                          className="text-[var(--status-overdue-text)] hover:underline"
                        >
                          Reverse
                        </button>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableRegion>
        )
      ) : null}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Record payment">
        <form onSubmit={handleRecord} className="space-y-4">
          {formError ? (
            <div role="alert" className="rounded-md bg-[var(--status-overdue-bg)] px-3.5 py-2.5 text-sm text-[var(--status-overdue-text)]">
              {formError}
            </div>
          ) : null}
          <Field id="pm-type" label="Payment direction">
            <select
              id="pm-type"
              value={paymentType}
              onChange={(e) => {
                setPaymentType(e.target.value as "CUSTOMER_RECEIPT" | "SUPPLIER_PAYMENT");
                setContactId("");
                setTargetDocId("");
              }}
              className={inputClass}
            >
              <option value="CUSTOMER_RECEIPT">Customer receipt (inflow)</option>
              <option value="SUPPLIER_PAYMENT">Supplier bill payment (outflow)</option>
            </select>
          </Field>
          <Field id="pm-contact" label="Contact">
            <select id="pm-contact" required value={contactId} onChange={(e) => { setContactId(e.target.value); setTargetDocId(""); }} className={inputClass}>
              <option value="">Select contact...</option>
              {matchingContacts.map((c) => (
                <option key={c.id} value={c.id}>{c.name} ({c.currencyCode})</option>
              ))}
            </select>
          </Field>
          <Field id="pm-doc" label={paymentType === "CUSTOMER_RECEIPT" ? "Allocate to invoice" : "Allocate to supplier bill"}>
            <select
              id="pm-doc"
              required={paymentType === "SUPPLIER_PAYMENT"}
              value={targetDocId}
              onChange={(e) => {
                setTargetDocId(e.target.value);
                const doc = openDocs.find((d) => d.id === e.target.value);
                if (doc) setAmount(doc.outstandingBalance);
              }}
              className={inputClass}
            >
              <option value="">
                {paymentType === "CUSTOMER_RECEIPT" ? "Unallocated customer credit" : "Select open supplier bill..."}
              </option>
              {openDocs.map((d) => (
                <option key={d.id} value={d.id}>
                  {"invoiceNumber" in d ? d.invoiceNumber : d.expenseNumber} (Due {formatMoney(d.outstandingBalance, d.currencyCode)})
                </option>
              ))}
            </select>
          </Field>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field id="pm-date" label="Payment date">
              <input id="pm-date" type="date" required value={paymentDate} onChange={(e) => setPaymentDate(e.target.value)} className={inputClass} />
            </Field>
            <Field id="pm-method" label="Method">
              <select id="pm-method" value={method} onChange={(e) => setMethod(e.target.value)} className={inputClass}>
                <option value="BANK_TRANSFER">Bank Transfer</option>
                <option value="CASH">Cash</option>
                <option value="MANUAL_EXTERNAL">Manual External Record</option>
              </select>
            </Field>
          </div>
          <Field id="pm-amt" label="Amount">
            <input id="pm-amt" type="number" step="0.01" min="0.01" required value={amount} onChange={(e) => setAmount(e.target.value)} className={`${inputClass} num`} />
          </Field>
          <Field id="pm-ref" label="Reference number">
            <input id="pm-ref" value={reference} onChange={(e) => setReference(e.target.value)} placeholder="Wire or transfer reference" className={inputClass} />
          </Field>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" disabled={saving}>{saving ? "Posting..." : "Post payment"}</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
