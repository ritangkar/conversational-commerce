# UC1 Design, continuation B: frontend, packs, configuration, security, tests, deployment (section "2b")

Labels: **[V]** verified, **[A]** assumption, **[R]** recommendation. The owner supplied no Angular, Spartacus, test, Spring XML, items.xml or Backoffice examples, so everything below marked "no example" must be checked against the owner's examples before it is built (see section 6, open items).

## 2b.1 Frontend component and state design

**Delivery [R]:** a lazy-loaded Spartacus feature library (working name `@conversational-commerce/chat`), client-side rendered only. The route (default `/ai-assistant`, configurable) sits behind the standard Spartacus auth guard. Feature registration uses Spartacus' feature-module configuration. **Verify against the owner's Spartacus version [A]:** exact guard, feature-config and logout-event APIs.

| Layer | Pieces |
| --- | --- |
| Page | `ChatPageComponent` (layout, welcome vs thread), `ComposerComponent` (textarea, send, cancel), `InterestNoticeComponent` (shown when `/chat/config` says events are on) |
| Thread | `ThreadComponent` (`role="log"`), `MessageComponent`, `StepIndicatorComponent` (live steps), `BlockRendererComponent` (switch on `block.type`) |
| Blocks (presentational, inputs only) | `ProductCardComponent`, `ComparisonTableComponent`, `OrderSummaryComponent`, `PurchaseListComponent` (always labelled "Demo purchase list, not your cart"), `RecommendationListComponent`, `WelcomePanelComponent`, `ClarificationChipsComponent`, `NoticeComponent` |
| Trust | `FeedbackControlsComponent` (thumbs), `WhyThisPanelComponent` (expandable, renders `TraceData` only) |
| Services | `ChatApiService` (fetch streaming + fallback), `SseFrameParser`, `ChatStore`, `ContextEnvelopeBuilder`, `SessionPersistenceService`, `ConfigService`, `TerminologyPipe` |

**State [R]:** an Angular-signals store (`ChatStore`) kept inside the feature, so it does not couple to the storefront's NgRx. State slices: `thread`, `activeSteps`, `streamingText`, `purchaseList`, `shortlist`, `config`, `status` (idle, streaming, error, capped). Persisted to `sessionStorage` under `cc.chat.<userId>`; schema-versioned; bounded by `context.maxTurns` and `context.maxChars`. Prices and hydrated card data are never stored, only codes, quantities, notes and turn text.

**Clearing (CF-04, D07):** the store clears on logout, on user change and when the tab closes (sessionStorage semantics). **Assumption to prove in S1:** the Spartacus logout event fires before navigation so the clear always runs.

**Streaming client [V E15 for the platform facts]:** `fetch` POST with `Authorization: Bearer <Spartacus token>`, `ReadableStream` reader, `TextDecoder`, an `AbortController` for cancel (Escape key and a Stop button) and a heartbeat watchdog. `EventSource` is not used because it cannot send the bearer header. On connect failure, a heartbeat timeout or a non-2xx, the client retries once through `POST /chat` and shows blocks without animation.

**Rendering order:** `step_started`/`step_completed` update the step list; each `block` event adds a block in its slot; `text_delta` appends to the block's text with a typewriter reveal that respects `prefers-reduced-motion`; `done` finalises, shows feedback controls and enables the composer.

**Purchase list (PA-01, PA-02, A11):** client-held. Every change calls `POST /chat/purchase-list/validate`, which returns validated, priced lines and issues. Quantity edits are local; the server is the only source of prices and availability. The list feeds `reviewPurchaseList` and `BASKET_REVIEW` through the request body, and the backend re-validates it each time.

**Future scope hook:** `PurchaseListGateway` has a `// FUTURE SCOPE: real cart integration` comment where a client would replace the placeholder with a cart add.

## 2b.2 Theming and terminology (PL-07, D32)

`GET /chat/config` returns the active pack's theme tokens, terminology, sample prompts and enabled capabilities. Tokens are applied as CSS custom properties on the `.cc-chat` container only, so the rest of the storefront is untouched.

