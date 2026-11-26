"use client";

import React, { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { canApprove, formatMoney } from "@ledger/contracts";
import { apiFetch, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/providers";
import {
  Button,
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  StatusBadge,
  TableRegion,
} from "@/components/ui";

interface ApprovalItem {
  id: string;
  entityType: string;
  entityId: string;
  entityReference: string;
  amount: string;
  currencyCode: string;
  status: string;
  requesterId: string;
  requesterName: string;
  approverName: string | null;
  decisionReason: string | null;
  requestedAt: string;
}

export default function ApprovalsPage() {
  const { auth } = useAuth();
  const queryClient = useQueryClient();
  const canDecide = canApprove(auth?.activeOrganization?.role);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const { data: approvals = [], isLoading, error, refetch } = useQuery<ApprovalItem[]>({
    queryKey: ["approvals"],
    queryFn: () => apiFetch<ApprovalItem[]>("/api/v1/approvals"),
  });

  async function decide(id: string, decision: "APPROVED" | "REJECTED" | "RETURNED") {
    setBusyId(id);
    setActionError(null);
    try {
      await apiFetch(`/api/v1/approvals/${id}/decide`, {
        method: "POST",
        body: JSON.stringify({
          decision,
          reason: decision === "APPROVED" ? "Reviewed and approved" : `Marked ${decision.toLowerCase()} in approval queue`,
        }),
      });
      await queryClient.invalidateQueries({ queryKey: ["approvals"] });
      await queryClient.invalidateQueries({ queryKey: ["expenses"] });
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Could not record decision");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div>
      <PageHeader
        title="Approval Queue"
        description="Review submitted expenses, supplier bills, and journals. When separation of duties is enabled, requesters cannot approve their own submissions."
      />

      {actionError ? <div className="mb-4"><ErrorState message={actionError} /></div> : null}
      {isLoading ? <LoadingState label="Loading approval requests..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Unable to load approvals"}
          onRetry={() => refetch()}
        />
      ) : null}

      {!isLoading && !error ? (
        approvals.length === 0 ? (
          <EmptyState
            title="No approval requests"
            description="Expenses or journals exceeding your approval threshold will appear here when submitted."
          />
        ) : (
          <TableRegion label="Approval requests">
            <table className="ledger-table">
              <thead>
                <tr>
                  <th>Document</th>
                  <th>Type</th>
                  <th>Requester</th>
                  <th className="!text-right">Amount</th>
                  <th>Status</th>
                  <th>Approver / Reason</th>
                  <th>Decision</th>
                </tr>
              </thead>
              <tbody>
                {approvals.map((a) => (
                  <tr key={a.id}>
                    <td className="font-mono-code text-xs font-semibold">{a.entityReference}</td>
                    <td><StatusBadge status={a.entityType} /></td>
                    <td className="text-sm">{a.requesterName}</td>
                    <td className="text-right num font-semibold whitespace-nowrap">
                      {formatMoney(a.amount, a.currencyCode)}
                    </td>
                    <td><StatusBadge status={a.status} /></td>
                    <td className="text-xs text-[var(--text-secondary)]">
                      {a.approverName ? `${a.approverName}: ${a.decisionReason || ""}` : "Awaiting reviewer"}
                    </td>
                    <td className="whitespace-nowrap">
                      {a.status === "PENDING" && canDecide ? (
                        <div className="flex gap-1.5">
                          <Button size="sm" disabled={busyId === a.id} onClick={() => decide(a.id, "APPROVED")}>
                            Approve
                          </Button>
                          <Button variant="secondary" size="sm" disabled={busyId === a.id} onClick={() => decide(a.id, "RETURNED")}>
                            Return
                          </Button>
                          <Button variant="danger" size="sm" disabled={busyId === a.id} onClick={() => decide(a.id, "REJECTED")}>
                            Reject
                          </Button>
                        </div>
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableRegion>
        )
      ) : null}
    </div>
  );
}
