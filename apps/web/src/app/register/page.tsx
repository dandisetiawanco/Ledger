"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { PublicShell } from "@/components/PublicShell";
import { Button, Field, inputClass } from "@/components/ui";
import { apiFetch, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/providers";

export default function RegisterPage() {
  const router = useRouter();
  const { refreshAuth } = useAuth();
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [organizationName, setOrganizationName] = useState("");
  const [businessType, setBusinessType] = useState("Digital & Consulting Agency");
  const [baseCurrency, setBaseCurrency] = useState("IDR");
  const [timeZone, setTimeZone] = useState("Asia/Jakarta");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await apiFetch("/api/v1/auth/register", {
        method: "POST",
        body: JSON.stringify({
          fullName,
          email,
          password,
          organizationName,
          businessType,
          baseCurrency,
          timeZone,
          locale: baseCurrency === "IDR" ? "id-ID" : "en-US",
          fiscalYearStartMonth: 1,
        }),
      });
      await refreshAuth();
      router.push("/workspace/onboarding");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Registration failed");
    } finally {
      setLoading(false);
    }
  }

  return (
    <PublicShell>
      <div className="mx-auto max-w-lg px-4 py-14 sm:px-6">
        <div className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-7">
          <h1 className="font-editorial text-3xl font-semibold text-[var(--text-primary)]">
            Create your Ledger workspace
          </h1>
          <p className="mt-2 text-sm text-[var(--text-secondary)]">
            Provisions a normalized Chart of Accounts, fiscal year periods, and organization-scoped
            ledger automatically.
          </p>

          {error ? (
            <div
              role="alert"
              className="mt-5 rounded-md border border-[var(--status-overdue-text)] bg-[var(--status-overdue-bg)] px-3.5 py-2.5 text-sm text-[var(--status-overdue-text)]"
            >
              {error}
            </div>
          ) : null}

          <form onSubmit={onSubmit} className="mt-6 space-y-4">
            <Field id="reg-name" label="Full name">
              <input
                id="reg-name"
                type="text"
                required
                placeholder="Your full name"
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                className={inputClass}
              />
            </Field>
            <Field id="reg-email" label="Work email">
              <input
                id="reg-email"
                type="email"
                required
                placeholder="email@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={inputClass}
              />
            </Field>
            <Field id="reg-password" label="Password" hint="Minimum 8 characters">
              <input
                id="reg-password"
                type="password"
                required
                minLength={8}
                placeholder="Create a strong password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={inputClass}
              />
            </Field>
            <div className="pt-2 border-t border-[var(--border-subtle)] space-y-4">
              <Field id="reg-org" label="Organization name">
                <input
                  id="reg-org"
                  type="text"
                  required
                  placeholder="Your business or agency name"
                  value={organizationName}
                  onChange={(e) => setOrganizationName(e.target.value)}
                  className={inputClass}
                />
              </Field>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Field id="reg-currency" label="Base currency">
                  <select
                    id="reg-currency"
                    value={baseCurrency}
                    onChange={(e) => setBaseCurrency(e.target.value)}
                    className={inputClass}
                  >
                    <option value="IDR">IDR (Indonesian Rupiah)</option>
                    <option value="USD">USD (US Dollar)</option>
                    <option value="SGD">SGD (Singapore Dollar)</option>
                    <option value="EUR">EUR (Euro)</option>
                    <option value="GBP">GBP (British Pound)</option>
                    <option value="AUD">AUD (Australian Dollar)</option>
                  </select>
                </Field>
                <Field id="reg-tz" label="Time zone">
                  <select
                    id="reg-tz"
                    value={timeZone}
                    onChange={(e) => setTimeZone(e.target.value)}
                    className={inputClass}
                  >
                    <option value="Asia/Jakarta">Asia/Jakarta (WIB)</option>
                    <option value="Asia/Singapore">Asia/Singapore (SGT)</option>
                    <option value="UTC">UTC</option>
                    <option value="America/New_York">America/New_York</option>
                    <option value="Europe/London">Europe/London</option>
                  </select>
                </Field>
              </div>
              <Field id="reg-type" label="Business type">
                <input
                  id="reg-type"
                  type="text"
                  value={businessType}
                  onChange={(e) => setBusinessType(e.target.value)}
                  className={inputClass}
                />
              </Field>
            </div>
            <Button type="submit" disabled={loading} className="w-full">
              {loading ? "Creating workspace..." : "Create account and continue"}
            </Button>
          </form>

          <p className="mt-5 text-center text-sm text-[var(--text-secondary)]">
            Already have an account?{" "}
            <Link href="/login" className="font-semibold text-[var(--brand-forest)] hover:underline">
              Sign in
            </Link>
          </p>
        </div>
      </div>
    </PublicShell>
  );
}
