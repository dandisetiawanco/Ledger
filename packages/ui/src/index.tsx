import React from "react";

export function LedgerBrandMark({
  className = "h-8 w-8",
  variant = "forest",
}: {
  className?: string;
  variant?: "forest" | "dark" | "mono";
}) {
  const bg = variant === "dark" ? "#19251F" : variant === "mono" ? "none" : "#183D30";
  const stroke = variant === "dark" ? "#B9D9B9" : variant === "mono" ? "currentColor" : "none";
  const primaryBar = variant === "mono" ? "currentColor" : "#F6F4EE";
  const accentBar = variant === "dark" ? "#B9D9B9" : variant === "mono" ? "currentColor" : "#D6E8D4";

  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 40 40"
      width={32}
      height={32}
      fill="none"
      className={className}
      aria-hidden="true"
    >
      <rect
        x="2"
        y="2"
        width="36"
        height="36"
        rx="7"
        fill={bg}
        stroke={stroke}
        strokeWidth={variant === "forest" ? 0 : 2}
      />
      <rect x="10.5" y="10" width="4.5" height="20" rx="1" fill={primaryBar} />
      <rect x="25" y="10" width="4.5" height="12.5" rx="1" fill={accentBar} />
      <rect x="10.5" y="25.5" width="19" height="4.5" rx="1" fill={primaryBar} />
      <rect x="18" y="17.5" width="4" height="4" rx="1" fill={accentBar} />
    </svg>
  );
}