| Token group | Examples |
| --- | --- |
| Colour | `--cc-color-primary`, `--cc-color-surface`, `--cc-color-accent`, `--cc-color-on-primary` |
| Shape and type | `--cc-radius`, `--cc-font-family`, `--cc-font-scale` |
| Content | `--cc-hero-image-url`, pack name, welcome headline, sample prompts, terminology map (e.g. "dealer", "SKU", "pack size") |

Static labels use Spartacus i18n resources; pack terminology overrides specific keys through `TerminologyPipe`. Model-generated prose is the only text that goes through translation (CF-05). Every token pair is checked at pack build time for contrast.

## 2b.3 Accessibility (target WCAG 2.1 AA, E20)

The core storefront claims about 90% coverage, so this route is tested separately **[V E20]**.

- The thread is `role="log"` with `aria-live="polite"`; step changes are announced politely; streamed text is **not** announced token by token, only the completed block.
- Composer: `Enter` sends, `Shift+Enter` newline, `Escape` stops a stream; focus returns to the composer after `done`.
- Cards are focusable groups with labelled actions; the comparison is a real `<table>` with header cells; icons have text alternatives; touch targets are at least 44 px.
- Motion respects `prefers-reduced-motion`; colour is never the only signal for estimates or demo labels.
- Gates: automated axe checks in CI, keyboard-only walk-through and one screen-reader pass per release.

## 2b.4 Industry packs, seed data and ImpEx (PL-07)

**Pack model [A, E19, spike S5]:** one base site and base store; one product catalog with a root category per pack; a `pack` entry per pack in `CCConfigEntry` holding root category, attribute mapping, terminology, sample prompts and theme tokens; one demo dealer per pack and one all-packs dealer (A4); every tool scopes by the pack resolved in the backend. Switching is `CCConfiguration.activePack` plus the cache TTL (`cc.config.cacheTtlSeconds`, default 10 **[A]**) plus a page reload.

**Manifest per pack (YAML, validated by JSON Schema in CI):**

| Section | Content |
| --- | --- |
| Identity | pack code, display name, root category code |
| Catalogue | category tree, product count, code pattern (e.g. `EL-####`), attribute definitions, price ranges, demo stock, one deliberately discontinued item |
| Attribute mapping | searchable fields, comparison attributes, optional compatibility attribute (PD-05), use-case keywords |
| People | dealer uid pattern, B2B unit, the all-packs dealer |
| Order rhythm | per dealer and product: interval days, quantity, jitter, `daysAgo` offsets, one overdue item, one pending order |
| Content | FAQ entries, complementary category rules, terminology, sample prompts, theme tokens |

**Generator [R]:** `tools/packgen`, a deterministic script (fixed seed) that emits the static ImpEx: catalog, categories, classification, customers, FAQ and configuration. Generated ImpEx is committed so a client needs no generator, and CI checks that regeneration produces identical files.

**Dates:** ImpEx cannot compute relative dates reliably **[A, E17 secondary]**, so orders are created by a Java loader (`SampleOrderLoader`) from the manifest's `daysAgo` offsets, using `ModelService`. This keeps "a 30-day rhythm" true on the day of the demo.

**Loading:** `@SystemSetup` hooks in `ccsampledata`, one option per pack (`ccsampledata.pack.<code>`), plus an essential-data hook for default `CCConfiguration` and `CCConfigEntry` rows. Naming follows the platform's `essentialdata*.impex` and `projectdata*.impex` pattern **[A, E17]**. The loader never runs against a site whose `syntheticDataOnly` flag is false.

**Required demo fixtures (from scenarios):**

