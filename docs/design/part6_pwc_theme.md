# UC1 Design, part 6: UI theme matched to PwC brand colours

Labels: **[V]** verified from a web source (but see the caveat), **[A]** assumption, **[R]** recommendation. Checked 2026-11-13.

## Caveat first

PwC launched a refreshed identity in 2025 with a bolder orange and a "momentum mark" (reported by HRNXT and Marketech APAC). I found **no official hex table for the new identity** in public pages. Brandfetch lists PwC's brand colours as International Orange `#FD5108` and black `#000000` (a third-party profile, not PwC). Older PwC guidance (2020 Visual Identity Guidance, via brandcolor.dev) lists `#EB8C00`, `#FFB600`, `#D04A02`, `#DB536A`, `#E0301E` and greys `#2D2D2D`, `#464646`, `#7D7D7D`. **The owner must confirm the hex values against PwC's brand portal before a client demo.** All colours live in one token file so changing them is a one-file edit.

Do **not** bundle the PwC logo, wordmark, "momentum mark" or any licensed PwC font in the public repo. Those are trademarks/licensed assets. This repo uses colours only; the owner supplies a logo and font locally through configuration (`--cc-logo-url`, `--cc-font-family`).

## Token file (single source of truth)

`storefront/chat-feature/src/styles/_tokens.scss` and a mirrored JSON that the pack generator and the contrast check read.

| Token | Value | Source | Use |
| --- | --- | --- | --- |
| `--cc-color-primary` | `#FD5108` | **[V]** Brandfetch (secondary) | Primary actions, active step, progress, focus accent |
| `--cc-color-primary-strong` | `#D04A02` | **[V]** older PwC palette | Text links and small orange text on white (contrast about 4.5:1, computed, re-check in CI) |
| `--cc-color-amber` | `#FFB600` | **[V]** older PwC palette | Estimate and demo labels, highlights (black text on it) |
| `--cc-color-amber-deep` | `#EB8C00` | **[V]** older PwC palette | Secondary accent |
| `--cc-color-rose` | `#DB536A` | **[V]** older PwC palette | Pack accent, charts |
| `--cc-color-red` | `#E0301E` | **[V]** older PwC palette | Errors, destructive actions |
| `--cc-color-ink` | `#000000` | **[V]** | Headings, user bubble, text on orange |
| `--cc-color-ink-soft` | `#2D2D2D` | **[V]** older PwC palette | Body text |
| `--cc-color-grey-700` | `#464646` | **[V]** older PwC palette | Secondary text |
| `--cc-color-grey-500` | `#7D7D7D` | **[V]** older PwC palette | Borders, disabled (not for small text on white) |
| `--cc-color-surface` | `#FFFFFF` | **[A]** | Page and cards |
| `--cc-color-surface-alt` | `#F5F5F5` | **[A]** | Assistant bubble, panels |
| `--cc-color-border` | `#D9D9D9` | **[A]** | Card borders |
| `--cc-color-success` | `#1E7B34` | **[A]** (PwC publishes no green) | In-stock demo label, validated line |
| `--cc-radius` | `8px` | **[R]** | Cards and inputs |

## Contrast rules (WCAG 2.1 AA)

- Orange `#FD5108` against white is about 3.3:1 (computed): **never white text on orange for normal-size text**. Use **black text on orange** (about 6.3:1, computed) for buttons and chips; white on orange only for text 24 px or 19 px bold and larger.
- Orange text on white uses `--cc-color-primary-strong`.
- Amber `#FFB600` always carries black text.
- Never use colour alone for estimate, demo or error: add an icon and a text label.
- CI runs a script over the token file and every pack's tokens and fails the build if any declared text/background pair is under 4.5:1 (3:1 for large text and UI borders).

## Component mapping

| Element | Style |
| --- | --- |
| Page header strip | Black background, white text, a 4 px orange underline |
| Composer send button | Orange fill, black text, 44 px minimum height, visible 3 px black focus ring with 2 px white offset |
| User message bubble | Black fill, white text |
| Assistant message | `surface-alt` fill, `ink-soft` text, no avatar image from PwC |
| Live step indicator | Orange dot pulsing while active, grey check when done; no pulse under `prefers-reduced-motion` |
| Product card | White, `border` outline, orange "Add to purchase list" button, price in black semibold, stock label in success green with a "Demo stock" tag in amber |
| Comparison table | Black header row with white text, zebra `surface-alt`, differing cells marked with a text badge, not colour only |
| Estimate tag | Amber outline, black text, label "Estimate" |
| Purchase list panel | Dashed grey border, heading "Demo purchase list, not your cart" in amber label |
| Welcome panel cards | White cards, orange left border 4 px |
| "Why this?" panel | `surface-alt`, orange text link `primary-strong` |
| Notices | Info grey, warning amber with black text, error red text on white with an icon |
| Feedback thumbs | Grey outline, black when selected |

## Industry pack accents (D32, PL-07)

The PwC palette is the base theme for every pack so the product always looks like the owner's brand. A pack may override only `--cc-color-accent`, the hero image, the headline and terminology, using this palette **[R]**:

| Pack | Accent token | Value |
| --- | --- | --- |
| Tyres and auto parts | `--cc-color-accent` | `#E0301E` |
| Paints and coatings | `--cc-color-accent` | `#DB536A` |
| Cement and building materials | `--cc-color-accent` | `#EB8C00` |
| Plywood and wood panels | `--cc-color-accent` | `#D04A02` |
| Electrical | `--cc-color-accent` | `#FFB600` |

The accent appears on the welcome panel border, pack chip and section dividers. Primary actions stay orange everywhere. This keeps D32 ("theme and terminology change per pack") while honouring the PwC colour request; it is listed as owner approval N8.

## Implementation notes for Claude Code

- Tokens are applied as CSS custom properties on `.cc-chat` only, never on `:root`, so the host storefront theme is untouched.
- `GET /chat/config` returns the active pack's accent override and terminology; the base PwC tokens ship in the library stylesheet.
- Provide a `docs/theming.md` that explains how to replace every token and the logo and font.
- Add a visual regression check (screenshots of welcome panel, thread with cards, comparison, purchase list) in light mode; dark mode is out of scope for the baseline.

Sources: Brandfetch PwC profile (https://brandfetch.com/pwc.hr), brandcolor.dev PwC page citing PwC Visual Identity Guidance 2020 (https://brandcolor-dev.pages.dev/brands/pwc), HRNXT on the 2025 refresh (https://hrnxt.com/news/corporate/pwc-unveils-new-brand-identity-earns-spot-among-indias-best-workplaces-for-2025/60826/2025/07/10/). Contrast ratios were computed from the hex values, not taken from a source.
