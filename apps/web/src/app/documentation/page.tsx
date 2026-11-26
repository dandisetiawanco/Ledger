import Link from "next/link";
import { PublicShell } from "@/components/PublicShell";

export const metadata = { title: "Documentation" };

const DOC_TOPICS = [
  { file: "docs/ARCHITECTURE.md", title: "System & Modular Monolith Architecture", desc: "Domain boundaries across 17 Spring Boot modules, transaction design, and Next.js integration." },
  { file: "docs/ACCOUNTING_ENGINE.md", title: "Double-Entry Accounting Engine", desc: "Journal lifecycles, normal balances, reversals, period locks, and automated business postings." },
  { file: "docs/FINANCIAL_INVARIANTS.md", title: "Financial Invariants & BigDecimal Rules", desc: "Precision, currency-specific HALF_UP rounding, FX realization, and subledger reconciliation." },
  { file: "docs/DATABASE.md", title: "Normalized Database & Flyway Migrations", desc: "34-table relational schema, constraints, indexes, optimistic locking, and H2 vs PostgreSQL profiles." },
  { file: "docs/API.md", title: "Versioned REST API & OpenAPI", desc: "Endpoint conventions, RFC 9457 ProblemDetail errors, pagination, CSRF, and Idempotency-Key usage." },
  { file: "docs/SECURITY.md", title: "Security & Threat Model", desc: "Session management, RBAC matrix, tenant isolation, file validation, and documented risk mitigations." },
  { file: "docs/TESTING.md", title: "Automated Testing Strategy", desc: "JUnit 5 accounting invariant tests, MockMvc security tests, PostgreSQL Testcontainers, and Vitest." },
  { file: "docs/DEVELOPMENT.md", title: "Local Setup & Deployment", desc: "Maven Wrapper, Makefile commands, lightweight H2 profile, PostgreSQL Docker Compose, and CI/CD." },
];

export default function DocumentationPage() {
  return (
    <PublicShell>
      <div className="mx-auto max-w-5xl px-4 py-14 sm:px-6 space-y-10">
        <div className="max-w-2xl">
          <h1 className="font-editorial text-4xl font-semibold text-[var(--text-primary)]">
            Engineering documentation
          </h1>
          <p className="mt-3 text-base text-[var(--text-secondary)] leading-relaxed">
            Explore the repository documentation, OpenAPI specification, and architectural notes
            covering Java fundamentals, Spring transactions, relational integrity, and accounting
            workflows.
          </p>
        </div>

        <div className="flex flex-wrap gap-3">
          <a
            href="/swagger-ui/index.html"
            target="_blank"
            rel="noreferrer"
            className="inline-flex min-h-[44px] items-center rounded-md bg-[var(--brand-forest)] px-5 text-sm font-medium text-[var(--text-on-brand)] hover:bg-[var(--brand-forest-hover)]"
          >
            Open Swagger / OpenAPI UI
          </a>
          <a
            href="/v3/api-docs"
            target="_blank"
            rel="noreferrer"
            className="inline-flex min-h-[44px] items-center rounded-md border border-[var(--border-strong)] bg-[var(--bg-surface)] px-5 text-sm font-medium text-[var(--text-primary)] hover:bg-[var(--bg-surface-subtle)]"
          >
            View OpenAPI JSON
          </a>
          <Link
            href="/demo"
            className="inline-flex min-h-[44px] items-center rounded-md border border-[var(--border-strong)] bg-[var(--bg-surface)] px-5 text-sm font-medium text-[var(--text-primary)] hover:bg-[var(--bg-surface-subtle)]"
          >
            Try the live demo
          </Link>
        </div>

        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {DOC_TOPICS.map((doc) => (
            <div
              key={doc.file}
              className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-5"
            >
              <p className="font-mono-code text-xs text-[var(--brand-forest)]">{doc.file}</p>
              <h2 className="mt-1.5 text-base font-semibold text-[var(--text-primary)]">{doc.title}</h2>
              <p className="mt-1.5 text-sm text-[var(--text-secondary)]">{doc.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </PublicShell>
  );
}
