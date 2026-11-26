"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { canOperateFinance, canWriteAccounting, formatMoney } from "@ledger/contracts";
import { apiFetch, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/providers";
import {
  Button,
  ErrorState,
  Field,
  LoadingState,
  Modal,
  PageHeader,
  StatusBadge,
  TableRegion,
  inputClass,
} from "@/components/ui";

interface InvoiceDetail {
  id: string;
  invoiceNumber: string;
  customerId: string;
  customerName: string;
  status: string;
  displayStatus: string;
  issueDate: string;
  dueDate: string;
  currencyCode: string;
  exchangeRate: string;
  taxInclusive: boolean;
  subtotal: string;
  discountTotal: string;
  taxTotal: string;
  totalAmount: string;
  amountPaid: string;
  creditApplied: string;
  outstandingBalance: string;
  notes: string | null;
  paymentInstructions: string | null;
  journalEntryId: string | null;
  lines: Array<{
    id: string;
    description: string;
    revenueAccountCode: string;
    revenueAccountName: string;
    quantity: string;
    unitPrice: string;
    discountAmount: string;
    taxAmount: string;
    lineTotal: string;
  }>;
  creditNotes: Array<{
    id: string;
    creditNoteNumber: string;
    creditDate: string;
    reason: string;
    totalAmount: string;
    journalEntryId: string | null;
  }>;
}

export default function InvoiceDetailPage() {
  const params = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const { auth } = useAuth();
  const canOperate = canOperateFinance(auth?.activeOrganization?.role);
  const canAccount = canWriteAccounting(auth?.activeOrganization?.role);

  const [payOpen, setPayOpen] = useState(false);
  const [cnOpen, setCnOpen] = useState(false);
  const [payAmount, setPayAmount] = useState("");
  const [payDate, setPayDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [payRef, setPayRef] = useState("");
  const [cnNet, setCnNet] = useState("");
  const [cnTax, setCnTax] = useState("0");
  const [cnReason, setCnReason] = useState("");
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const { data: invoice, isLoading, error, refetch } = useQuery<InvoiceDetail>({
    queryKey: ["invoice", params.id],
    queryFn: () => apiFetch<InvoiceDetail>(`/api/v1/invoices/${params.id}`),
  });

  async function refreshAll() {
    await queryClient.invalidateQueries({ queryKey: ["invoice", params.id] });
    await queryClient.invalidateQueries({ queryKey: ["invoices"] });
    await queryClient.invalidateQueries({ queryKey: ["overview"] });
  }

  async function handleIssue() {
    setBusy(true);
    setActionError(null);
    try {
      await apiFetch(`/api/v1/invoices/${params.id}/issue`, { method: "POST" });
      await refreshAll();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to issue invoice");
    } finally {
      setBusy(false);
    }
  }

  async function handleVoid() {
    setBusy(true);
    setActionError(null);
    try {
      await apiFetch(`/api/v1/invoices/${params.id}/void`, {
        method: "POST",
        body: JSON.stringify({ reason: "Voided from invoice detail" }),
      });
      await refreshAll();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to void invoice");
    } finally {
      setBusy(false);
    }
  }

  async function handleRecordPayment(e: React.FormEvent) {
    e.preventDefault();
    if (!invoice) return;
    setBusy(true);
    setActionError(null);
    try {
      const amt = Number(payAmount);
      await apiFetch("/api/v1/payments", {
        method: "POST",
        idempotencyKey: `inv-${invoice.id}-${Date.now()}`,
        body: JSON.stringify({
          paymentType: "CUSTOMER_RECEIPT",
          contactId: invoice.customerId,
          paymentDate: payDate,
          paymentMethod: "BANK_TRANSFER",
          currencyCode: invoice.currencyCode,
          amount: amt,
          referenceNumber: payRef || undefined,
          allocations: [{ invoiceId: invoice.id, amount: Math.min(amt, Number(invoice.outstandingBalance)) }],
        }),
      });
      setPayOpen(false);
      await refreshAll();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Payment failed");
    } finally {
      setBusy(false);
    }
  }

  async function handleCreditNote(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setActionError(null);
    try {
      await apiFetch(`/api/v1/invoices/${params.id}/credit-notes`, {
        method: "POST",
        body: JSON.stringify({
          creditDate: new Date().toISOString().slice(0, 10),
          reason: cnReason,
          netAmount: Number(cnNet),
          taxAmount: Number(cnTax || 0),
        }),
      });
      setCnOpen(false);
      setCnNet("");
      setCnReason("");
      await refreshAll();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Failed to issue credit note");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <PageHeader
        title={invoice ? `Invoice ${invoice.invoiceNumber}` : "Invoice detail"}
        description={invoice ? `${invoice.customerName} · Due ${invoice.dueDate}` : undefined}
        actions={
          invoice ? (
            <>
              <a
                href={`/api/v1/invoices/${invoice.id}/pdf`}
                target="_blank"
                rel="noreferrer"
                className="inline-flex min-h-[42px] items-center rounded-md border border-[var(--border-strong)] bg-[var(--bg-surface)] px-3.5 text-sm font-medium hover:bg-[var(--bg-surface-subtle)]"
              >
                Download A4 PDF
              </a>
              <Button variant="secondary" onClick={() => window.print()}>
                Print
              </Button>
              {canOperate && invoice.status === "DRAFT" ? (
                <Button onClick={handleIssue} disabled={busy}>
                  Issue invoice
                </Button>
              ) : null}
              {canOperate && (invoice.status === "ISSUED" || invoice.status === "PARTIALLY_PAID") ? (
                <Button
                  onClick={() => {
                    setPayAmount(invoice.outstandingBalance);
                    setPayOpen(true);
                  }}
                >
                  Record payment
                </Button>
              ) : null}
              {canAccount && (invoice.status === "ISSUED" || invoice.status === "PARTIALLY_PAID") ? (
                <Button variant="secondary" onClick={() => setCnOpen(true)}>
                  Issue credit note
                </Button>
              ) : null}
              {canAccount && invoice.status === "ISSUED" && Number(invoice.amountPaid) === 0 ? (
                <Button variant="danger" onClick={handleVoid} disabled={busy}>
                  Void
                </Button>
              ) : null}
            </>
          ) : null
        }
      />

      {actionError ? <div className="mb-4"><ErrorState message={actionError} /></div> : null}
      {isLoading ? <LoadingState label="Loading invoice..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Invoice not found"}
          onRetry={() => refetch()}
        />
      ) : null}

      {invoice ? (
        <div className="space-y-6">
          {/* Printable Invoice Document Sheet */}
          <article className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6 sm:p-8 space-y-6">
            <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4 border-b border-[var(--border-subtle)] pb-5">
              <div>
                <p className="font-editorial text-2xl font-semibold text-[var(--brand-forest)]">
                  {auth?.activeOrganization?.name}
                </p>
                <p className="mt-1 text-sm text-[var(--text-secondary)]">
                  Bill To: <strong className="text-[var(--text-primary)]">{invoice.customerName}</strong>
                </p>
              </div>
              <div className="sm:text-right space-y-1 text-sm">
                <StatusBadge status={invoice.displayStatus} />
                <p className="font-mono-code font-semibold mt-1">{invoice.invoiceNumber}</p>
                <p className="text-xs text-[var(--text-secondary)] num">
                  Issued {invoice.issueDate} · Due {invoice.dueDate}
                </p>
                {invoice.journalEntryId ? (
                  <p className="text-xs">
                    <Link
                      href={`/workspace/journals/${invoice.journalEntryId}`}
                      className="font-semibold text-[var(--brand-forest)] hover:underline"
                    >
                      Inspect posted accounting journal
                    </Link>
                  </p>
                ) : null}
              </div>
            </div>

            <TableRegion label="Invoice line items">
              <table className="ledger-table">
                <thead>
                  <tr>
                    <th>Description</th>
                    <th>Account</th>
                    <th className="!text-right">Qty</th>
                    <th className="!text-right">Unit Price</th>
                    <th className="!text-right">Discount</th>
                    <th className="!text-right">Tax</th>
                    <th className="!text-right">Line Total</th>
                  </tr>
                </thead>
                <tbody>
                  {invoice.lines.map((l) => (
                    <tr key={l.id}>
                      <td className="font-medium">{l.description}</td>
                      <td className="text-xs text-[var(--text-secondary)]">
                        <span className="font-mono-code">{l.revenueAccountCode}</span> {l.revenueAccountName}
                      </td>
                      <td className="text-right num">{l.quantity}</td>
                      <td className="text-right num">{formatMoney(l.unitPrice, invoice.currencyCode)}</td>
                      <td className="text-right num">{formatMoney(l.discountAmount, invoice.currencyCode)}</td>
                      <td className="text-right num">{formatMoney(l.taxAmount, invoice.currencyCode)}</td>
                      <td className="text-right num font-semibold">{formatMoney(l.lineTotal, invoice.currencyCode)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </TableRegion>

            <div className="grid grid-cols-1 md:grid-cols-12 gap-6 pt-2">
              <div className="md:col-span-7 space-y-3 text-sm text-[var(--text-secondary)]">
                {invoice.paymentInstructions ? (
                  <div>
                    <p className="text-xs font-semibold text-[var(--text-primary)]">Payment instructions</p>
                    <p className="mt-1">{invoice.paymentInstructions}</p>
                  </div>
                ) : null}
                {invoice.notes ? (
                  <div>
                    <p className="text-xs font-semibold text-[var(--text-primary)]">Notes</p>
                    <p className="mt-1">{invoice.notes}</p>
                  </div>
                ) : null}
              </div>
              <div className="md:col-span-5 space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-[var(--text-secondary)]">Subtotal</span>
                  <span className="num">{formatMoney(invoice.subtotal, invoice.currencyCode)}</span>
                </div>
                {Number(invoice.discountTotal) > 0 ? (
                  <div className="flex justify-between">
                    <span className="text-[var(--text-secondary)]">Discount</span>
                    <span className="num">-{formatMoney(invoice.discountTotal, invoice.currencyCode)}</span>
                  </div>
                ) : null}
                <div className="flex justify-between">
                  <span className="text-[var(--text-secondary)]">Tax</span>
                  <span className="num">{formatMoney(invoice.taxTotal, invoice.currencyCode)}</span>
                </div>
                <div className="flex justify-between font-semibold border-t border-[var(--border-subtle)] pt-2">
                  <span>Total ({invoice.currencyCode})</span>
                  <span className="num">{formatMoney(invoice.totalAmount, invoice.currencyCode)}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-[var(--text-secondary)]">Amount Paid</span>
                  <span className="num">{formatMoney(invoice.amountPaid, invoice.currencyCode)}</span>
                </div>
                {Number(invoice.creditApplied) > 0 ? (
                  <div className="flex justify-between">
                    <span className="text-[var(--text-secondary)]">Credit Notes Applied</span>
                    <span className="num">{formatMoney(invoice.creditApplied, invoice.currencyCode)}</span>
                  </div>
                ) : null}
                <div className="flex justify-between text-base font-semibold text-[var(--brand-forest)] border-t border-[var(--border-subtle)] pt-2">
                  <span>Balance Due</span>
                  <span className="num">{formatMoney(invoice.outstandingBalance, invoice.currencyCode)}</span>
                </div>
              </div>
            </div>
          </article>

          {invoice.creditNotes.length > 0 ? (
            <section className="space-y-2">
              <h2 className="text-sm font-semibold">Credit notes applied</h2>
              <TableRegion label="Invoice credit notes">
                <table className="ledger-table">
                  <thead>
                    <tr>
                      <th>Credit Note #</th>
                      <th>Date</th>
                      <th>Reason</th>
                      <th className="!text-right">Credit Amount</th>
                      <th>PDF</th>
                    </tr>
                  </thead>
                  <tbody>
                    {invoice.creditNotes.map((cn) => (
                      <tr key={cn.id}>
                        <td className="font-mono-code text-xs font-semibold">{cn.creditNoteNumber}</td>
                        <td className="num">{cn.creditDate}</td>
                        <td>{cn.reason}</td>
                        <td className="text-right num font-semibold">{formatMoney(cn.totalAmount, invoice.currencyCode)}</td>
                        <td>
                          <a
                            href={`/api/v1/invoices/credit-notes/${cn.id}/pdf`}
                            target="_blank"
                            rel="noreferrer"
                            className="text-xs font-semibold text-[var(--brand-forest)] hover:underline"
                          >
                            Download PDF
                          </a>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </TableRegion>
            </section>
          ) : null}
        </div>
      ) : null}

      <Modal open={payOpen} onClose={() => setPayOpen(false)} title="Record customer payment">
        <form onSubmit={handleRecordPayment} className="space-y-4">
          <Field id="pay-amt" label={`Amount received (${invoice?.currencyCode})`}>
            <input id="pay-amt" type="number" step="0.01" min="0.01" required value={payAmount} onChange={(e) => setPayAmount(e.target.value)} className={`${inputClass} num`} />
          </Field>
          <Field id="pay-dt" label="Payment date">
            <input id="pay-dt" type="date" required value={payDate} onChange={(e) => setPayDate(e.target.value)} className={inputClass} />
          </Field>
          <Field id="pay-ref" label="Bank transfer reference">
            <input id="pay-ref" value={payRef} onChange={(e) => setPayRef(e.target.value)} placeholder="e.g. TRF-2026-1126" className={inputClass} />
          </Field>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={() => setPayOpen(false)}>Cancel</Button>
            <Button type="submit" disabled={busy}>{busy ? "Posting..." : "Post customer receipt"}</Button>
          </div>
        </form>
      </Modal>

      <Modal open={cnOpen} onClose={() => setCnOpen(false)} title="Issue credit note">
        <form onSubmit={handleCreditNote} className="space-y-4">
          <Field id="cn-net" label={`Net credit amount (${invoice?.currencyCode})`}>
            <input id="cn-net" type="number" step="0.01" min="0.01" required value={cnNet} onChange={(e) => setCnNet(e.target.value)} className={`${inputClass} num`} />
          </Field>
          <Field id="cn-tax" label={`Tax credit amount (${invoice?.currencyCode})`}>
            <input id="cn-tax" type="number" step="0.01" min="0" value={cnTax} onChange={(e) => setCnTax(e.target.value)} className={`${inputClass} num`} />
          </Field>
          <Field id="cn-reason" label="Reason">
            <input id="cn-reason" required value={cnReason} onChange={(e) => setCnReason(e.target.value)} placeholder="Describe the billing adjustment" className={inputClass} />
          </Field>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={() => setCnOpen(false)}>Cancel</Button>
            <Button type="submit" disabled={busy}>{busy ? "Posting..." : "Post credit note"}</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
