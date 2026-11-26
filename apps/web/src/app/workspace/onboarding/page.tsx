"use client";

import React, { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { apiFetch, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/providers";
import { Button, Field, LoadingState, PageHeader, inputClass } from "@/components/ui";

interface OrgDetail {
  id: string;
  name: string;
  businessType: string | null;
  baseCurrency: string;
  timeZone: string;
  locale: string;
  fiscalYearStartMonth: number;
  contactEmail: string | null;
  contactPhone: string | null;
  address: string | null;
  taxId: string | null;
  settings: {
    invoicePrefix: string;
    defaultTaxName: string;
    defaultTaxRate: string;
    defaultPaymentTermsDays: number;
    paymentInstructions: string | null;
  };
}

export default function OnboardingPage() {
  const router = useRouter();
  const { refreshAuth } = useAuth();
  const { data: org, isLoading } = useQuery<OrgDetail>({
    queryKey: ["organization", "current"],
    queryFn: () => apiFetch<OrgDetail>("/api/v1/organizations/current"),
  });

  const [name, setName] = useState("");
  const [businessType, setBusinessType] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [contactPhone, setContactPhone] = useState("");
  const [address, setAddress] = useState("");
  const [invoicePrefix, setInvoicePrefix] = useState("INV");
  const [taxName, setTaxName] = useState("VAT");
  const [taxRate, setTaxRate] = useState("11");
  const [paymentTerms, setPaymentTerms] = useState("30");
  const [paymentInstructions, setPaymentInstructions] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!org) return;
    setName(org.name || "");
    setBusinessType(org.businessType || "");
    setContactEmail(org.contactEmail || "");
    setContactPhone(org.contactPhone || "");
    setAddress(org.address || "");
    setInvoicePrefix(org.settings.invoicePrefix || "INV");
    setTaxName(org.settings.defaultTaxName || "VAT");
    setTaxRate(org.settings.defaultTaxRate || "0");
    setPaymentTerms(String(org.settings.defaultPaymentTermsDays ?? 30));
    setPaymentInstructions(org.settings.paymentInstructions || "");
  }, [org]);

  async function handleSave(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await apiFetch("/api/v1/organizations/current", {
        method: "PATCH",
        body: JSON.stringify({
          name,
          businessType,
          contactEmail,
          contactPhone,
          address,
          invoicePrefix,
          defaultTaxName: taxName,
          defaultTaxRate: Number(taxRate),
          defaultPaymentTermsDays: Number(paymentTerms),
          paymentInstructions,
          onboardingCompleted: true,
        }),
      });
      await refreshAuth();
      router.push("/workspace");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not save onboarding settings");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="max-w-2xl">
      <PageHeader
        title="Organization setup"
        description="Configure your invoice numbering, configurable tax defaults, and business details. Your starter Chart of Accounts and monthly accounting periods are already active."
      />
      {isLoading ? (
        <LoadingState label="Loading organization configuration..." />
      ) : (
        <form
          onSubmit={handleSave}
          className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6 space-y-5"
        >
          {error ? (
            <div
              role="alert"
              className="rounded-md border border-[var(--status-overdue-text)] bg-[var(--status-overdue-bg)] px-4 py-3 text-sm text-[var(--status-overdue-text)]"
            >
              {error}
            </div>
          ) : null}

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field id="ob-name" label="Organization name">
              <input id="ob-name" value={name} onChange={(e) => setName(e.target.value)} required className={inputClass} />
            </Field>
            <Field id="ob-type" label="Business type">
              <input id="ob-type" value={businessType} onChange={(e) => setBusinessType(e.target.value)} className={inputClass} />
            </Field>
            <Field id="ob-email" label="Billing email">
              <input id="ob-email" type="email" value={contactEmail} onChange={(e) => setContactEmail(e.target.value)} placeholder="billing@example.com" className={inputClass} />
            </Field>
            <Field id="ob-phone" label="Business phone">
              <input id="ob-phone" value={contactPhone} onChange={(e) => setContactPhone(e.target.value)} placeholder="+62 21 ..." className={inputClass} />
            </Field>
          </div>

          <Field id="ob-addr" label="Business address (printed on PDF invoices)">
            <textarea id="ob-addr" rows={2} value={address} onChange={(e) => setAddress(e.target.value)} className={inputClass} />
          </Field>

          <div className="grid grid-cols-1 sm:grid-cols-4 gap-4 pt-3 border-t border-[var(--border-subtle)]">
            <Field id="ob-prefix" label="Invoice prefix">
              <input id="ob-prefix" value={invoicePrefix} onChange={(e) => setInvoicePrefix(e.target.value)} className={inputClass} />
            </Field>
            <Field id="ob-tax-name" label="Tax label">
              <input id="ob-tax-name" value={taxName} onChange={(e) => setTaxName(e.target.value)} className={inputClass} />
            </Field>
            <Field id="ob-tax-rate" label="Default tax %">
              <input id="ob-tax-rate" type="number" step="0.01" min="0" value={taxRate} onChange={(e) => setTaxRate(e.target.value)} className={`${inputClass} num`} />
            </Field>
            <Field id="ob-terms" label="Terms (days)">
              <input id="ob-terms" type="number" min="0" value={paymentTerms} onChange={(e) => setPaymentTerms(e.target.value)} className={`${inputClass} num`} />
            </Field>
          </div>

          <Field id="ob-pay-inst" label="Invoice payment instructions">
            <textarea
              id="ob-pay-inst"
              rows={2}
              value={paymentInstructions}
              onChange={(e) => setPaymentInstructions(e.target.value)}
              placeholder="Specify your bank transfer instructions to print on invoices"
              className={inputClass}
            />
          </Field>

          <div className="flex justify-end gap-3 pt-2">
            <Button variant="secondary" onClick={() => router.push("/workspace")}>
              Skip for now
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? "Saving..." : "Complete setup"}
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}
