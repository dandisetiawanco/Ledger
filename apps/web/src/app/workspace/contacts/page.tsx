"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { canOperateFinance, formatMoney } from "@ledger/contracts";
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

interface ContactSummary {
  id: string;
  contactType: "CUSTOMER" | "SUPPLIER" | "BOTH";
  name: string;
  contactPerson: string | null;
  email: string | null;
  currencyCode: string;
  paymentTermsDays: number;
  receivableOutstanding: string;
  payableOutstanding: string;
}

export default function ContactsPage() {
  const { auth } = useAuth();
  const queryClient = useQueryClient();
  const ccy = auth?.activeOrganization?.baseCurrency ?? "IDR";
  const canOperate = canOperateFinance(auth?.activeOrganization?.role);

  const [typeFilter, setTypeFilter] = useState("");
  const [modalOpen, setModalOpen] = useState(false);
  const [contactType, setContactType] = useState<"CUSTOMER" | "SUPPLIER" | "BOTH">("CUSTOMER");
  const [name, setName] = useState("");
  const [contactPerson, setContactPerson] = useState("");
  const [email, setEmail] = useState("");
  const [currencyCode, setCurrencyCode] = useState(ccy);
  const [terms, setTerms] = useState("30");
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const { data: contacts = [], isLoading, error, refetch } = useQuery<ContactSummary[]>({
    queryKey: ["contacts", typeFilter],
    queryFn: () =>
      apiFetch<ContactSummary[]>(typeFilter ? `/api/v1/contacts?type=${typeFilter}` : "/api/v1/contacts"),
  });

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    setSaving(true);
    try {
      await apiFetch("/api/v1/contacts", {
        method: "POST",
        body: JSON.stringify({
          contactType,
          name,
          contactPerson: contactPerson || undefined,
          email: email || undefined,
          currencyCode,
          paymentTermsDays: Number(terms || 30),
        }),
      });
      await queryClient.invalidateQueries({ queryKey: ["contacts"] });
      setModalOpen(false);
      setName("");
      setContactPerson("");
      setEmail("");
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Could not create contact");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Customers & Suppliers"
        description="Unified business directory with receivables and payables derived directly from posted invoices, bills, and payments."
        actions={canOperate ? <Button onClick={() => setModalOpen(true)}>Add contact</Button> : null}
      />

      <div className="mb-5 flex flex-wrap gap-1.5">
        {[
          { key: "", label: "All contacts" },
          { key: "CUSTOMER", label: "Customers" },
          { key: "SUPPLIER", label: "Suppliers" },
        ].map((f) => (
          <button
            key={f.key || "ALL"}
            type="button"
            onClick={() => setTypeFilter(f.key)}
            className={`min-h-[38px] rounded-md px-3 text-xs font-semibold border ${
              typeFilter === f.key
                ? "bg-[var(--brand-forest)] text-[var(--text-on-brand)] border-[var(--brand-forest)]"
                : "bg-[var(--bg-surface)] text-[var(--text-secondary)] border-[var(--border-subtle)]"
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {isLoading ? <LoadingState label="Loading contacts..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Unable to load contacts"}
          onRetry={() => refetch()}
        />
      ) : null}

      {!isLoading && !error ? (
        contacts.length === 0 ? (
          <EmptyState title="No contacts yet" description="Add customers and suppliers to issue invoices and track supplier bills." />
        ) : (
          <TableRegion label="Customers and suppliers table">
            <table className="ledger-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Type</th>
                  <th>Currency</th>
                  <th>Terms</th>
                  <th className="!text-right">AR Balance ({ccy})</th>
                  <th className="!text-right">AP Balance ({ccy})</th>
                </tr>
              </thead>
              <tbody>
                {contacts.map((c) => (
                  <tr key={c.id}>
                    <td>
                      <Link href={`/workspace/contacts/${c.id}`} className="font-semibold text-[var(--brand-forest)] hover:underline">
                        {c.name}
                      </Link>
                      {c.email ? <div className="text-xs text-[var(--text-secondary)]">{c.email}</div> : null}
                    </td>
                    <td><StatusBadge status={c.contactType} /></td>
                    <td className="font-mono-code text-xs">{c.currencyCode}</td>
                    <td className="text-xs text-[var(--text-secondary)]">Net {c.paymentTermsDays}d</td>
                    <td className="text-right num font-semibold">{formatMoney(c.receivableOutstanding, ccy)}</td>
                    <td className="text-right num font-semibold">{formatMoney(c.payableOutstanding, ccy)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableRegion>
        )
      ) : null}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Add customer or supplier">
        <form onSubmit={handleCreate} className="space-y-4">
          {formError ? (
            <div role="alert" className="rounded-md bg-[var(--status-overdue-bg)] px-3.5 py-2.5 text-sm text-[var(--status-overdue-text)]">
              {formError}
            </div>
          ) : null}
          <Field id="ct-type" label="Contact type">
            <select id="ct-type" value={contactType} onChange={(e) => setContactType(e.target.value as "CUSTOMER" | "SUPPLIER" | "BOTH")} className={inputClass}>
              <option value="CUSTOMER">Customer</option>
              <option value="SUPPLIER">Supplier</option>
              <option value="BOTH">Both customer and supplier</option>
            </select>
          </Field>
          <Field id="ct-name" label="Business or individual name">
            <input id="ct-name" required value={name} onChange={(e) => setName(e.target.value)} placeholder="Organization or contact name" className={inputClass} />
          </Field>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field id="ct-person" label="Contact person">
              <input id="ct-person" value={contactPerson} onChange={(e) => setContactPerson(e.target.value)} placeholder="Primary contact" className={inputClass} />
            </Field>
            <Field id="ct-email" label="Email">
              <input id="ct-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="email@example.com" className={inputClass} />
            </Field>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field id="ct-ccy" label="Default currency">
              <select id="ct-ccy" value={currencyCode} onChange={(e) => setCurrencyCode(e.target.value)} className={inputClass}>
                <option value="IDR">IDR</option>
                <option value="USD">USD</option>
                <option value="SGD">SGD</option>
                <option value="EUR">EUR</option>
              </select>
            </Field>
            <Field id="ct-terms" label="Payment terms (days)">
              <input id="ct-terms" type="number" min="0" value={terms} onChange={(e) => setTerms(e.target.value)} className={`${inputClass} num`} />
            </Field>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" disabled={saving}>{saving ? "Saving..." : "Save contact"}</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
