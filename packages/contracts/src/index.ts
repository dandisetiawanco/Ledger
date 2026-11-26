import Decimal from "decimal.js";

export type OrgRole =
  | "OWNER"
  | "ADMIN"
  | "ACCOUNTANT"
  | "FINANCE_OFFICER"
  | "APPROVER"
  | "VIEWER";

export type AccountType = "ASSET" | "LIABILITY" | "EQUITY" | "REVENUE" | "EXPENSE";
export type NormalBalance = "DEBIT" | "CREDIT";

export interface UserSummary {
  id: string;
  email: string;
  fullName: string;
  demoUser: boolean;
}

export interface OrgMembershipSummary {
  organizationId: string;
  name: string;
  slug: string;
  baseCurrency: string;
  timeZone: string;
  locale: string;
  role: OrgRole;
  demo: boolean;
  onboardingCompleted: boolean;
}

export interface AuthResponse {
  user: UserSummary;
  activeOrganization: OrgMembershipSummary | null;
  organizations: OrgMembershipSummary[];
}

export interface InvoiceLinePreviewInput {
  quantity: string | number;
  unitPrice: string | number;
  discountRate?: string | number;
  taxRate?: string | number;
}

export interface InvoiceCalculationPreview {
  subtotal: string;
  discountTotal: string;
  taxTotal: string;
  grandTotal: string;
  lines: Array<{
    gross: string;
    discountAmount: string;
    netAmount: string;
    taxAmount: string;
    lineTotal: string;
  }>;
}

const ZERO_MINOR_CURRENCIES = new Set(["IDR", "JPY"]);

export function minorUnits(currencyCode: string): number {
  return ZERO_MINOR_CURRENCIES.has((currencyCode || "IDR").toUpperCase()) ? 0 : 2;
}

export function roundMoney(value: Decimal.Value, currencyCode: string): Decimal {
  const minor = minorUnits(currencyCode);
  return new Decimal(value || 0).toDecimalPlaces(minor, Decimal.ROUND_HALF_UP);
}

/**
 * Client-side preview calculation using arbitrary-precision Decimal arithmetic.
 * Backend recalculates and remains the authoritative source of financial truth.
 */
export function calculateInvoicePreview(
  lines: InvoiceLinePreviewInput[],
  currencyCode: string,
  taxInclusive: boolean
): InvoiceCalculationPreview {
  let subtotal = new Decimal(0);
  let discountTotal = new Decimal(0);
  let taxTotal = new Decimal(0);
  let grandTotal = new Decimal(0);

  const computedLines = lines.map((line) => {
    const qty = safeDecimal(line.quantity);
    const price = safeDecimal(line.unitPrice);
    const discRate = safeDecimal(line.discountRate ?? 0);
    const taxRate = safeDecimal(line.taxRate ?? 0);

    const gross = roundMoney(qty.mul(price), currencyCode);
    const discountAmount = roundMoney(gross.mul(discRate).div(100), currencyCode);
    const afterDiscount = roundMoney(gross.sub(discountAmount), currencyCode);

    let netAmount: Decimal;
    let taxAmount: Decimal;
    let lineTotal: Decimal;

    if (taxInclusive && taxRate.gt(0)) {
      netAmount = roundMoney(afterDiscount.mul(100).div(taxRate.add(100)), currencyCode);
      taxAmount = roundMoney(afterDiscount.sub(netAmount), currencyCode);
      lineTotal = afterDiscount;
    } else {
      netAmount = afterDiscount;
      taxAmount = roundMoney(netAmount.mul(taxRate).div(100), currencyCode);
      lineTotal = roundMoney(netAmount.add(taxAmount), currencyCode);
    }

    subtotal = subtotal.add(gross);
    discountTotal = discountTotal.add(discountAmount);
    taxTotal = taxTotal.add(taxAmount);
    grandTotal = grandTotal.add(lineTotal);

    return {
      gross: gross.toFixed(2),
      discountAmount: discountAmount.toFixed(2),
      netAmount: netAmount.toFixed(2),
      taxAmount: taxAmount.toFixed(2),
      lineTotal: lineTotal.toFixed(2),
    };
  });

  return {
    subtotal: subtotal.toFixed(2),
    discountTotal: discountTotal.toFixed(2),
    taxTotal: taxTotal.toFixed(2),
    grandTotal: grandTotal.toFixed(2),
    lines: computedLines,
  };
}

export function formatMoney(
  amount: string | number | Decimal | null | undefined,
  currencyCode = "IDR",
  locale = "en-US"
): string {
  const code = (currencyCode || "IDR").toUpperCase();
  const dec = safeDecimal(amount ?? 0);
  const digits = minorUnits(code);
  const isNeg = dec.isNegative();
  const absStr = dec.abs().toFixed(digits);
  const [intPart, fracPart] = absStr.split(".");
  const grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, locale === "id-ID" ? "." : ",");
  const formattedNumber = fracPart !== undefined ? `${grouped}${locale === "id-ID" ? "," : "."}${fracPart}` : grouped;
  const prefix = code === "IDR" ? "IDR " : code === "USD" ? "USD " : `${code} `;
  return `${isNeg ? "-" : ""}${prefix}${formattedNumber}`;
}

export function safeDecimal(val: Decimal.Value | null | undefined): Decimal {
  if (val === null || val === undefined || val === "") return new Decimal(0);
  try {
    const d = new Decimal(val);
    return d.isFinite() ? d : new Decimal(0);
  } catch {
    return new Decimal(0);
  }
}

export function canWriteAccounting(role?: OrgRole | null): boolean {
  return role === "OWNER" || role === "ADMIN" || role === "ACCOUNTANT";
}

export function canOperateFinance(role?: OrgRole | null): boolean {
  return role === "OWNER" || role === "ADMIN" || role === "ACCOUNTANT" || role === "FINANCE_OFFICER";
}

export function canApprove(role?: OrgRole | null): boolean {
  return role === "OWNER" || role === "ADMIN" || role === "APPROVER" || role === "ACCOUNTANT";
}

export function canManageOrg(role?: OrgRole | null): boolean {
  return role === "OWNER" || role === "ADMIN";
}
