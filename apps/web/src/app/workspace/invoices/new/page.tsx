"use client";

import React, { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { calculateInvoicePreview, formatMoney } from "@ledger/contracts";
import { apiFetch, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/providers";
import { Button, Field, PageHeader, inputClass } from "@/components/ui";

interface ContactOpt {
  id: string;
  name: string;
  currencyCode: string;
  paymentTermsDays: number;
}

interface CatalogOpt {
  id: string;
  name: string;
  description: string | null;
  unitPrice: string;
  currencyCode: string;
  revenueAccountId: string;
  defaultTaxRate: string;
}

interface AccountOpt {
  id: string;
  code: string;
  name: string;
  accountType: string;
  allowPosting: boolean;
}

interface LineDraft {
  catalogItemId: string;
  description: string;
  revenueAccountId: string;
  quantity: string;
  unitPrice: string;
  discountRate: string;
  taxRate: string;
}

export default function NewInvoicePage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const { auth } = useAuth();
  const orgCcy = auth?.activeOrganization?.baseCurrency ?? "IDR";

  const { data: customers = [] } = useQuery<ContactOpt[]>({
    queryKey: ["contacts", "CUSTOMER"],
    queryFn: () => apiFetch<ContactOpt[]>("/api/v1/contacts?type=CUSTOMER"),
  });
  const { data: catalog = [] } = useQuery<CatalogOpt[]>({
    queryKey: ["catalog"],
    queryFn: () => apiFetch<CatalogOpt[]>("/api/v1/catalog"),
  });
  const { data: accounts = [] } = useQuery<AccountOpt[]>({
    queryKey: ["accounts"],
    queryFn: () => apiFetch<AccountOpt[]>("/api/v1/accounts"),
  });

  const revenueAccounts = useMemo(
    () => accounts.filter((a) => a.accountType === "REVENUE" && a.allowPosting),
    [accounts]
  );

  const [customerId, setCustomerId] = useState("");
  const [issueDate, setIssueDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [paymentTermsDays, setPaymentTermsDays] = useState("30");
  const [currencyCode, setCurrencyCode] = useState(orgCcy);
  const [exchangeRate, setExchangeRate] = useState("");
  const [taxInclusive, setTaxInclusive] = useState(false);
  const [notes, setNotes] = useState("");
  const [lines, setLines] = useState<LineDraft[]>([
    { catalogItemId: "", description: "", revenueAccountId: "", quantity: "1", unitPrice: "0", discountRate: "0", taxRate: "11" },
  ]);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const preview = useMemo(
    () => calculateInvoicePreview(lines, currencyCode, taxInclusive),
    [lines, currencyCode, taxInclusive]
  );

  function selectCustomer(id: string) {
    setCustomerId(id);
    const found = customers.find((c) => c.id === id);
    if (found) {
      setCurrencyCode(found.currencyCode);
      setPaymentTermsDays(String(found.paymentTermsDays));
    }
  }

  function applyCatalogItem(idx: number, catalogId: string) {
    const item = catalog.find((c) => c.id === catalogId);
    setLines((prev) =>
      prev.map((l, i) =>
        i !== idx
          ? l
          : item
          ? {
              ...l,
              catalogItemId: item.id,
              description: item.name,
              revenueAccountId: item.revenueAccountId,
              unitPrice: item.unitPrice,
              taxRate: item.defaultTaxRate,
            }
          : { ...l, catalogItemId: "" }
      )
    );
  }

  function updateLine(idx: number, patch: Partial<LineDraft>) {
    setLines((prev) => prev.map((l, i) => (i === idx ? { ...l, ...patch } : l)));
  }

  async function saveInvoice(issueImmediately: boolean) {
    setError(null);
    setSaving(true);
    try {
      const payload = {
        customerId,
        issueDate,
        paymentTermsDays: Number(paymentTermsDays),
        currencyCode,
        exchangeRate: currencyCode !== orgCcy && exchangeRate ? Number(exchangeRate) : undefined,
        taxInclusive,
        notes: notes || undefined,
        issueImmediately,
        lines: lines.map((l) => ({
          catalogItemId: l.catalogItemId || undefined,
          description: l.description,
          revenueAccountId: l.revenueAccountId || undefined,
          quantity: Number(l.quantity),
          unitPrice: Number(l.unitPrice),
          discountRate: Number(l.discountRate || 0),
          taxRate: Number(l.taxRate || 0),
        })),
      };
      const created = await apiFetch<{ id: string }>("/api/v1/invoices", {
        method: "POST",
        body: JSON.stringify(payload),
      });
      await queryClient.invalidateQueries({ queryKey: ["invoices"] });
      await queryClient.invalidateQueries({ queryKey: ["overview"] });
      router.push(`/workspace/invoices/${created.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to save invoice");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="max-w-5xl">
      <PageHeader
        title="Create invoice"
        description="Line previews use Decimal.js; the backend independently computes authoritative financial totals and double-entry postings upon issuance."
      />

      {error ? (
        <div
          role="alert"
          className="mb-5 rounded-md border border-[var(--status-overdue-text)] bg-[var(--status-overdue-bg)] px-4 py-3 text-sm text-[var(--status-overdue-text)]"
        >
          {error}
        </div>
      ) : null}

      <div className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6 space-y-6">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <Field id="inv-cust" label="Customer">
            <select
              id="inv-cust"
              required
              value={customerId}
              onChange={(e) => selectCustomer(e.target.value)}
              className={inputClass}
            >
              <option value="">Select customer...</option>
              {customers.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({c.currencyCode})
                </option>
              ))}
            </select>
          </Field>
          <Field id="inv-date" label="Issue date">
            <input id="inv-date" type="date" value={issueDate} onChange={(e) => setIssueDate(e.target.value)} className={inputClass} />
          </Field>
          <Field id="inv-terms" label="Payment terms (days)">
            <input id="inv-terms" type="number" min="0" value={paymentTermsDays} onChange={(e) => setPaymentTermsDays(e.target.value)} className={`${inputClass} num`} />
          </Field>
          <Field id="inv-ccy" label="Currency">
            <select id="inv-ccy" value={currencyCode} onChange={(e) => setCurrencyCode(e.target.value)} className={inputClass}>
              <option value="IDR">IDR</option>
              <option value="USD">USD</option>
              <option value="SGD">SGD</option>
              <option value="EUR">EUR</option>
            </select>
          </Field>
        </div>

        <div className="flex flex-wrap items-center gap-6">
          <label className="flex items-center gap-2 text-sm text-[var(--text-primary)]">
            <input type="checkbox" checked={taxInclusive} onChange={(e) => setTaxInclusive(e.target.checked)} />
            Prices include tax (tax-inclusive calculation)
          </label>
          {currencyCode !== orgCcy ? (
            <div className="w-60">
              <Field id="inv-fx" label={`Exchange rate (1 ${currencyCode} to ${orgCcy})`} hint="Leave empty to use configured org rate">
                <input id="inv-fx" type="number" step="0.0001" value={exchangeRate} onChange={(e) => setExchangeRate(e.target.value)} placeholder="Configured rate" className={`${inputClass} num`} />
              </Field>
            </div>
          ) : null}
        </div>

        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold">Line items</h2>
            <Button
              variant="secondary"
              size="sm"
              onClick={() =>
                setLines((prev) => [
                  ...prev,
                  { catalogItemId: "", description: "", revenueAccountId: "", quantity: "1", unitPrice: "0", discountRate: "0", taxRate: "11" },
                ])
              }
            >
              Add line item
            </Button>
          </div>

          <div className="space-y-3">
            {lines.map((line, idx) => (
              <div
                key={idx}
                className="rounded-md border border-[var(--border-subtle)] bg-[var(--bg-canvas)] p-4 space-y-3"
              >
                <div className="grid grid-cols-1 md:grid-cols-12 gap-3">
                  <div className="md:col-span-4">
                    <Field id={`cat-${idx}`} label="Service catalog (optional)">
                      <select
                        id={`cat-${idx}`}
                        value={line.catalogItemId}
                        onChange={(e) => applyCatalogItem(idx, e.target.value)}
                        className={inputClass}
                      >
                        <option value="">Custom item</option>
                        {catalog.map((c) => (
                          <option key={c.id} value={c.id}>{c.name}</option>
                        ))}
                      </select>
                    </Field>
                  </div>
                  <div className="md:col-span-5">
                    <Field id={`desc-${idx}`} label="Description">
                      <input
                        id={`desc-${idx}`}
                        required
                        value={line.description}
                        onChange={(e) => updateLine(idx, { description: e.target.value })}
                        placeholder="Service or deliverable description"
                        className={inputClass}
                      />
                    </Field>
                  </div>
                  <div className="md:col-span-3">
                    <Field id={`rev-${idx}`} label="Revenue account">
                      <select
                        id={`rev-${idx}`}
                        value={line.revenueAccountId}
                        onChange={(e) => updateLine(idx, { revenueAccountId: e.target.value })}
                        className={inputClass}
                      >
                        <option value="">Default Service Revenue</option>
                        {revenueAccounts.map((a) => (
                          <option key={a.id} value={a.id}>{a.code} · {a.name}</option>
                        ))}
                      </select>
                    </Field>
                  </div>
                </div>

                <div className="grid grid-cols-2 sm:grid-cols-5 gap-3 items-end">
                  <Field id={`qty-${idx}`} label="Qty">
                    <input id={`qty-${idx}`} type="number" step="0.01" min="0.01" value={line.quantity} onChange={(e) => updateLine(idx, { quantity: e.target.value })} className={`${inputClass} text-right num`} />
                  </Field>
                  <Field id={`price-${idx}`} label={`Unit Price (${currencyCode})`}>
                    <input id={`price-${idx}`} type="number" step="0.01" min="0" value={line.unitPrice} onChange={(e) => updateLine(idx, { unitPrice: e.target.value })} className={`${inputClass} text-right num`} />
                  </Field>
                  <Field id={`disc-${idx}`} label="Discount %">
                    <input id={`disc-${idx}`} type="number" step="0.01" min="0" max="100" value={line.discountRate} onChange={(e) => updateLine(idx, { discountRate: e.target.value })} className={`${inputClass} text-right num`} />
                  </Field>
                  <Field id={`tax-${idx}`} label="Tax %">
                    <input id={`tax-${idx}`} type="number" step="0.01" min="0" value={line.taxRate} onChange={(e) => updateLine(idx, { taxRate: e.target.value })} className={`${inputClass} text-right num`} />
                  </Field>
                  <div className="flex items-center justify-between sm:justify-end gap-3 pb-2">
                    <span className="text-sm font-semibold num">
                      {formatMoney(preview.lines[idx]?.lineTotal ?? "0", currencyCode)}
                    </span>
                    {lines.length > 1 ? (
                      <button
                        type="button"
                        onClick={() => setLines((prev) => prev.filter((_, i) => i !== idx))}
                        className="text-xs text-[var(--status-overdue-text)] hover:underline"
                      >
                        Remove
                      </button>
                    ) : null}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-12 gap-6 pt-3 border-t border-[var(--border-subtle)]">
          <div className="md:col-span-7">
            <Field id="inv-notes" label="Notes">
              <textarea id="inv-notes" rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Project reference or billing notes" className={inputClass} />
            </Field>
          </div>
          <div className="md:col-span-5 space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-[var(--text-secondary)]">Subtotal</span>
              <span className="num font-medium">{formatMoney(preview.subtotal, currencyCode)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-[var(--text-secondary)]">Discount</span>
              <span className="num font-medium">-{formatMoney(preview.discountTotal, currencyCode)}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-[var(--text-secondary)]">
                Tax {taxInclusive ? "(Included)" : ""}
              </span>
              <span className="num font-medium">{formatMoney(preview.taxTotal, currencyCode)}</span>
            </div>
            <div className="flex justify-between border-t border-[var(--border-subtle)] pt-2 text-base font-semibold text-[var(--brand-forest)]">
              <span>Estimated Total</span>
              <span className="num">{formatMoney(preview.grandTotal, currencyCode)}</span>
            </div>
          </div>
        </div>

        <div className="flex flex-wrap justify-end gap-3">
          <Button variant="secondary" onClick={() => router.push("/workspace/invoices")}>
            Cancel
          </Button>
          <Button variant="secondary" disabled={saving || !customerId} onClick={() => saveInvoice(false)}>
            Save draft
          </Button>
          <Button disabled={saving || !customerId} onClick={() => saveInvoice(true)}>
            {saving ? "Issuing..." : "Save and issue invoice"}
          </Button>
        </div>
      </div>
    </div>
  );
}
