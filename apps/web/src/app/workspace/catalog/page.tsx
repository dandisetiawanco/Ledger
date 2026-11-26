"use client";

import React, { useState } from "react";
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
  TableRegion,
  inputClass,
} from "@/components/ui";

interface CatalogItem {
  id: string;
  sku: string | null;
  name: string;
  description: string | null;
  unitPrice: string;
  currencyCode: string;
  revenueAccountCode: string;
  revenueAccountName: string;
  defaultTaxRate: string;
  active: boolean;
}

export default function CatalogPage() {
  const { auth } = useAuth();
  const queryClient = useQueryClient();
  const ccy = auth?.activeOrganization?.baseCurrency ?? "IDR";
  const canOperate = canOperateFinance(auth?.activeOrganization?.role);

  const [modalOpen, setModalOpen] = useState(false);
  const [sku, setSku] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [unitPrice, setUnitPrice] = useState("");
  const [taxRate, setTaxRate] = useState("11");
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const { data: items = [], isLoading, error, refetch } = useQuery<CatalogItem[]>({
    queryKey: ["catalog"],
    queryFn: () => apiFetch<CatalogItem[]>("/api/v1/catalog"),
  });

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setFormError(null);
    setSaving(true);
    try {
      await apiFetch("/api/v1/catalog", {
        method: "POST",
        body: JSON.stringify({
          sku: sku || undefined,
          name,
          description: description || undefined,
          unitPrice: Number(unitPrice),
          currencyCode: ccy,
          defaultTaxRate: Number(taxRate || 0),
        }),
      });
      await queryClient.invalidateQueries({ queryKey: ["catalog"] });
      setModalOpen(false);
      setSku("");
      setName("");
      setDescription("");
      setUnitPrice("");
    } catch (err) {
      setFormError(err instanceof ApiError ? err.message : "Could not save catalog item");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="Services & Products Catalog"
        description="Reusable service items mapped to revenue accounts and configurable tax rates for consistent invoicing. Inventory valuation is outside the current service-business scope."
        actions={canOperate ? <Button onClick={() => setModalOpen(true)}>Add service item</Button> : null}
      />

      {isLoading ? <LoadingState label="Loading catalog..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Unable to load catalog"}
          onRetry={() => refetch()}
        />
      ) : null}

      {!isLoading && !error ? (
        items.length === 0 ? (
          <EmptyState title="No catalog items yet" description="Add your standard agency services to speed up invoice entry." />
        ) : (
          <TableRegion label="Products and services catalog">
            <table className="ledger-table">
              <thead>
                <tr>
                  <th>SKU</th>
                  <th>Service Name</th>
                  <th>Revenue Account</th>
                  <th className="!text-right">Default Tax</th>
                  <th className="!text-right">Unit Price</th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id}>
                    <td className="font-mono-code text-xs text-[var(--text-secondary)]">{item.sku || ""}</td>
                    <td>
                      <div className="font-medium">{item.name}</div>
                      {item.description ? <div className="text-xs text-[var(--text-secondary)]">{item.description}</div> : null}
                    </td>
                    <td className="text-xs text-[var(--text-secondary)]">
                      <span className="font-mono-code">{item.revenueAccountCode}</span> {item.revenueAccountName}
                    </td>
                    <td className="text-right num">{item.defaultTaxRate}%</td>
                    <td className="text-right num font-semibold whitespace-nowrap">
                      {formatMoney(item.unitPrice, item.currencyCode)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableRegion>
        )
      ) : null}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Add service catalog item">
        <form onSubmit={handleCreate} className="space-y-4">
          {formError ? (
            <div role="alert" className="rounded-md bg-[var(--status-overdue-bg)] px-3.5 py-2.5 text-sm text-[var(--status-overdue-text)]">
              {formError}
            </div>
          ) : null}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Field id="cat-sku" label="SKU / Reference">
              <input id="cat-sku" value={sku} onChange={(e) => setSku(e.target.value)} placeholder="e.g. SVC-WEB" className={inputClass} />
            </Field>
            <Field id="cat-tax" label="Default tax %">
              <input id="cat-tax" type="number" step="0.01" min="0" value={taxRate} onChange={(e) => setTaxRate(e.target.value)} className={`${inputClass} num`} />
            </Field>
          </div>
          <Field id="cat-name" label="Service name">
            <input id="cat-name" required value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Technical Architecture Sprint" className={inputClass} />
          </Field>
          <Field id="cat-desc" label="Description">
            <input id="cat-desc" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Short scope summary" className={inputClass} />
          </Field>
          <Field id="cat-price" label={`Unit price (${ccy})`}>
            <input id="cat-price" type="number" step="0.01" min="0" required value={unitPrice} onChange={(e) => setUnitPrice(e.target.value)} className={`${inputClass} num`} />
          </Field>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" disabled={saving}>{saving ? "Saving..." : "Save item"}</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
