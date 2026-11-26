"use client";

import React, { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeftRight,
  BarChart3,
  Bell,
  BookOpen,
  Building2,
  CheckSquare,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  CreditCard,
  FileSpreadsheet,
  FileText,
  Landmark,
  LayoutDashboard,
  LogOut,
  Menu,
  Moon,
  Package,
  PieChart,
  Receipt,
  Scale,
  Search,
  Settings,
  ShieldCheck,
  Sun,
  Users,
  Wallet,
  X,
} from "lucide-react";
import { LedgerBrandMark } from "@ledger/ui";
import { apiFetch } from "@/lib/api";
import { useAuth, useTheme } from "@/lib/providers";
import { LoadingState } from "@/components/ui";

interface NavItem {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string; "aria-hidden"?: boolean | "true" | "false" }>;
}

const NAV_GROUPS: Array<{ title: string; items: NavItem[] }> = [
  {
    title: "Overview",
    items: [{ href: "/workspace", label: "Financial Overview", icon: LayoutDashboard }],
  },
  {
    title: "Accounting",
    items: [
      { href: "/workspace/accounts", label: "Chart of Accounts", icon: BookOpen },
      { href: "/workspace/journals", label: "Journal Entries", icon: FileSpreadsheet },
      { href: "/workspace/general-ledger", label: "General Ledger", icon: Scale },
    ],
  },
  {
    title: "Sales & Purchases",
    items: [
      { href: "/workspace/invoices", label: "Invoices", icon: FileText },
      { href: "/workspace/expenses", label: "Expenses & Bills", icon: Receipt },
      { href: "/workspace/payments", label: "Payments", icon: CreditCard },
      { href: "/workspace/receivables", label: "Receivables (AR)", icon: Wallet },
      { href: "/workspace/payables", label: "Payables (AP)", icon: ClipboardList },
      { href: "/workspace/contacts", label: "Contacts", icon: Users },
      { href: "/workspace/catalog", label: "Services Catalog", icon: Package },
    ],
  },
  {
    title: "Cash & Planning",
    items: [
      { href: "/workspace/banking", label: "Bank Accounts", icon: Landmark },
      { href: "/workspace/reconciliation", label: "Reconciliation", icon: ArrowLeftRight },
      { href: "/workspace/budgets", label: "Budgets", icon: PieChart },
    ],
  },
  {
    title: "Reports & Governance",
    items: [
      { href: "/workspace/reports", label: "Financial Reports", icon: BarChart3 },
      { href: "/workspace/approvals", label: "Approvals", icon: CheckSquare },
      { href: "/workspace/audit", label: "Audit Trail", icon: ShieldCheck },
      { href: "/workspace/team", label: "Team & Roles", icon: Building2 },
      { href: "/workspace/settings", label: "Settings & Periods", icon: Settings },
    ],
  },
];

interface NotificationItem {
  id: string;
  category: string;
  title: string;
  message: string;
  readAt: string | null;
  createdAt: string;
}

