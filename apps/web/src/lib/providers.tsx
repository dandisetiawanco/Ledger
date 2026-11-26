"use client";

import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { QueryClient, QueryClientProvider, useQuery, useQueryClient } from "@tanstack/react-query";
import type { AuthResponse } from "@ledger/contracts";
import { apiFetch, clearCsrfCache } from "./api";

export type ThemePreference = "system" | "light" | "dark";

interface ThemeContextValue {
  preference: ThemePreference;
  resolved: "light" | "dark";
  setPreference: (pref: ThemePreference) => void;
  toggleQuick: () => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

function resolveSystem(): "light" | "dark" {
  if (typeof window === "undefined") return "light";
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function applyThemeToDocument(pref: ThemePreference) {
  if (typeof document === "undefined") return;
  const meta = document.querySelector('meta[name="color-scheme"]');
  if (pref === "system") {
    if (meta) meta.setAttribute("content", "light dark");
    document.documentElement.removeAttribute("data-theme");
  } else {
    if (meta) meta.setAttribute("content", pref);
    document.documentElement.setAttribute("data-theme", pref);
  }
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [preference, setPrefState] = useState<ThemePreference>("system");
  const [systemScheme, setSystemScheme] = useState<"light" | "dark">("light");

  useEffect(() => {
    setSystemScheme(resolveSystem());
    try {
      const stored = localStorage.getItem("ledger-color-scheme");
      if (stored === "light" || stored === "dark") {
        setPrefState(stored);
        applyThemeToDocument(stored);
      } else {
        setPrefState("system");
        applyThemeToDocument("system");
      }
    } catch {
      // Storage may be unavailable in strict privacy contexts
    }

    const mq = window.matchMedia("(prefers-color-scheme: dark)");
    const onMqChange = (e: MediaQueryListEvent) => setSystemScheme(e.matches ? "dark" : "light");
    const onStorage = (e: StorageEvent) => {
      if (e.key === "ledger-color-scheme") {
        const next: ThemePreference =
          e.newValue === "light" || e.newValue === "dark" ? e.newValue : "system";
        setPrefState(next);
        applyThemeToDocument(next);
      }
    };
    mq.addEventListener("change", onMqChange);
    window.addEventListener("storage", onStorage);
    return () => {
      mq.removeEventListener("change", onMqChange);
      window.removeEventListener("storage", onStorage);
    };
  }, []);

  const setPreference = useCallback((pref: ThemePreference) => {
    setPrefState(pref);
    applyThemeToDocument(pref);
    try {
      if (pref === "system") {
        localStorage.removeItem("ledger-color-scheme");
      } else {
        localStorage.setItem("ledger-color-scheme", pref);
      }
    } catch {
      // Theme still applies to current session
    }
  }, []);

  const resolved: "light" | "dark" = preference === "system" ? systemScheme : preference;

  const toggleQuick = useCallback(() => {
    const target: "light" | "dark" = resolved === "dark" ? "light" : "dark";
    if (target === systemScheme) {
      setPreference("system");
    } else {
      setPreference(target);
    }
  }, [resolved, systemScheme, setPreference]);

  const value = useMemo(
    () => ({ preference, resolved, setPreference, toggleQuick }),
    [preference, resolved, setPreference, toggleQuick]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error("useTheme must be used inside ThemeProvider");
  return ctx;
}

interface AuthContextValue {
  auth: AuthResponse | null;
  isLoading: boolean;
  refreshAuth: () => Promise<void>;
  logout: () => Promise<void>;
  switchOrganization: (organizationId: string) => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function AuthProviderInner({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient();
  const { data, isLoading, refetch } = useQuery<AuthResponse | null>({
    queryKey: ["auth", "me"],
    queryFn: async () => {
      try {
        return await apiFetch<AuthResponse>("/api/v1/auth/me");
      } catch {
        return null;
      }
    },
    staleTime: 60_000,
    retry: false,
  });

  const refreshAuth = useCallback(async () => {
    await refetch();
  }, [refetch]);

  const logout = useCallback(async () => {
    try {
      await apiFetch("/api/v1/auth/logout", { method: "POST" });
    } finally {
      clearCsrfCache();
      queryClient.clear();
      await refetch();
    }
  }, [queryClient, refetch]);

  const switchOrganization = useCallback(
    async (organizationId: string) => {
      await apiFetch<AuthResponse>("/api/v1/auth/switch-organization", {
        method: "POST",
        body: JSON.stringify({ organizationId }),
      });
      queryClient.clear();
      await refetch();
    },
    [queryClient, refetch]
  );

  const value = useMemo(
    () => ({
      auth: data ?? null,
      isLoading,
      refreshAuth,
      logout,
      switchOrganization,
    }),
    [data, isLoading, refreshAuth, logout, switchOrganization]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AppProviders");
  return ctx;
}

export function AppProviders({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 20_000,
            refetchOnWindowFocus: false,
            retry: 1,
          },
        },
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthProviderInner>{children}</AuthProviderInner>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
