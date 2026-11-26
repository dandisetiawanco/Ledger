"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { canOperateFinance, formatMoney } from "@ledger/contracts";
import { apiFetch, ApiError } from "@/lib/api";
import { useAuth } from "@/lib/providers";
import {
  Button,
  EmptyState,
  ErrorState,
  Field,
  LoadingState,
  PageHeader,
  StatusBadge,
  TableRegion,
  inputClass,
} from "@/components/ui";

interface BankDetail {
  bankAccount: {
    id: string;
    name: string;
    institutionName: string;
    currencyCode: string;
    ledgerBalance: string;
    statementBalance: string;
    reconciliationDifference: string;
  };
  imports: Array<{
    id: string;
    fileName: string;
    totalRows: number;
    importedRows: number;
    duplicateRows: number;
    importedAt: string;
  }>;
  statementLines: Array<{
    id: string;
    transactionDate: string;
    description: string;
    reference: string | null;
    amount: string;
    reconciled: boolean;
  }>;
}

interface PreviewResp {
  rows: Array<{
    rowNumber: number;
    transactionDate: string;
    description: string;
    reference: string;
    amount: string;
    duplicate: boolean;
  }>;
  errors: Array<{ rowNumber: number; message: string }>;
}

export default function BankAccountDetailPage() {
  const params = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const { auth } = useAuth();
  const canImport = canOperateFinance(auth?.activeOrganization?.role);

  const [csvContent, setCsvContent] = useState("");
  const [fileName, setFileName] = useState("statement.csv");
  const [dateColumn, setDateColumn] = useState("Date");
  const [descriptionColumn, setDescriptionColumn] = useState("Description");
  const [referenceColumn, setReferenceColumn] = useState("Reference");
  const [debitColumn, setDebitColumn] = useState("Debit");
  const [creditColumn, setCreditColumn] = useState("Credit");
  const [preview, setPreview] = useState<PreviewResp | null>(null);
  const [importMsg, setImportMsg] = useState<string | null>(null);
  const [importErr, setImportErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const { data, isLoading, error, refetch } = useQuery<BankDetail>({
    queryKey: ["bank-account", params.id],
    queryFn: () => apiFetch<BankDetail>(`/api/v1/banking/accounts/${params.id}`),
  });

  function handleFileRead(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0];
    if (!f) return;
    setFileName(f.name);
    const reader = new FileReader();
    reader.onload = () => setCsvContent(String(reader.result || ""));
    reader.readAsText(f);
  }

  async function runPreview() {
    setImportErr(null);
    setImportMsg(null);
    setBusy(true);
    try {
      const res = await apiFetch<PreviewResp>(`/api/v1/banking/accounts/${params.id}/import/preview`, {
        method: "POST",
        body: JSON.stringify({
          fileName,
          csvContent,
          dateColumn,
          descriptionColumn,
          referenceColumn,
          debitColumn,
          creditColumn,
          dateFormat: "yyyy-MM-dd",
        }),
      });
      setPreview(res);
    } catch (err) {
      setImportErr(err instanceof ApiError ? err.message : "Preview failed");
    } finally {
      setBusy(false);
    }
  }

  async function commitImport() {
    setImportErr(null);
    setBusy(true);
    try {
      const res = await apiFetch<{ importedRows: number; duplicateRows: number }>(
        `/api/v1/banking/accounts/${params.id}/import/commit`,
        {
          method: "POST",
          body: JSON.stringify({
            fileName,
            csvContent,
            dateColumn,
            descriptionColumn,
            referenceColumn,
            debitColumn,
            creditColumn,
            dateFormat: "yyyy-MM-dd",
          }),
        }
      );
      setImportMsg(`Imported ${res.importedRows} new line(s); skipped ${res.duplicateRows} duplicate(s).`);
      setPreview(null);
      setCsvContent("");
      await queryClient.invalidateQueries({ queryKey: ["bank-account", params.id] });
    } catch (err) {
      setImportErr(err instanceof ApiError ? err.message : "Commit failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={data ? data.bankAccount.name : "Bank account"}
        description={data ? `${data.bankAccount.institutionName} · Manual CSV statement import & audit` : undefined}
        actions={
          <Link
            href={`/workspace/reconciliation?bankAccountId=${params.id}`}
            className="inline-flex min-h-[42px] items-center rounded-md bg-[var(--brand-forest)] px-4 text-sm font-medium text-[var(--text-on-brand)] hover:bg-[var(--brand-forest-hover)]"
          >
            Open reconciliation workspace
          </Link>
        }
      />

      {isLoading ? <LoadingState label="Loading bank statements..." /> : null}
      {error ? (
        <ErrorState
          message={error instanceof Error ? error.message : "Unable to load bank account"}
          onRetry={() => refetch()}
        />
      ) : null}

      {data ? (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-5 text-sm">
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Ledger balance</p>
              <p className="mt-1 text-lg font-semibold num">
                {formatMoney(data.bankAccount.ledgerBalance, data.bankAccount.currencyCode)}
              </p>
            </div>
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Imported statement balance</p>
              <p className="mt-1 text-lg font-semibold num">
                {formatMoney(data.bankAccount.statementBalance, data.bankAccount.currencyCode)}
              </p>
            </div>
            <div>
              <p className="text-xs text-[var(--text-secondary)]">Difference (Ledger minus Statement)</p>
              <p className="mt-1 text-lg font-semibold num">
                {formatMoney(data.bankAccount.reconciliationDifference, data.bankAccount.currencyCode)}
              </p>
            </div>
          </div>

          {canImport ? (
            <section className="rounded-lg border border-[var(--border-subtle)] bg-[var(--bg-surface)] p-6 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <h2 className="text-base font-semibold">Import CSV bank statement</h2>
                <label className="inline-flex min-h-[38px] items-center rounded-md border border-[var(--border-strong)] px-3 text-xs font-semibold hover:bg-[var(--bg-surface-subtle)]">
                  <span>Load CSV file</span>
                  <input type="file" accept=".csv,text/csv" onChange={handleFileRead} className="sr-only" />
                </label>
              </div>

              {importErr ? <ErrorState message={importErr} /> : null}
              {importMsg ? (
                <div className="rounded-md bg-[var(--status-paid-bg)] px-4 py-3 text-sm text-[var(--status-paid-text)]">
                  {importMsg}
                </div>
              ) : null}

              <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
                <Field id="col-date" label="Date col"><input id="col-date" value={dateColumn} onChange={(e) => setDateColumn(e.target.value)} className={inputClass} /></Field>
                <Field id="col-desc" label="Description col"><input id="col-desc" value={descriptionColumn} onChange={(e) => setDescriptionColumn(e.target.value)} className={inputClass} /></Field>
                <Field id="col-ref" label="Reference col"><input id="col-ref" value={referenceColumn} onChange={(e) => setReferenceColumn(e.target.value)} className={inputClass} /></Field>
                <Field id="col-dr" label="Debit col"><input id="col-dr" value={debitColumn} onChange={(e) => setDebitColumn(e.target.value)} className={inputClass} /></Field>
                <Field id="col-cr" label="Credit col"><input id="col-cr" value={creditColumn} onChange={(e) => setCreditColumn(e.target.value)} className={inputClass} /></Field>
              </div>

              <Field id="csv-raw" label="CSV content (paste or load file)">
                <textarea
                  id="csv-raw"
                  rows={4}
                  value={csvContent}
                  onChange={(e) => setCsvContent(e.target.value)}
                  placeholder="Date,Description,Reference,Debit,Credit"
                  className={`${inputClass} font-mono-code text-xs`}
                />
              </Field>

              <div className="flex flex-wrap gap-2">
                <Button variant="secondary" disabled={busy || !csvContent.trim()} onClick={runPreview}>
                  Validate &amp; preview rows
                </Button>
                {preview && preview.errors.length === 0 ? (
                  <Button disabled={busy} onClick={commitImport}>
                    Commit {preview.rows.filter((r) => !r.duplicate).length} new row(s)
                  </Button>
                ) : null}
              </div>

              {preview ? (
                <div className="space-y-3 pt-2">
                  {preview.errors.length > 0 ? (
                    <div role="alert" className="rounded-md bg-[var(--status-overdue-bg)] p-3 text-xs text-[var(--status-overdue-text)] space-y-1">
                      {preview.errors.map((er) => (
                        <p key={`${er.rowNumber}-${er.message}`}>Row {er.rowNumber}: {er.message}</p>
                      ))}
                    </div>
                  ) : null}
                  <TableRegion label="CSV preview rows">
                    <table className="ledger-table">
                      <thead>
                        <tr>
                          <th>Row</th>
                          <th>Date</th>
                          <th>Description</th>
                          <th>Ref</th>
                          <th className="!text-right">Signed Amount</th>
                          <th>Duplicate Check</th>
                        </tr>
                      </thead>
                      <tbody>
                        {preview.rows.map((r) => (
                          <tr key={r.rowNumber}>
                            <td className="num">{r.rowNumber}</td>
                            <td className="num">{r.transactionDate}</td>
                            <td>{r.description}</td>
                            <td className="font-mono-code text-xs">{r.reference}</td>
                            <td className="text-right num font-semibold">{formatMoney(r.amount, data.bankAccount.currencyCode)}</td>
                            <td><StatusBadge status={r.duplicate ? "DUPLICATE" : "NEW"} /></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </TableRegion>
                </div>
              ) : null}
            </section>
          ) : null}

          <section className="space-y-3">
            <h2 className="text-base font-semibold">Imported statement lines</h2>
            {data.statementLines.length === 0 ? (
              <EmptyState title="No statement lines imported" description="Upload a CSV statement above to begin reconciliation." />
            ) : (
              <TableRegion label="Imported bank statement lines">
                <table className="ledger-table">
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Description</th>
                      <th>Reference</th>
                      <th className="!text-right">Amount</th>
                      <th>Reconciliation</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.statementLines.map((l) => (
                      <tr key={l.id}>
                        <td className="num whitespace-nowrap">{l.transactionDate}</td>
                        <td className="font-medium">{l.description}</td>
                        <td className="font-mono-code text-xs text-[var(--text-secondary)]">{l.reference || ""}</td>
                        <td className="text-right num font-semibold whitespace-nowrap">
                          {formatMoney(l.amount, data.bankAccount.currencyCode)}
                        </td>
                        <td><StatusBadge status={l.reconciled ? "MATCHED" : "UNMATCHED"} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </TableRegion>
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}