export function WorkspaceShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const queryClient = useQueryClient();
  const { auth, isLoading, logout, switchOrganization } = useAuth();
  const { resolved, toggleQuick } = useTheme();

  const [collapsed, setCollapsed] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [notifOpen, setNotifOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [searchFocused, setSearchFocused] = useState(false);

  useEffect(() => {
    if (!isLoading && !auth) {
      router.replace("/login");
    }
  }, [isLoading, auth, router]);

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") {
        setMobileOpen(false);
        setNotifOpen(false);
        setSearchFocused(false);
      }
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  const { data: notifications = [] } = useQuery<NotificationItem[]>({
    queryKey: ["notifications"],
    queryFn: () => apiFetch<NotificationItem[]>("/api/v1/notifications"),
    enabled: !!auth?.activeOrganization,
  });

  const unreadCount = notifications.filter((n) => !n.readAt).length;

  const allNavItems = useMemo(() => NAV_GROUPS.flatMap((g) => g.items), []);
  const quickMatches = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return [];
    return allNavItems.filter((item) => item.label.toLowerCase().includes(q)).slice(0, 6);
  }, [allNavItems, searchQuery]);

  async function markNotificationRead(id: string) {
    await apiFetch(`/api/v1/notifications/${id}/read`, { method: "POST" });
    await queryClient.invalidateQueries({ queryKey: ["notifications"] });
  }

  async function handleLogout() {
    await logout();
    router.push("/");
  }

  if (isLoading) {
    return (
      <div className="min-h-dvh flex items-center justify-center bg-[var(--bg-canvas)] p-6">
        <LoadingState label="Loading accounting workspace..." />
      </div>
    );
  }

  if (!auth) {
    return null;
  }

  const activeOrg = auth.activeOrganization;

  const isNavActive = (href: string) => {
    if (href === "/workspace") return pathname === "/workspace";
    return pathname === href || pathname.startsWith(`${href}/`);
  };

  const renderSidebarContent = (isMobileDrawer = false) => (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between gap-2 px-3 py-3.5 border-b border-[var(--border-subtle)]">
        <Link
          href="/workspace"
          className="flex items-center gap-2.5 min-w-0 min-h-[40px]"
          onClick={() => isMobileDrawer && setMobileOpen(false)}
        >
          <LedgerBrandMark
            className="h-7 w-7 shrink-0"
            variant={resolved === "dark" ? "dark" : "forest"}
          />
          {(!collapsed || isMobileDrawer) && (
            <span className="font-editorial text-lg font-semibold tracking-tight truncate">
              Ledger
            </span>
          )}
        </Link>
        {isMobileDrawer ? (
          <button
            type="button"
            onClick={() => setMobileOpen(false)}
            aria-label="Close navigation menu"
            className="inline-flex h-10 w-10 items-center justify-center rounded-md text-[var(--text-secondary)] hover:bg-[var(--bg-surface-subtle)]"
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        ) : (
          <button
            type="button"
            aria-expanded={!collapsed}
            aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
            onClick={() => setCollapsed((c) => !c)}
            className="hidden lg:inline-flex h-9 w-9 items-center justify-center rounded-md text-[var(--text-secondary)] hover:bg-[var(--bg-surface-subtle)]"
          >
            {collapsed ? (
              <ChevronRight className="h-4 w-4" aria-hidden="true" />
            ) : (
              <ChevronLeft className="h-4 w-4" aria-hidden="true" />
            )}
          </button>
        )}
      </div>

      {/* Organization Switcher */}
      {(!collapsed || isMobileDrawer) && activeOrg ? (
        <div className="px-3 py-3 border-b border-[var(--border-subtle)]">
          <label htmlFor={isMobileDrawer ? "org-switch-m" : "org-switch-d"} className="sr-only">
            Active organization
          </label>
          <select
            id={isMobileDrawer ? "org-switch-m" : "org-switch-d"}
            value={activeOrg.organizationId}
            onChange={(e) => switchOrganization(e.target.value)}
            className="w-full min-h-[40px] rounded-md border border-[var(--border-subtle)] bg-[var(--bg-canvas)] px-2.5 py-1.5 text-xs font-semibold text-[var(--text-primary)]"
          >
            {auth.organizations.map((org) => (
              <option key={org.organizationId} value={org.organizationId}>
                {org.name} ({org.baseCurrency})
              </option>
            ))}
          </select>
          <p className="mt-1.5 px-0.5 text-[11px] text-[var(--text-secondary)] flex items-center justify-between">
            <span>Role: {activeOrg.role.replaceAll("_", " ")}</span>
            {activeOrg.demo ? (
              <span className="font-semibold text-[var(--brand-forest)]">Demo</span>
            ) : null}
          </p>
        </div>
      ) : null}

      <nav aria-label="Workspace navigation" className="flex-1 overflow-y-auto px-2 py-3 space-y-5">
        {NAV_GROUPS.map((group) => (
          <div key={group.title}>
            {(!collapsed || isMobileDrawer) && (
              <p className="px-2.5 pb-1.5 text-[11px] font-semibold text-[var(--text-secondary)]">
                {group.title}
              </p>
            )}
            <ul className="space-y-0.5">
              {group.items.map((item) => {
                const Icon = item.icon;
                const active = isNavActive(item.href);
                return (
                  <li key={item.href}>
                    <Link
                      href={item.href}
                      title={collapsed && !isMobileDrawer ? item.label : undefined}
                      aria-label={collapsed && !isMobileDrawer ? item.label : undefined}
                      aria-current={active ? "page" : undefined}
                      onClick={() => isMobileDrawer && setMobileOpen(false)}
                      className={`flex min-h-[40px] items-center gap-2.5 rounded-md px-2.5 text-sm transition-colors ${
                        active
                          ? "bg-[var(--bg-accent-soft)] text-[var(--brand-forest)] font-semibold border-l-[3px] border-[var(--brand-forest)]"
                          : "text-[var(--text-secondary)] hover:bg-[var(--bg-surface-subtle)] hover:text-[var(--text-primary)]"
                      }`}
                    >
                      <Icon className="h-4 w-4 shrink-0" aria-hidden="true" />
                      {(!collapsed || isMobileDrawer) && (
                        <span className="truncate">{item.label}</span>
                      )}
                    </Link>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </nav>

      <div className="border-t border-[var(--border-subtle)] p-3">
        <div className="flex items-center justify-between gap-2">
          {(!collapsed || isMobileDrawer) && (
            <div className="min-w-0">
              <p className="text-xs font-semibold text-[var(--text-primary)] truncate">
                {auth.user.fullName}
              </p>
              <p className="text-[11px] text-[var(--text-secondary)] truncate">
                {auth.user.email}
              </p>
            </div>
          )}
          <button
            type="button"
            onClick={handleLogout}
            aria-label="Sign out"
            title="Sign out"
            className="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-md text-[var(--text-secondary)] hover:bg-[var(--bg-surface-subtle)] hover:text-[var(--text-primary)]"
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>
  );

  return (
    <div className="min-h-dvh flex bg-[var(--bg-canvas)] text-[var(--text-primary)]">
      {/* Desktop Sidebar */}
      <aside
        className={`no-print hidden lg:flex flex-col shrink-0 border-r border-[var(--border-subtle)] bg-[var(--bg-surface)] transition-[width] duration-200 ${
          collapsed ? "w-[68px]" : "w-[252px]"
        }`}
      >
        {renderSidebarContent(false)}
      </aside>

      {/* Mobile Navigation Drawer */}
      {mobileOpen ? (
        <div className="fixed inset-0 z-50 lg:hidden flex">
          <div
            className="fixed inset-0 bg-black/45"
            aria-hidden="true"
            onClick={() => setMobileOpen(false)}
          />
          <aside className="relative z-10 w-[276px] max-w-[85dvw] bg-[var(--bg-surface)] border-r border-[var(--border-subtle)] shadow-xl">
            {renderSidebarContent(true)}
          </aside>
        </div>
      ) : null}

      <div className="flex-1 min-w-0 flex flex-col">
        {activeOrg?.demo ? (
          <div className="no-print bg-[#183D30] text-[#EDF2ED] px-4 py-2 text-xs flex flex-wrap items-center justify-between gap-2">
            <span>
              <strong>Demonstration Workspace:</strong> Nusa Karsa Studio uses synthetic six-month
              agency accounting records. External payments and emails are disabled.
            </span>
            <Link href="/register" className="underline underline-offset-2 font-semibold text-[#D6E8D4]">
              Create your own workspace
            </Link>
          </div>
        ) : null}

        <header className="no-print sticky top-0 z-20 flex items-center justify-between gap-3 border-b border-[var(--border-subtle)] bg-[var(--bg-surface)] px-4 py-2.5 sm:px-6">
          <div className="flex items-center gap-3 min-w-0 flex-1">
            <button
              type="button"
              onClick={() => setMobileOpen(true)}
              aria-label="Open navigation menu"
              className="lg:hidden inline-flex min-h-[44px] items-center gap-2 rounded-md border border-[var(--border-subtle)] px-3 text-sm font-medium text-[var(--text-primary)]"
            >
              <Menu className="h-4 w-4" aria-hidden="true" />
              <span>Menu</span>
            </button>

            <div className="relative w-full max-w-sm hidden sm:block">
              <Search
                className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--text-secondary)]"
                aria-hidden="true"
              />
              <input
                type="search"
                aria-label="Jump to workspace section"
                placeholder="Jump to accounts, invoices, reports..."
                value={searchQuery}
                onFocus={() => setSearchFocused(true)}
                onBlur={() => setTimeout(() => setSearchFocused(false), 160)}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full min-h-[40px] rounded-md border border-[var(--border-subtle)] bg-[var(--bg-canvas)] pl-9 pr-3 text-sm text-[var(--text-primary)] placeholder:text-[var(--text-secondary)]"
              />
              {searchFocused && quickMatches.length > 0 ? (
                <ul className="absolute left-0 right-0 mt-1 rounded-md border border-[var(--border-subtle)] bg-[var(--bg-surface)] py-1 shadow-lg z-40">
                  {quickMatches.map((m) => (
                    <li key={m.href}>
                      <Link
                        href={m.href}
                        onClick={() => {
                          setSearchQuery("");
                          setSearchFocused(false);
                        }}
                        className="block px-3 py-2 text-sm text-[var(--text-primary)] hover:bg-[var(--bg-surface-subtle)]"
                      >
                        {m.label}
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : null}
            </div>
          </div>

          <div className="flex items-center gap-2 shrink-0">
            <div className="relative">
              <button
                type="button"
                aria-label={`Notifications (${unreadCount} unread)`}
                aria-expanded={notifOpen}
                onClick={() => setNotifOpen((v) => !v)}
                className="relative inline-flex h-11 w-11 items-center justify-center rounded-md border border-[var(--border-subtle)] bg-[var(--bg-canvas)] text-[var(--text-primary)] hover:bg-[var(--bg-surface-subtle)]"
              >
                <Bell className="h-4 w-4" aria-hidden="true" />
                {unreadCount > 0 ? (
                  <span className="absolute -top-1 -right-1 inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-[var(--brand-forest)] px-1 text-[10px] font-bold text-[var(--text-on-brand)]">
                    {unreadCount}
                  </span>
                ) : null}
              </button>

              {notifOpen ? (
                <div className="absolute right-0 mt-2 w-80 max-w-[90dvw] rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] shadow-xl z-40">
                  <div className="flex items-center justify-between px-4 py-3 border-b border-[var(--border-subtle)]">
                    <span className="text-sm font-semibold">Notifications</span>
                    <button
                      type="button"
                      onClick={() => setNotifOpen(false)}
                      className="text-xs text-[var(--text-secondary)] hover:text-[var(--text-primary)]"
                    >
                      Close
                    </button>
                  </div>
                  <div className="max-h-80 overflow-y-auto divide-y divide-[var(--border-subtle)]">
                    {notifications.length === 0 ? (
                      <p className="p-4 text-xs text-[var(--text-secondary)]">
                        No accounting alerts or approval notifications.
                      </p>
                    ) : (
                      notifications.map((n) => (
                        <div key={n.id} className="p-3.5 text-xs">
                          <div className="flex items-start justify-between gap-2">
                            <p className="font-semibold text-[var(--text-primary)]">{n.title}</p>
                            {!n.readAt ? (
                              <button
                                type="button"
                                onClick={() => markNotificationRead(n.id)}
                                className="text-[11px] font-semibold text-[var(--brand-forest)] shrink-0 hover:underline"
                              >
                                Mark read
                              </button>
                            ) : null}
                          </div>
                          <p className="mt-1 text-[var(--text-secondary)]">{n.message}</p>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              ) : null}
            </div>

            <button
              type="button"
              onClick={toggleQuick}
              aria-label={resolved === "dark" ? "Switch to light theme" : "Switch to dark theme"}
              className="inline-flex h-11 w-11 items-center justify-center rounded-md border border-[var(--border-subtle)] bg-[var(--bg-canvas)] text-[var(--text-primary)] hover:bg-[var(--bg-surface-subtle)]"
            >
              {resolved === "dark" ? (
                <Sun className="h-4 w-4" aria-hidden="true" />
              ) : (
                <Moon className="h-4 w-4" aria-hidden="true" />
              )}
            </button>
          </div>
        </header>

        <main className="flex-1 p-4 sm:p-6 lg:p-8 max-w-[1440px] w-full mx-auto">
          {children}
        </main>
      </div>
    </div>
  );
}
