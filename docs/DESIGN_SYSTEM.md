# Ledger Design System

## Design Direction & Dials

- **Identity**: Editorial, calm, archival financial clarity inspired by modern ledger publications without cloning existing products or using desktop/OS chrome.
- **Dials**: Public site `ENERGY 2 / RHYTHM 3 / MOTION 2`; Accounting workspace `ENERGY 1 / RHYTHM 2 / MOTION 1`.
- **Brand Motif**: Original geometric double-entry equilibrium mark (`assets/branding/logo-mark.svg`) representing balanced debit and credit columns bridged by a horizontal ledger bar.

## Color System (Verified WCAG AA Contrast)

### Light Theme
- Canvas (Ivory): `#F6F4EE` (Text `#222222` contrast `14.47:1` PASS; Forest `#183D30` contrast `10.92:1` PASS)
- Surface (White): `#FFFFFF` (Secondary `#4F5A54` contrast `7.18:1` PASS)
- Accent Soft (Sage): `#D6E8D4` (Forest `#183D30` contrast `9.35:1` PASS)
- Brand Primary (Forest): `#183D30` (White `#FFFFFF` contrast `12.01:1` PASS)

### Dark Theme
- Canvas: `#111A16` (Primary `#EDF2ED` contrast `15.66:1` PASS)
- Surface: `#19251F` (Secondary `#A5B4A9` contrast `7.32:1` PASS; Accent `#B9D9B9` contrast `10.33:1` PASS)

Semantic financial status badges (Paid, Overdue, Partially Paid, Pending Approval, Void) always pair tint contrast with explicit uppercase text labels and never rely on color alone.

## Typography

- **Editorial Serif**: `Newsreader` (self-hosted via `next/font/google`) reserved for public headlines and the brand wordmark.
- **Application Sans**: `Plus Jakarta Sans` for crisp table legibility, forms, and navigation.
- **Monospace**: `IBM Plex Mono` strictly for account codes, document numbers, and tabular numerals (`font-variant-numeric: tabular-nums`), right-aligned in every financial column.

## Accessibility & Responsiveness

- Keyboard-scrollable table regions (`tabIndex={0}`, `role="region"`), visible `:focus-visible` indicators in both themes, native `<dialog>` modals with `Esc` dismissal, `prefers-reduced-motion` media query support, 44px minimum tap targets, and verified layouts across `390px`, `768px`, `1024px`, and `1440px`.
