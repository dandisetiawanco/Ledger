# Security Architecture

Ledger implements server-enforced authentication, authorization, multi-tenant isolation, CSRF protection, and upload hardening. Ledger is a portfolio application and has not undergone an independent third-party penetration test or security certification.

## Authentication & Sessions

- **Password Hashing**: `BCryptPasswordEncoder(12)` via Spring Security.
- **Session Storage**: `user_sessions` stores only the SHA-256 hash of a 256-bit `SecureRandom` session token. Raw tokens are never stored in the database or audit logs.
- **Cookies**: `LEDGER_SESSION` is configured `HttpOnly`, `SameSite=Lax`, configurable `Secure` (`LEDGER_SESSION_COOKIE_SECURE=true` in HTTPS production).
- **Session Fixation Protection**: Login and registration always mint a new session token.
- **Revocation**: Logout and `DELETE /api/v1/auth/sessions/{id}` mark `revoked_at`, immediately invalidating the session on the next request.
- **Rate Limiting**: `LoginRateLimiter` applies a sliding-window attempt limit per `(clientIp, email)`.

## Role-Based Access Control (RBAC)

| Role | Read Reports & Ledger | Create Invoices / Expenses / Payments | Post Journals / Close Periods | Approve Requests | Manage Org & Team |
|---|---|---|---|---|---|
| `OWNER` | Yes | Yes | Yes | Yes | Yes |
| `ADMIN` | Yes | Yes | Yes | Yes | Yes |
| `ACCOUNTANT` | Yes | Yes | Yes | Yes | No |
| `FINANCE_OFFICER` | Yes | Yes | No | No | No |
| `APPROVER` | Yes | No | No | Yes | No |
| `VIEWER` | Yes | No | No | No | No |

When `enforceSeparationOfDuties` is enabled, a requester cannot approve their own approval request regardless of role.

## Tenant & Demo Isolation

- `SessionAuthenticationFilter` verifies active `organization_memberships` for every request before populating `LedgerPrincipal`.
- Spoofing `X-Organization-Id` for an organization where the user lacks membership yields an unset organization context and fails downstream authorization checks.
- Users flagged `is_demo_user = true` are strictly blocked from accessing organizations where `is_demo = false`.

## Upload & Document Protection

- `AttachmentService` enforces allowed MIME types (`application/pdf`, `image/png`, `image/jpeg`, `image/webp`, `text/csv`, `text/plain`), 10 MB size limits, sanitized filenames, and canonical `Path.normalize()` prefix checks to prevent path traversal.
- Uploaded files are stored outside public static web directories and downloaded strictly through `GET /api/v1/attachments/{id}/download` with organization ownership checks and `X-Content-Type-Options: nosniff`.
