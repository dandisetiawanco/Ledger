import Link from "next/link";
import { PublicShell } from "@/components/PublicShell";

export const metadata = { title: "Security" };

export default function SecurityPage() {
  return (
    <PublicShell>
      <div className="mx-auto max-w-5xl px-4 py-14 sm:px-6 space-y-10">
        <div className="max-w-2xl">
          <h1 className="font-editorial text-4xl font-semibold text-[var(--text-primary)]">
            Security architecture and boundaries
          </h1>
          <p className="mt-3 text-base text-[var(--text-secondary)] leading-relaxed">
            Ledger implements practical application security controls tested through automated
            Spring Security integration tests. This project is a portfolio engineering demonstration
            and makes no claim of third-party security certification or regulatory accreditation.
          </p>
        </div>

        <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
          {[
            {
              title: "Session Authentication & CSRF",
              body: "Passwords are hashed with BCrypt (cost factor 12). Login rotates a 256-bit random session token stored hashed (SHA-256) in user_sessions and delivered via an HttpOnly, SameSite=Lax cookie. State-changing endpoints enforce CookieCsrfTokenRepository X-XSRF-TOKEN validation.",
            },
            {
              title: "Multi-Tenant Isolation & RBAC",
              body: "Six server-enforced roles (Owner, Admin, Accountant, Finance Officer, Approver, Viewer). Every query scopes strictly by organizationId, and demo users are isolated from accessing real organizations or private attachments.",
            },
            {
              title: "Concurrency & Idempotency",
              body: "Payment creation accepts Idempotency-Key headers backed by database uniqueness constraints. Invoice numbering, payment allocation, period closing, and bank reconciliation use transactional locks and optimistic locking (@Version).",
            },
            {
              title: "Document Storage & Audit Trail",
              body: "Uploads validate MIME type, file size, and canonical path boundaries to block path traversal, and download only through organization-authorized endpoints. Financial mutations append immutable entries to audit_events without recording secrets.",
            },
          ].map((block) => (
            <div
              key={block.title}
              className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6"
            >
              <h2 className="text-base font-semibold text-[var(--text-primary)]">{block.title}</h2>
              <p className="mt-2 text-sm text-[var(--text-secondary)] leading-relaxed">{block.body}</p>
            </div>
          ))}
        </div>

        <Link
          href="/documentation"
          className="inline-flex min-h-[44px] items-center rounded-md border border-[var(--border-strong)] bg-[var(--bg-surface)] px-5 text-sm font-medium text-[var(--text-primary)] hover:bg-[var(--bg-surface-subtle)]"
        >
          View documentation and threat model overview
        </Link>
      </div>
    </PublicShell>
  );
}
