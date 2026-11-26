"use client";

import React, { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { canManageOrg, type OrgRole } from "@ledger/contracts";
import { apiFetch, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/providers";
import {
  Button,
  ErrorState,
  Field,
  LoadingState,
  Modal,
  PageHeader,
  StatusBadge,
  TableRegion,
  inputClass,
} from "@/components/ui";

interface MemberItem {
  membershipId: string;
  userId: string;
  email: string;
  fullName: string;
  role: OrgRole;
  active: boolean;
  joinedAt: string;
}

const ROLES: OrgRole[] = ["OWNER", "ADMIN", "ACCOUNTANT", "FINANCE_OFFICER", "APPROVER", "VIEWER"];

export default function TeamPage() {
  const { auth } = useAuth();
  const queryClient = useQueryClient();
  const isAdmin = canManageOrg(auth?.activeOrganization?.role);
  const isDemo = Boolean(auth?.activeOrganization?.demo);

  const [modalOpen, setModalOpen] = useState(false);
  const [email, setEmail] = useState("");
  const [fullName, setFullName] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<OrgRole>("ACCOUNTANT");
  const [errMsg, setErrMsg] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const { data: members = [], isLoading, error, refetch } = useQuery<MemberItem[]>({
    queryKey: ["org-members"],
    queryFn: () => apiFetch<MemberItem[]>("/api/v1/organizations/current/members"),
  });

  async function addMember(e: React.FormEvent) {
    e.preventDefault();
    setErrMsg(null);
    setSaving(true);
    try {
      await apiFetch("/api/v1/organizations/current/members", {
        method: "POST",
        body: JSON.stringify({ email, fullName, temporaryPassword: password, role }),
      });
      await queryClient.invalidateQueries({ queryKey: ["org-members"] });
      setModalOpen(false);
      setEmail("");
      setFullName("");
      setPassword("");
    } catch (err) {
      setErrMsg(err instanceof ApiError ? err.message : "Unable to add member");
    } finally {
      setSaving(false);
    }
  }

  async function updateRole(membershipId: string, nextRole: OrgRole) {
    setErrMsg(null);
    try {
      await apiFetch(`/api/v1/organizations/current/members/${membershipId}`, {
        method: "PATCH",
        body: JSON.stringify({ role: nextRole }),
      });
      await queryClient.invalidateQueries({ queryKey: ["org-members"] });
    } catch (err) {
      setErrMsg(err instanceof ApiError ? err.message : "Could not update role");
    }
  }

  return (
    <div>
      <PageHeader
        title="Team & Role Permissions"
        description="Server-enforced RBAC roles. Viewers have strict read-only access and cannot create, post, approve, or reverse financial entries."
        actions={
          isAdmin && !isDemo ? <Button onClick={() => setModalOpen(true)}>Add team member</Button> : null
        }
      />

      {errMsg ? <div className="mb-4"><ErrorState message={errMsg} /></div> : null}
      {isLoading ? <LoadingState label="Loading organization members..." /> : null}
      {error ? (
        <ErrorState message={error instanceof Error ? error.message : "Unable to load team"} onRetry={() => refetch()} />
      ) : null}

      {!isLoading && !error ? (
        <TableRegion label="Organization members">
          <table className="ledger-table">
            <thead>
              <tr>
                <th>Member</th>
                <th>Email</th>
                <th>Role</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {members.map((m) => (
                <tr key={m.membershipId}>
                  <td className="font-semibold">{m.fullName}</td>
                  <td className="text-sm text-[var(--text-secondary)]">{m.email}</td>
                  <td>
                    {isAdmin && !isDemo ? (
                      <select
                        aria-label={`Role for ${m.fullName}`}
                        value={m.role}
                        onChange={(e) => updateRole(m.membershipId, e.target.value as OrgRole)}
                        className={`${inputClass} !w-48 !min-h-[36px] text-xs`}
                      >
                        {ROLES.map((r) => (
                          <option key={r} value={r}>{r.replaceAll("_", " ")}</option>
                        ))}
                      </select>
                    ) : (
                      <StatusBadge status={m.role} />
                    )}
                  </td>
                  <td><StatusBadge status={m.active ? "OPEN" : "VOID"} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </TableRegion>
      ) : null}

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Add organization member">
        <form onSubmit={addMember} className="space-y-4">
          <Field id="tm-name" label="Full name">
            <input id="tm-name" required value={fullName} onChange={(e) => setFullName(e.target.value)} placeholder="Member full name" className={inputClass} />
          </Field>
          <Field id="tm-email" label="Email address">
            <input id="tm-email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} placeholder="email@example.com" className={inputClass} />
          </Field>
          <Field id="tm-pass" label="Initial password (min 8 chars)">
            <input id="tm-pass" type="password" minLength={8} required value={password} onChange={(e) => setPassword(e.target.value)} className={inputClass} />
          </Field>
          <Field id="tm-role" label="Role">
            <select id="tm-role" value={role} onChange={(e) => setRole(e.target.value as OrgRole)} className={inputClass}>
              {ROLES.map((r) => (
                <option key={r} value={r}>{r.replaceAll("_", " ")}</option>
              ))}
            </select>
          </Field>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button type="submit" disabled={saving}>{saving ? "Adding..." : "Add member"}</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
