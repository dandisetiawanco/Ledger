"use client";

import React, { useEffect, useRef } from "react";
import { X, AlertCircle, Loader2 } from "lucide-react";

type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";

export function Button({
  variant = "primary",
  size = "md",
  className = "",
  type = "button",
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  size?: "sm" | "md";
}) {
  const base =
    "inline-flex items-center justify-center gap-2 font-medium rounded-md transition-colors whitespace-nowrap";
  const sizes = size === "sm" ? "min-h-[38px] px-3 text-sm" : "min-h-[44px] px-4 text-sm";

  const styles: Record<ButtonVariant, string> = {
    primary:
      "bg-[var(--brand-forest)] text-[var(--text-on-brand)] hover:bg-[var(--brand-forest-hover)] active:opacity-90",
    secondary:
      "bg-[var(--bg-surface)] text-[var(--text-primary)] border border-[var(--border-strong)] hover:bg-[var(--bg-surface-subtle)]",
    ghost:
      "bg-transparent text-[var(--text-primary)] hover:bg-[var(--bg-surface-subtle)]",
    danger:
      "bg-[var(--status-overdue-text)] text-white hover:opacity-90",
  };

  return (
    <button
      type={type}
      className={`${base} ${sizes} ${styles[variant]} ${className}`}
      {...props}
    />
  );
}

export function StatusBadge({ status }: { status: string }) {
  const s = (status || "").toUpperCase();
  let bg = "var(--status-muted-bg)";
  let color = "var(--status-muted-text)";

  if (["PAID", "POSTED", "APPROVED", "MATCHED", "OPEN", "BALANCED"].includes(s)) {
    bg = "var(--status-paid-bg)";
    color = "var(--status-paid-text)";
  } else if (["OVERDUE", "REJECTED", "VOID", "UNBALANCED"].includes(s)) {
    bg = "var(--status-overdue-bg)";
    color = "var(--status-overdue-text)";
  } else if (["PARTIALLY_PAID", "PENDING", "PENDING_APPROVAL", "SUBMITTED", "RETURNED"].includes(s)) {
    bg = "var(--status-warning-bg)";
    color = "var(--status-warning-text)";
  } else if (["ISSUED", "REVERSED", "CLOSED"].includes(s)) {
    bg = "var(--status-pending-bg)";
    color = "var(--status-pending-text)";
  }

  return (
    <span
      style={{ backgroundColor: bg, color }}
      className="inline-flex items-center px-2.5 py-0.5 rounded text-xs font-semibold tracking-tight whitespace-nowrap"
    >
      {s.replaceAll("_", " ")}
    </span>
  );
}

export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: string;
  actions?: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between pb-5 mb-6 border-b border-[var(--border-subtle)]">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-[var(--text-primary)]">
          {title}
        </h1>
        {description ? (
          <p className="mt-1 text-sm text-[var(--text-secondary)] max-w-2xl">
            {description}
          </p>
        ) : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  );
}

export function LoadingState({ label = "Loading records..." }: { label?: string }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex items-center gap-3 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] px-5 py-8 text-sm text-[var(--text-secondary)]"
    >
      <Loader2 className="h-4 w-4 animate-spin text-[var(--brand-forest)]" aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function ErrorState({
  message,
  onRetry,
}: {
  message: string;
  onRetry?: () => void;
}) {
  return (
    <div
      role="alert"
      className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 rounded-lg border border-[var(--status-overdue-text)] bg-[var(--status-overdue-bg)] px-5 py-4 text-sm text-[var(--status-overdue-text)]"
    >
      <div className="flex items-start gap-2.5">
        <AlertCircle className="h-4 w-4 mt-0.5 shrink-0" aria-hidden="true" />
        <span>{message}</span>
      </div>
      {onRetry ? (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          Try again
        </Button>
      ) : null}
    </div>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] px-6 py-10 text-center">
      <p className="text-base font-semibold text-[var(--text-primary)]">{title}</p>
      <p className="mt-1.5 text-sm text-[var(--text-secondary)] max-w-md mx-auto">
        {description}
      </p>
      {action ? <div className="mt-4 flex justify-center">{action}</div> : null}
    </div>
  );
}

export function TableRegion({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div tabIndex={0} role="region" aria-label={label} className="ledger-table-wrap">
      {children}
    </div>
  );
}

export function Field({
  id,
  label,
  error,
  hint,
  children,
}: {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-xs font-semibold text-[var(--text-secondary)]">
        {label}
      </label>
      {children}
      {hint && !error ? (
        <span className="text-xs text-[var(--text-secondary)]">{hint}</span>
      ) : null}
      {error ? (
        <span role="alert" className="text-xs font-medium text-[var(--status-overdue-text)]">
          {error}
        </span>
      ) : null}
    </div>
  );
}

export const inputClass =
  "w-full min-h-[42px] rounded-md border border-[var(--border-strong)] bg-[var(--bg-surface)] px-3 py-2 text-sm text-[var(--text-primary)] placeholder:text-[var(--text-secondary)]/70 focus:border-[var(--brand-forest)]";

export function Modal({
  open,
  onClose,
  title,
  children,
}: {
  open: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  if (!open) return null;

  return (
    <dialog
      ref={ref}
      aria-labelledby="ledger-modal-title"
      className="ledger-dialog"
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
    >
      <div className="flex items-center justify-between border-b border-[var(--border-subtle)] px-5 py-4">
        <h2 id="ledger-modal-title" className="text-base font-semibold text-[var(--text-primary)]">
          {title}
        </h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close dialog"
          className="inline-flex h-9 w-9 items-center justify-center rounded-md text-[var(--text-secondary)] hover:bg-[var(--bg-surface-subtle)]"
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      </div>
      <div className="overflow-y-auto p-5">{children}</div>
    </dialog>
  );
}
