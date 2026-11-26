import type { Metadata } from "next";
import { Newsreader, Plus_Jakarta_Sans, IBM_Plex_Mono } from "next/font/google";
import "./globals.css";
import { AppProviders } from "@/lib/providers";

const newsreader = Newsreader({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-serif",
  display: "swap",
});

const jakarta = Plus_Jakarta_Sans({
  subsets: ["latin"],
  weight: ["400", "500", "600", "700"],
  variable: "--font-sans",
  display: "swap",
});

const plexMono = IBM_Plex_Mono({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  variable: "--font-mono",
  display: "swap",
});

export const metadata: Metadata = {
  title: {
    default: "Ledger | Clarity in every transaction.",
    template: "%s | Ledger",
  },
  description:
    "Manage invoices, track expenses, reconcile bank statements, and understand your business finances through one connected double-entry accounting workspace.",
  icons: {
    icon: "/favicon.svg",
  },
};

const themeInitScript = `
try {
  var saved = localStorage.getItem("ledger-color-scheme");
  var meta = document.querySelector('meta[name="color-scheme"]');
  if (saved === "light" || saved === "dark") {
    if (meta) meta.content = saved;
    document.documentElement.setAttribute("data-theme", saved);
  } else {
    if (meta) meta.content = "light dark";
    document.documentElement.removeAttribute("data-theme");
  }
} catch (e) {}
`;

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html
      lang="en"
      className={`${newsreader.variable} ${jakarta.variable} ${plexMono.variable}`}
      suppressHydrationWarning
    >
      <head>
        <meta name="color-scheme" content="light dark" />
        <script dangerouslySetInnerHTML={{ __html: themeInitScript }} />
      </head>
      <body>
        <AppProviders>{children}</AppProviders>
      </body>
    </html>
  );
}
