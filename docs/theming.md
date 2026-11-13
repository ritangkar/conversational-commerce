# Theming

`storefront/chat-feature/src/styles/tokens.json` is the single source of truth; `tools/ci/tokens.py` generates `_tokens.scss`; `tools/ci/contrast.py` fails CI if a declared text/background pair is under 4.5:1 (3:1 for large text and UI borders) or a pack accent cannot reach 4.5:1 with black or white text.

**Confirm before a client demo.** The hex values are from part6 of the design: primary `#FD5108` is from a third-party profile (Brandfetch), the others from older PwC guidance via a third-party site. PwC published no official hex table for its 2025 identity that was found. The values must be confirmed against PwC's brand portal; changing them is a one-file edit plus `python3 tools/ci/tokens.py`.

**Not bundled.** No PwC logo, wordmark, momentum mark or font is in this repository. Set `--cc-logo-url` and `--cc-font-family` in the host storefront.

Rules in force: black text on orange; white on orange only for 24 px or 19 px bold text; amber always carries black text; estimate, demo and error states carry an icon or text label, never colour alone; focus ring 3 px black with a 2 px white offset; motion stops under `prefers-reduced-motion`. Pack accents come from the same palette (`manifests/*.json` `theme`).
