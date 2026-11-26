"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { PublicShell } from "@/components/PublicShell";
import { Button, Field, inputClass } from "@/components/ui";
import { apiFetch, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/providers";

export default function LoginPage() {
  const router = useRouter();
  const { refreshAuth } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await apiFetch("/api/v1/auth/login", {
        method: "POST",
        body: JSON.stringify({ email, password }),
      });
      await refreshAuth();
      router.push("/workspace");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to sign in");
    } finally {
      setLoading(false);
    }
  }

  async function onStartDemo() {
    setError(null);
    setLoading(true);
    try {
      await apiFetch("/api/v1/demo/start", { method: "POST" });
      await refreshAuth();
      router.push("/workspace");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to start demo");
    } finally {
      setLoading(false);
    }
  }

  return (
    <PublicShell>
      <div className="mx-auto max-w-md px-4 py-14 sm:px-6">
        <div className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-7">
          <h1 className="font-editorial text-3xl font-semibold text-[var(--text-primary)]">
            Sign in to Ledger
          </h1>
          <p className="mt-2 text-sm text-[var(--text-secondary)]">
            Access your organization&apos;s accounting workspace or launch the isolated demonstration.
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
            <Field id="login-email" label="Email address">
              <input
                id="login-email"
                type="email"
                required
                autoComplete="email"
                placeholder="email@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={inputClass}
              />
            </Field>
            <Field id="login-password" label="Password">
              <input
                id="login-password"
                type="password"
                required
                autoComplete="current-password"
                placeholder="Enter your password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={inputClass}
              />
            </Field>
            <Button type="submit" disabled={loading} className="w-full">
              {loading ? "Signing in..." : "Sign in"}
            </Button>
          </form>

          <div className="mt-6 pt-5 border-t border-[var(--border-subtle)] space-y-3">
            <Button variant="secondary" onClick={onStartDemo} disabled={loading} className="w-full">
              Open Nusa Karsa Studio demo
            </Button>
            <p className="text-center text-sm text-[var(--text-secondary)]">
              Need a new workspace?{" "}
              <Link href="/register" className="font-semibold text-[var(--brand-forest)] hover:underline">
                Create account
              </Link>
            </p>
          </div>
        </div>
      </div>
    </PublicShell>
  );
}