| Scenario | Fixture the generator must create |
| --- | --- |
| SC-01 | Tyre dealer with a clear 30-day rhythm, one overdue item, one pending order |
| SC-02 | Paint products with surface, finish and coverage attributes; several under 3,000 per unit for this dealer |
| SC-03 | Electrical items `EL-4821`, `EL-9910`, a cable listed by description, and discontinued `EL-1033` with a replacement; one unresolvable line |
| SC-04 | Cement dealer with enough history to fill a 5 lakh budget and leave items out |
| SC-05 | Plywood dealer whose 18 mm ply quantity fell about 40% over the last two orders |
| SC-06 | Tyre dealer with several undelivered orders above 20,000 last month; a second dealer's order for the isolation demo |
| SC-07 | Three exterior primers with comparable attributes and a coastal-suitability attribute on only some |
| SC-08 | Electrical list with one forgotten usual item, one duplicate of a pending order, two complements |
| SC-09 | Cement dealer; Hindi prompt uses the SC-01 data shape |
| SC-10 | All five packs loaded together |

## 2b.5 Configuration and deployment

| Layer | Holds | Where |
| --- | --- | --- |
| 1 | Safe defaults: timeouts, endpoints, provider tier, pool sizes, stream timeout and heartbeat | `project.properties` (committed) |
| 2 | API keys by alias, environment overrides | `local.properties` (gitignored), environment variables, Cloud secret |
| 3 | Non-secret runtime config: toggles, thresholds, prompts, routes, terminology, packs | Backoffice items, cached with a short TTL, cleared on save |

**CCv2 [A]:** extensions listed in the project manifest; the storefront builds separately with the Node and Angular versions of the owner's release (Angular 19 / Node 22 if A12 holds **[V E10]**). The route is excluded from server-side rendering. **Gap [E14]:** SAP documents nothing about ingress, CDN or API-node buffering of SSE, so S1 decides whether streaming ships, falls back to NDJSON or to polling.

**Release gate:** no release until S1–S8 have a recorded pass or an accepted branch (section 4).

## 2b.6 Security controls (OWASP LLM Top 10 2025, E16 secondary: confirm against the OWASP document)

| Risk | Control in this design |
| --- | --- |
| LLM01 Prompt injection | Privilege separation: any call that reads untrusted text (product text, FAQ, order notes, user text) has no tool authority and returns IDs and enums only; delimiters; instruction-pattern stripping; schema-validated output |
| LLM02 Sensitive information disclosure | `FieldPolicy`/`EgressPolicy`, `OutboundGuard`, no prices or margin in any prompt, metadata-only audit |
| LLM05 Improper output handling | Provenance check, slot filling, text-versus-table check, nothing rendered as HTML from model text |
| LLM06 Excessive agency | Closed tool catalogue, workflow templates, no write tools, AI never places orders or touches a cart |
| LLM07 System prompt leakage | Prompts hold no secrets; "Why this?" never shows prompts |
| LLM09 Misinformation | Evidence packs, closed-world prompts, honest-limit notices |
| LLM10 Unbounded consumption | Step, time, length and per-customer caps, executor and queue bounds |
| Isolation | Customer bound from the security context; `userId` guard; DAO customer filter; spike S7 |

## 2b.7 Test strategy

| Level | What | Notes |
| --- | --- | --- |
| Unit | Tools, rules, allocator (never exceeds budget), replenishment and pattern maths, `buildFilterClause`, sanitiser, schema validators, slot filler | Plain JUnit; follow the owner's sample once supplied |
| Service/integration | DAOs against the platform test database; seeded packs; every customer-scoped tool run as customer A and B | S7 |
| Provider contract | Recorded fixtures per adapter; no keys in CI; optional live smoke run with a local key | S3 |
| Golden sets | Intent and parameter extraction per pack; Hindi and other languages with identifier survival | S8 |
| Safety | Injection corpus, price-leak canaries (a unique price must never appear in a captured outbound payload), margin canary | Run in CI with the fake provider |
| Performance | Fake provider with configurable latency; concurrent sessions; stream hold time | S4 |
| Frontend | Component tests for each block, SSE parser fixtures, axe checks, e2e for SC-01 to SC-10 with a deterministic fake backend | Test framework to match the owner's workspace |

**No test examples were supplied.** The implementation prompt tells Claude Code to ask for them before writing the first test.
