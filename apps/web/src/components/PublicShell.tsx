"use client";

import React, { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Menu, Moon, Sun, X } from "lucide-react";
import { LedgerBrandMark } from "@ledger/ui";
import { useAuth, useTheme } from "@/lib/providers";

const PUBLIC_NAV = [
  { href: "/product", label: "Product" },
  { href: "/features", label: "Features" },
  { href: "/accounting", label: "Accounting" },
  { href: "/invoicing", label: "Invoicing" },
  { href: "/security", label: "Security" },
  { href: "/documentation", label: "Documentation" },
  { href: "/demo", label: "Demo" },
];

export function PublicShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { auth } = useAuth();
  const { resolved, toggleQuick } = useTheme();
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <div className="min-h-dvh flex flex-col bg-[var(--bg-canvas)] text-[var(--text-primary)]">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-50 focus:rounded-md focus:bg-[var(--brand-forest)] focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:text-[var(--text-on-brand)]"
      >
        Skip to content
      </a>

      <header className="sticky top-0 z-30 border-b border-[var(--border-subtle)] bg-[var(--bg-canvas)]/95 backdrop-blur-sm">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
          <Link
            href="/"
            className="flex items-center gap-2.5 shrink-0 min-h-[44px]"
            aria-label="Ledger home"
          >
            <LedgerBrandMark
              className="h-8 w-8"
              variant={resolved === "dark" ? "dark" : "forest"}
            />
            <span className="font-editorial text-xl font-semibold tracking-tight text-[var(--text-primary)]">
              Ledger
            </span>
          </Link>

          <nav aria-label="Primary" className="hidden lg:flex items-center gap-1">
            {PUBLIC_NAV.map((item) => {
              const active = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  className={`px-3 py-2 rounded-md text-sm transition-colors ${
                    active
                      ? "font-semibold text-[var(--brand-forest)] bg-[var(--bg-accent-soft)]/70 underline decoration-2 underline-offset-4"
                      : "text-[var(--text-secondary)] hover:text-[var(--text-primary)] hover:bg-[var(--bg-surface-subtle)]"
                  }`}
                >
                  {item.label}
                </Link>
              );
            })}
          </nav>

          <div className="hidden lg:flex items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={toggleQuick}
              aria-label={resolved === "dark" ? "Switch to light theme" : "Switch to dark theme"}
              className="inline-flex h-11 w-11 items-center justify-center rounded-md border border-[var(--border-subtle)] bg-[var(--bg-surface)] text-[var(--text-primary)] hover:bg-[var(--bg-surface-subtle)]"
            >
              {resolved === "dark" ? (
                <Sun className="h-4 w-4" aria-hidden="true" />
              ) : (
                <Moon className="h-4 w-4" aria-hidden="true" />
              )}
            </button>
            {auth ? (
              <Link
                href="/workspace"
                className="inline-flex min-h-[44px] items-center rounded-md bg-[var(--brand-forest)] px-4 text-sm font-medium text-[var(--text-on-brand)] hover:bg-[var(--brand-forest-hover)]"
              >
                Open workspace
              </Link>
            ) : (
              <>
                <Link
                  href="/login"
                  className="inline-flex min-h-[44px] items-center rounded-md px-3.5 text-sm font-medium text-[var(--text-primary)] hover:bg-[var(--bg-surface-subtle)]"
                >
                  Sign in
                </Link>
                <Link
                  href="/register"
                  className="inline-flex min-h-[44px] items-center rounded-md bg-[var(--brand-forest)] px-4 text-sm font-medium text-[var(--text-on-brand)] hover:bg-[var(--brand-forest-hover)]"
                >
                  Create account
                </Link>
              </>
            )}
          </div>

          <div className="flex lg:hidden items-center gap-2">
            <button
              type="button"
              onClick={toggleQuick}
              aria-label={resolved === "dark" ? "Switch to light theme" : "Switch to dark theme"}
              className="inline-flex h-11 w-11 items-center justify-center rounded-md border border-[var(--border-subtle)] bg-[var(--bg-surface)] text-[var(--text-primary)]"
            >
              {resolved === "dark" ? (
                <Sun className="h-4 w-4" aria-hidden="true" />
              ) : (
                <Moon className="h-4 w-4" aria-hidden="true" />
              )}
            </button>
            <button
              type="button"
              aria-expanded={mobileOpen}
              aria-controls="public-mobile-nav"
              onClick={() => setMobileOpen((v) => !v)}
              className="inline-flex min-h-[44px] items-center gap-2 rounded-md border border-[var(--border-subtle)] bg-[var(--bg-surface)] px-3 text-sm font-medium text-[var(--text-primary)]"
            >
              {mobileOpen ? (
                <X className="h-4 w-4" aria-hidden="true" />
              ) : (
                <Menu className="h-4 w-4" aria-hidden="true" />
              )}
              <span>Menu</span>
            </button>
          </div>
        </div>

        {mobileOpen ? (
          <nav
            id="public-mobile-nav"
            aria-label="Mobile navigation"
            className="lg:hidden border-t border-[var(--border-subtle)] bg-[var(--bg-surface)] px-4 py-4"
          >
            <ul className="flex flex-col gap-1">
              {PUBLIC_NAV.map((item) => {
                const active = pathname === item.href;
                return (
                  <li key={item.href}>
                    <Link
                      href={item.href}
                      onClick={() => setMobileOpen(false)}
                      aria-current={active ? "page" : undefined}
                      className={`flex min-h-[44px] items-center rounded-md px-3 text-sm ${
                        active
                          ? "font-semibold bg-[var(--bg-accent-soft)] text-[var(--brand-forest)]"
                          : "text-[var(--text-primary)] hover:bg-[var(--bg-surface-subtle)]"
                      }`}
                    >
                      {item.label}
                    </Link>
                  </li>
                );
              })}
            </ul>
            <div className="mt-4 pt-4 border-t border-[var(--border-subtle)] flex flex-col gap-2">
              {auth ? (
                <Link
                  href="/workspace"
                  onClick={() => setMobileOpen(false)}
                  className="flex min-h-[44px] items-center justify-center rounded-md bg-[var(--brand-forest)] px-4 text-sm font-medium text-[var(--text-on-brand)]"
                >
                  Open workspace
                </Link>
              ) : (
                <>
                  <Link
                    href="/login"
                    onClick={() => setMobileOpen(false)}
                    className="flex min-h-[44px] items-center justify-center rounded-md border border-[var(--border-strong)] px-4 text-sm font-medium text-[var(--text-primary)]"
                  >
                    Sign in
                  </Link>
                  <Link
                    href="/register"
                    onClick={() => setMobileOpen(false)}
                    className="flex min-h-[44px] items-center justify-center rounded-md bg-[var(--brand-forest)] px-4 text-sm font-medium text-[var(--text-on-brand)]"
                  >
                    Create account
                  </Link>
                </>
              )}
            </div>
          </nav>
        ) : null}
      </header>

      <main id="main-content" className="flex-1">
        {children}
      </main>

      <footer className="border-t border-[var(--border-subtle)] bg-[var(--bg-surface)] mt-16">
        <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 flex flex-col gap-8 md:flex-row md:items-start md:justify-between">
          <div className="max-w-sm">
            <div className="flex items-center gap-2.5">
              <LedgerBrandMark
                className="h-7 w-7"
                variant={resolved === "dark" ? "dark" : "forest"}
              />
              <span className="font-editorial text-lg font-semibold">Ledger</span>
            </div>
            <p className="mt-3 text-sm text-[var(--text-secondary)] leading-relaxed">
              Clarity in every transaction. A financial management and double-entry accounting
              portfolio application engineered by{" "}
              <a
                href="https://www.dandisetiawan.id/"
                target="_blank"
                rel="noreferrer"
                className="underline underline-offset-2 text-[var(--text-primary)] hover:text-[var(--brand-forest)]"
              >
                Dandi Setiawan
              </a>
              .
            </p>
            <p className="mt-2 text-xs text-[var(--text-secondary)]">
              Working portfolio software. Not an accounting certification or statutory tax filing
              service.
            </p>
          </div>

          <div className="flex flex-wrap gap-10 text-sm">
            <div>
              <p className="font-semibold text-[var(--text-primary)] mb-2.5">Platform</p>
              <ul className="space-y-2 text-[var(--text-secondary)]">
                <li><Link href="/product" className="hover:text-[var(--text-primary)]">Product overview</Link></li>
                <li><Link href="/accounting" className="hover:text-[var(--text-primary)]">Accounting engine</Link></li>
                <li><Link href="/invoicing" className="hover:text-[var(--text-primary)]">Invoicing &amp; PDFs</Link></li>
                <li><Link href="/demo" className="hover:text-[var(--text-primary)]">Interactive demo</Link></li>
              </ul>
            </div>
            <div>
              <p className="font-semibold text-[var(--text-primary)] mb-2.5">Engineering</p>
              <ul className="space-y-2 text-[var(--text-secondary)]">
                <li><Link href="/features" className="hover:text-[var(--text-primary)]">Capabilities</Link></li>
                <li><Link href="/security" className="hover:text-[var(--text-primary)]">Security model</Link></li>
                <li><Link href="/documentation" className="hover:text-[var(--text-primary)]">Documentation</Link></li>
                <li>
                  <a
                    href="https://github.com/dandisetiawanco/Ledger"
                    target="_blank"
                    rel="noreferrer"
                    className="hover:text-[var(--text-primary)]"
                  >
                    GitHub repository
                  </a>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </footer>
    </div>
  );
}
