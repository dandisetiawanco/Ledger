"use client";

import React, { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { canManageOrg, canWriteAccounting } from "@ledger/contracts";
import { apiFetch, ApiError } from "@/lib/api";
import { useAuth, useTheme, type ThemePreference } from "@/lib/providers";
import {
  Button,
  ErrorState,
  Field,
  LoadingState,
  PageHeader,
  StatusBadge,
  TableRegion,
  inputClass,
} from "@/components/ui";

interface OrgDetail {
  id: string;
  name: string;
  baseCurrency: string;
  timeZone: string;
  contactEmail: string | null;
  address: string | null;
  settings: {
    invoicePrefix: string;
    defaultTaxName: string;
    defaultTaxRate: string;
    requireExpenseApproval: boolean;
    requireJournalApproval: boolean;
    enforceSeparationOfDuties: boolean;
    expenseApprovalThreshold: string;
  };
}

interface PeriodItem {
  id: string;
  name: string;
  startDate: string;
  endDate: string;
  status: "OPEN" | "CLOSED";
}

interface ExchangeRateItem {
  id: string;
  fromCurrency: string;
  toCurrency: string;
  rate: string;
  effectiveDate: string;
  source: string;
}

export default function SettingsPage() {
  const { auth } = useAuth();
  const { preference, resolved, setPreference } = useTheme();
  const queryClient = useQueryClient();
  const isAdmin = canManageOrg(auth?.activeOrganization?.role);
  const canAccount = canWriteAccounting(auth?.activeOrganization?.role);

  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const [orgName, setOrgName] = useState("");
  const [taxRate, setTaxRate] = useState("11");
  const [requireExpApproval, setRequireExpApproval] = useState(true);
  const [enforceSod, setEnforceSod] = useState(true);
  const [threshold, setThreshold] = useState("15000000");

  const [fxFrom, setFxFrom] = useState("USD");
  const [fxRate, setFxRate] = useState("16050");
  const [fxDate, setFxDate] = useState(() => new Date().toISOString().slice(0, 10));

  const { data: org, isLoading } = useQuery<OrgDetail>({
    queryKey: ["organization", "current"],
    queryFn: () => apiFetch<OrgDetail>("/api/v1/organizations/current"),
  });
  const { data: periods = [] } = useQuery<PeriodItem[]>({
    queryKey: ["periods"],
    queryFn: () => apiFetch<PeriodItem[]>("/api/v1/periods"),
  });
  const { data: rates = [] } = useQuery<ExchangeRateItem[]>({
    queryKey: ["exchange-rates"],
    queryFn: () => apiFetch<ExchangeRateItem[]>("/api/v1/organizations/current/exchange-rates"),
  });

  useEffect(() => {
    if (!org) return;
    setOrgName(org.name);
    setTaxRate(org.settings.defaultTaxRate);
    setRequireExpApproval(org.settings.requireExpenseApproval);
    setEnforceSod(org.settings.enforceSeparationOfDuties);
    setThreshold(org.settings.expenseApprovalThreshold);
  }, [org]);

  async function saveOrgSettings(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    setErr(null);
    try {
      await apiFetch("/api/v1/organizations/current", {
        method: "PATCH",
        body: JSON.stringify({
          name: orgName,
          defaultTaxRate: Number(taxRate),
          requireExpenseApproval: requireExpApproval,
          enforceSeparationOfDuties: enforceSod,
          expenseApprovalThreshold: Number(threshold),
        }),
      });
      await queryClient.invalidateQueries({ queryKey: ["organization", "current"] });
      setMsg("Organization financial controls updated.");
    } catch (e2) {
      setErr(e2 instanceof ApiError ? e2.message : "Failed to save settings");
    }
  }

  async function saveExchangeRate(e: React.FormEvent) {
    e.preventDefault();
    if (!org) return;
    setMsg(null);
    setErr(null);
    try {
      await apiFetch("/api/v1/organizations/current/exchange-rates", {
        method: "POST",
        body: JSON.stringify({
          fromCurrency: fxFrom,
          toCurrency: org.baseCurrency,
          rate: Number(fxRate),
          effectiveDate: fxDate,
        }),
      });
      await queryClient.invalidateQueries({ queryKey: ["exchange-rates"] });
      setMsg("Manual exchange rate saved.");
    } catch (e2) {
      setErr(e2 instanceof ApiError ? e2.message : "Failed to save exchange rate");
    }
  }

  async function togglePeriod(p: PeriodItem) {
    setMsg(null);
    setErr(null);
    try {
      if (p.status === "OPEN") {
        await apiFetch(`/api/v1/periods/${p.id}/close`, { method: "POST" });
        setMsg(`Closed accounting period ${p.name}.`);
      } else {
        await apiFetch(`/api/v1/periods/${p.id}/reopen`, {
          method: "POST",
          body: JSON.stringify({ reason: "Reopened by administrator for documented adjustment" }),
        });
        setMsg(`Reopened accounting period ${p.name}.`);
      }
      await queryClient.invalidateQueries({ queryKey: ["periods"] });
    } catch (e2) {
      setErr(e2 instanceof ApiError ? e2.message : "Could not update period status");
    }
  }

  return (
    <div className="space-y-8 max-w-5xl">
      <PageHeader
        title="Settings, Periods & Exchange Rates"
        description="Manage appearance preference, approval controls, manual foreign exchange rates, and monthly accounting period locks."
      />

      {err ? <ErrorState message={err} /> : null}
      {msg ? (
        <div className="rounded-md bg-[var(--status-paid-bg)] px-4 py-3 text-sm text-[var(--status-paid-text)]">
          {msg}
        </div>
      ) : null}

      {/* Appearance Theme Control (3-state System / Light / Dark per modern-web-guidance: dark-mode-toggle) */}
      <section className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6">
        <h2 className="text-base font-semibold">Appearance</h2>
        <p className="mt-1 text-xs text-[var(--text-secondary)]">
          Follows your operating system color scheme by default, or pin Light or Dark mode for this browser.
        </p>
        <div className="mt-4 flex flex-wrap gap-2" role="radiogroup" aria-label="Theme preference">
          {(["system", "light", "dark"] as ThemePreference[]).map((opt) => (
            <button
              key={opt}
              type="button"
              role="radio"
              aria-checked={preference === opt}
              onClick={() => setPreference(opt)}
              className={`min-h-[42px] rounded-md border px-4 text-sm font-medium ${
                preference === opt
                  ? "bg-[var(--brand-forest)] text-[var(--text-on-brand)] border-[var(--brand-forest)]"
                  : "bg-[var(--bg-canvas)] text-[var(--text-primary)] border-[var(--border-subtle)]"
              }`}
            >
              {opt === "system" ? `System (Currently ${resolved})` : opt[0].toUpperCase() + opt.slice(1)}
            </button>
          ))}
        </div>
      </section>

      {isLoading ? (
        <LoadingState label="Loading organization settings..." />
      ) : org ? (
        <section className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6">
          <h2 className="text-base font-semibold">Organization &amp; Approval Controls</h2>
          <form onSubmit={saveOrgSettings} className="mt-4 space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <Field id="set-org" label="Organization name">
                <input id="set-org" disabled={!isAdmin} value={orgName} onChange={(e) => setOrgName(e.target.value)} className={inputClass} />
              </Field>
              <Field id="set-tax" label="Default tax rate (%)">
                <input id="set-tax" type="number" step="0.01" disabled={!isAdmin} value={taxRate} onChange={(e) => setTaxRate(e.target.value)} className={`${inputClass} num`} />
              </Field>
              <Field id="set-thresh" label={`Expense approval threshold (${org.baseCurrency})`}>
                <input id="set-thresh" type="number" step="0.01" disabled={!isAdmin} value={threshold} onChange={(e) => setThreshold(e.target.value)} className={`${inputClass} num`} />
              </Field>
            </div>
            <div className="flex flex-wrap gap-6 text-sm">
              <label className="flex items-center gap-2">
                <input type="checkbox" disabled={!isAdmin} checked={requireExpApproval} onChange={(e) => setRequireExpApproval(e.target.checked)} />
                Require approval for expenses above threshold
              </label>
              <label className="flex items-center gap-2">
                <input type="checkbox" disabled={!isAdmin} checked={enforceSod} onChange={(e) => setEnforceSod(e.target.checked)} />
                Enforce separation of duties (requester cannot self-approve)
              </label>
            </div>
            {isAdmin ? <Button type="submit">Save financial controls</Button> : null}
          </form>
        </section>
      ) : null}

      {/* Manual Foreign Exchange Rates */}
      <section className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6 space-y-4">
        <div>
          <h2 className="text-base font-semibold">Manual Exchange Rates</h2>
          <p className="mt-1 text-xs text-[var(--text-secondary)]">
            Exchange rates are configured manually and preserved at posting time. Live external rate feeds are not enabled.
          </p>
        </div>
        {canAccount && org ? (
          <form onSubmit={saveExchangeRate} className="grid grid-cols-1 sm:grid-cols-4 gap-3 items-end">
            <Field id="fx-from" label="Foreign currency">
              <select id="fx-from" value={fxFrom} onChange={(e) => setFxFrom(e.target.value)} className={inputClass}>
                <option value="USD">USD</option>
                <option value="SGD">SGD</option>
                <option value="EUR">EUR</option>
                <option value="GBP">GBP</option>
                <option value="AUD">AUD</option>
              </select>
            </Field>
            <Field id="fx-rate" label={`Rate to ${org.baseCurrency}`}>
              <input id="fx-rate" type="number" step="0.0001" min="0.0001" required value={fxRate} onChange={(e) => setFxRate(e.target.value)} className={`${inputClass} num`} />
            </Field>
            <Field id="fx-date" label="Effective date">
              <input id="fx-date" type="date" required value={fxDate} onChange={(e) => setFxDate(e.target.value)} className={inputClass} />
            </Field>
            <Button type="submit">Save rate</Button>
          </form>
        ) : null}

        <TableRegion label="Configured exchange rates">
          <table className="ledger-table">
            <thead>
              <tr>
                <th>Pair</th>
                <th className="!text-right">Rate</th>
                <th>Effective Date</th>
                <th>Source</th>
              </tr>
            </thead>
            <tbody>
              {rates.map((r) => (
                <tr key={r.id}>
                  <td className="font-mono-code text-xs font-semibold">{r.fromCurrency} / {r.toCurrency}</td>
                  <td className="text-right num font-semibold">{r.rate}</td>
                  <td className="num text-[var(--text-secondary)]">{r.effectiveDate}</td>
                  <td className="text-xs text-[var(--text-secondary)]">{r.source}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </TableRegion>
      </section>

      {/* Accounting Period Locks */}
      <section className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6 space-y-4">
        <div>
          <h2 className="text-base font-semibold">Accounting Periods</h2>
          <p className="mt-1 text-xs text-[var(--text-secondary)]">
            Closed periods block new or backdated postings until an Administrator reopens the period with an audited reason.
          </p>
        </div>
        <TableRegion label="Accounting periods">
          <table className="ledger-table">
            <thead>
              <tr>
                <th>Period</th>
                <th>Date Range</th>
                <th>Status</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {periods.slice(-14).map((p) => (
                <tr key={p.id}>
                  <td className="font-semibold">{p.name}</td>
                  <td className="num text-xs text-[var(--text-secondary)]">{p.startDate} to {p.endDate}</td>
                  <td><StatusBadge status={p.status} /></td>
                  <td>
                    {canAccount && p.status === "OPEN" ? (
                      <Button variant="secondary" size="sm" onClick={() => togglePeriod(p)}>
                        Close period
                      </Button>
                    ) : isAdmin && p.status === "CLOSED" ? (
                      <Button variant="secondary" size="sm" onClick={() => togglePeriod(p)}>
                        Reopen period
                      </Button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </TableRegion>
      </section>
    </div>
  );
}
