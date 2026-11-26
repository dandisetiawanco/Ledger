import React from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import {
  calculateInvoicePreview,
  canApprove,
  canOperateFinance,
  canWriteAccounting,
  formatMoney,
} from "@ledger/contracts";
import { EmptyState, ErrorState, LoadingState, StatusBadge } from "@/components/ui";

describe("Financial Decimal Arithmetic & Rounding", () => {
  it("computes tax-exclusive invoices with discounts accurately without floating-point drift", () => {
    const res = calculateInvoicePreview(
      [{ quantity: "2", unitPrice: "5000000", discountRate: "10", taxRate: "11" }],
      "IDR",
      false
    );
    expect(res.subtotal).toBe("10000000.00");
    expect(res.discountTotal).toBe("1000000.00");
    expect(res.taxTotal).toBe("990000.00");
    expect(res.grandTotal).toBe("9990000.00");
  });

  it("extracts tax accurately for tax-inclusive pricing", () => {
    const res = calculateInvoicePreview(
      [{ quantity: "1", unitPrice: "11100000", discountRate: "0", taxRate: "11" }],
      "IDR",
      true
    );
    expect(res.taxTotal).toBe("1100000.00");
    expect(res.grandTotal).toBe("11100000.00");
    expect(res.lines[0].netAmount).toBe("10000000.00");
  });

  it("preserves exact 2-decimal precision for USD calculations (0.1 + 0.2 trap)", () => {
    const res = calculateInvoicePreview(
      [
        { quantity: "1", unitPrice: "0.10", discountRate: "0", taxRate: "0" },
        { quantity: "1", unitPrice: "0.20", discountRate: "0", taxRate: "0" },
      ],
      "USD",
      false
    );
    expect(res.grandTotal).toBe("0.30");
    expect(formatMoney(res.grandTotal, "USD")).toBe("USD 0.30");
  });
});

describe("RBAC Permission Helpers", () => {
  it("blocks Viewer from accounting, finance operations, and approvals", () => {
    expect(canWriteAccounting("VIEWER")).toBe(false);
    expect(canOperateFinance("VIEWER")).toBe(false);
    expect(canApprove("VIEWER")).toBe(false);
    expect(canWriteAccounting("ACCOUNTANT")).toBe(true);
    expect(canApprove("APPROVER")).toBe(true);
  });
});

describe("Accessible UI States & Financial Badges", () => {
  it("renders loading, empty, error, and status controls with clear text", () => {
    const onRetry = vi.fn();
    render(
      <div>
        <LoadingState label="Loading ledger balances..." />
        <EmptyState title="No invoices" description="Create your first invoice." />
        <ErrorState message="Period is closed" onRetry={onRetry} />
        <StatusBadge status="PARTIALLY_PAID" />
      </div>
    );
    expect(screen.getByRole("status")).toHaveTextContent("Loading ledger balances...");
    expect(screen.getByText("No invoices")).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent("Period is closed");
    expect(screen.getByText("PARTIALLY PAID")).toBeInTheDocument();
  });
});
