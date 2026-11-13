# CLAUDE CODE MASTER PROMPT: UC1 AI-First Conversational B2B Commerce Accelerator

Paste this whole file into Claude Code as the first message, in an empty folder that will become the repo. The seed archive `uc1_repo_seed.zip` must be in that folder (repo root).

## 0. Setup (do this first, yourself)

1. Run `git init` if the folder is not a repository yet.
2. Unzip the seed into the repo root: `unzip -o uc1_repo_seed.zip -d .` (use Python `zipfile` if `unzip` is missing). Then move the archive out of the repo (`mv uc1_repo_seed.zip /tmp/`) or delete it, and make sure it is not committed.
3. Verify these exist, and report any that are missing: `CLAUDE.md`, `README.md`, `.gitignore`, `docs/CLAUDE_CODE_MASTER_PROMPT.md`, `docs/design/UC1_Stage3_Handoff.json`, `docs/design/part1_architecture_backend_core.md` to `part6_pwc_theme.md`, and in `docs/examples/`: `README.md`, `facade-beans.xml`, `occ-beans.xml`, `ChatController.java`, `DefaultChatFacade.validation-excerpt.java`, `CppDeliveryIssueDaoImpl.query-excerpt.java`, `MISSING_EXAMPLES.md`.
4. Commit as "Seed: design inputs and owner examples".

**About `docs/examples/`:** the owner's samples were screenshots, so these files are **faithful transcriptions of only what was visible**. Lines that were cut off are marked `TRUNCATED`. Nothing was reconstructed. Treat the visible patterns as binding. For everything in `MISSING_EXAMPLES.md`, ask the owner for the original file (the owner can overwrite the transcriptions with the real files at any time), or record an assumption.

Files in the seed: `docs/design/` holds the authoritative handoff JSON (decisions D01-D37, capability catalogue, scenarios) and parts 1-6 of the design, where part5 contains the Coding Convention Reference.

---

## 1. Mission

Build, in stages, a public GitHub repository named for the product that a client can drop into their own SAP Commerce OCC, facade and core layers: a full-page ChatGPT/Claude-style purchasing assistant for logged-in B2B dealers inside a Spartacus storefront. **AI interprets and communicates; SAP Commerce Java is the executor of record.** You write all production code, tests, generators, docs and CI. You do not guess where the design says "ask".

## 2. Read first, in this order (before any code)

1. `docs/design/part5_conventions_prompt_open_items.md` section 7 (Coding Convention Reference) and every file in `docs/examples/`.
2. `docs/design/part1…part4` and `part6`.
3. `docs/design/UC1_Stage3_Handoff.json` (capability catalogue with acceptance criteria, scenarios SC-01..SC-10, decisions).
Then write `docs/convention-digest.md` (what you will follow, what is unknown) and `docs/assumptions.md`.

## 3. Rules that never bend

1. **Conventions are binding.** Follow the owner's examples exactly: package naming, `Interface` + `DefaultX`, `XxxDao` + `XxxDaoImpl`, `*Data` in `facade-beans.xml`, `*WsDTO` in `occ-beans.xml`, `getDataMapper().map(...)`, `@Resource(name=...)` on private fields, property keys as `private static final String`, `ConfigurationService`, `validateParameterNotNull`, `IllegalArgumentException` in facades, `LogManager.getLogger`, log status only, Allman braces, `final` on parameters and locals, FlexibleSearch constants with `{r:...}` aliases and named parameters, `PagedFlexibleSearchService` with `SortQueryData`, `buildFilterClause`, `createSortQuery` with default fallback. `fpp` and `Cpp` are placeholders. Use package base `com.conversational.commerce`, bean prefix `cc`, type prefix `CC`.
2. **Unknown conventions are not invented.** Unknown = controller exception handling, Spring bean XML, `items.xml`, Backoffice config, populators/converters, tests, Angular/Spartacus, Javadoc style. If an example is missing: if the owner is present, STOP and ask; if you are running unattended, apply the clearly labelled default stated in the design, write it to `docs/assumptions.md` with the file it affects, and put a `// ASSUMPTION:` marker in code. Never silently invent.
3. **Protected decisions D01–D37 are fixed** (summary in section 4). If work seems to need a change, STOP that item, write evidence and options to `docs/owner-approvals.md`, and continue with other work. Never change one.
4. **Security always:** the model never builds or runs queries, never sees prices, totals, margin, priority, names, emails, internal PKs or other customers' data, has no write tool, never places orders or touches a cart. Customer identity comes only from the Spring Security context. Every external model call goes through the sanitiser; sanitiser off = calls fail closed. Provenance check on every ID the model returns. No key, real data or client-specific model in the repo. Logs never contain prompts, message text or personal data.
5. **Future scope comments:** put `// FUTURE SCOPE:` at each hook for unit-level order access (D33), unit-level replenishment (D34), live stock, real cart, carrier tracking.
6. **Label demo content** wherever shown: demo stock, "Demo purchase list, not your cart".
7. **Verify before relying** on any platform or provider fact (model IDs, request parameters, yocc wiring, Spartacus APIs for the owner's version). Record the source and date in `docs/verification-log.md`. Provider terms change.
8. **Labels in docs and code comments:** keep facts, assumptions and recommendations visibly separate.
9. **No silent scope creep.** Excluded: promotions, AI-generated queries, AI-placed orders, separate authentication, persisted chat history, photo search, voice, human-handoff workflow.

## 4. Protected decisions (one line each; full text in the JSON)

D01 public repo, drop-in module, no secrets/real data/client models · D02 local demo with seeded dummy data · D03 Add to Cart = labelled placeholder list, checkout in storefront, AI never orders · D04 dealer/B2B buyer persona only · D05 search by code, description, use case, constraints · D06 deterministic backend search; AI extracts and judges; price/stock evaluated in backend · D07 session-only memory, no persisted chat history · D08 customer-linked interest events in a dedicated table, on by default with visible notice, toggle, 90-day retention · D09 full-page experience with proactive welcome panel · D10 order tracking from Commerce-held data only · D11 simple rule-based replenishment · D12 purchase-pattern change detection in baseline · D13 every capability toggleable in Backoffice (fail-closed for PL-01/03/04/05 per A1) · D15 cross-sell/bundles deterministic, AI explains · D16 FAQ only from approved content · D17 multilingual: translate in, process in English, translate out, identifiers never translated · D18 provider/model per task across Gemini, Anthropic, OpenAI · D19 keys only in secure properties · D20 welcome panel · D21 attribute comparison and compatibility · D22 reorder from previous order · D23 ten scenarios · D24 business recommendation controls, margin never to LLM or customers · D25 quantity recommendations and basket optimisation · D26 AI returns IDs only, backend hydrates, prices never to LLM · D27 hardcoded labelled demo stock with live-stock hook · D28 off-topic redirected, unsupported declined · D29 live step indicators plus streamed animated answer · D30 thumbs feedback metadata only · D31 contact account manager text when unresolved · D32 five industry packs, generators in repo, live switch, data coexists · D33 own orders only · D34 own history only · D35 quick order, budget planner, substitution, order search, why-this panel, editable list in baseline · D36 ≤10 s target, usage caps · D37 owner conventions binding, future-scope comments.

**Owner-approved on 2026-11-13:** A1 fail-closed toggles; A2 synthetic-data-only guard; A3 NOT adopted; A4 pack dealers + all-packs dealer; A5 `ROLE_CUSTOMERGROUP` only with userId binding; A7 events and feedback in separate tables; A9 AI suggests enum event types only; A10 margin in separate item type; A11 browser-held purchase list and context; A13 validate-then-reveal streaming; A14 audit as structured logs. **Assumed, owner to confirm:** SAP Commerce 2211-jdk21 (Jakarta, Spring 6), Angular 19, Node 22 (A12); derived names `cc`/`CC`/`cccore`… (N1). **Pending owner approval (do not implement):** N3 welcome-text caching, N4 spike fallbacks that change protected decisions, N8 PwC base theme with pack accents only (implement as designed; flag in owner-approvals).

## 5. Repository layout

```
/
├── README.md  LICENSE  SECURITY.md  CONTRIBUTING.md  CHANGELOG.md
├── .gitignore                      # local.properties, *.key, .env, node_modules
├── .github/workflows/              # backend build+test, storefront build+test, secret scan, pack regeneration check, contrast check
├── docs/
│   ├── design/                     # the files listed above (read-only inputs)
│   ├── examples/                   # owner code samples (read-only)
│   ├── convention-digest.md  assumptions.md  owner-approvals.md  verification-log.md
│   ├── architecture.md  extension-guide.md  demo-script.md  theming.md  provider-terms.md  privacy.md
│   └── spikes/S1.md … S8.md
├── tools/packgen/                  # deterministic generator, manifests/, schema/
├── backend/
│   ├── cccore/  ccfacades/  ccocc/  ccbackoffice/  ccsampledata/
└── storefront/chat-feature/        # Spartacus lazy-loaded feature library
```

## 6. What to build (summary; details in the design files)

**Backend (`cccore`, `ccfacades`, `ccocc`):** six item types (`CCConfiguration`, `CCConfigEntry`, `CCInterestEvent`, `CCFeedback`, `CCProductBusinessAttribute`, `CCUsageCounter`) and four enums; layered configuration with fail-closed rules and a short-TTL cache; DTO beans; endpoints `POST /chat`, `POST /chat/stream`, `GET /chat/welcome`, `GET /chat/config`, `POST /chat/feedback`, `POST /chat/purchase-list/validate`; `UserBindingGuard`; stream events `step_started`, `step_completed`, `block`, `text_delta`, `done`, `error` plus heartbeat; tools and workflows exactly as in part2; DAOs per part2 and the DAO convention; provider adapters (Gemini, OpenAI, Anthropic) over `HttpClient` + Jackson with `ModelRouter`, fallback and token counting; sanitiser (`FieldPolicy`, `EgressPolicy`, masking rules, `OutboundGuard`, request-scoped token maps); prompts and strict JSON schemas as resource files; orchestrator with limits, parallel groups, provenance check, slot filler, text-vs-table check, `TraceBuilder`; audit logger `cc.audit`; usage caps; interest-event service and retention cron jobs; welcome service; business ranker; replenishment, pattern-change, co-purchase, basket and budget-planner rules; demo stock gateway.

**Backoffice (`ccbackoffice`):** editor, list and search configs for all types; business attribute restricted to group `ccbusinessmanagergroup`.

**Frontend (`storefront/chat-feature`):** lazy-loaded, CSR-only auth-guarded route (default `/ai-assistant`); signals store persisted to `sessionStorage` (`cc.chat.<userId>`, schema-versioned, bounded, cleared on logout); fetch-based SSE client with bearer token, AbortController, heartbeat watchdog and automatic fallback to `POST /chat`; components: page, composer, thread (`role="log"`), message, step indicator, block renderer, product card, comparison table, order summary, purchase list (labelled demo), recommendation list, welcome panel, clarification chips, notice, feedback controls, why-this panel, interest notice; terminology pipe; Spartacus i18n for static labels; accessibility to WCAG 2.1 AA; responsive; reduced-motion respected.

**Theme:** PwC-matched colours per `part6_pwc_theme.md`: orange `#FD5108` primary, black ink, grey text, amber/rose/red accents, black text on orange, token file as single source of truth, contrast check in CI, pack accents only from the PwC palette. Do not bundle PwC logos or fonts; expose `--cc-logo-url` and `--cc-font-family`. State in `docs/theming.md` that the hex values must be confirmed against PwC's brand portal.

**Packs and data (`tools/packgen`, `ccsampledata`):** manifests and JSON Schema for five packs; deterministic generator; committed generated ImpEx; `SampleOrderLoader` creating orders with relative dates; `@SystemSetup` options per pack; essential data for configuration and toggles; fixtures for SC-01..SC-10 as listed in part3 section 2b.4.

**Docs and CI:** everything under `docs/`, secret scanning with push protection, contrast check, pack regeneration check, canary leak tests.

## 7. Stages, prompts and checks

Run in order. Finish each stage's check before the next. Commit per stage with a clear message. If a check cannot be run in your environment, say so in the stage summary and add the command the owner should run.

**Stage 0 – Intake.** Read per section 2. Write `convention-digest.md`, `assumptions.md`. List every missing example with the stage that needs it (controller error handling, Spring XML, items.xml/Backoffice, populator, tests, Angular/Spartacus + lint, Javadoc/braces, versions, full truncated catch blocks and DAO query). *Check:* digest complete; unknowns named; no code yet.

**Stage 1 – Guardrails.** Layout, `.gitignore`, CI skeletons, secret scanning config, `SECURITY.md`, property-key constants, `project.properties` safe defaults. *Check:* CI runs; a planted fake key is caught.

**Stage 2 – Spikes S1, S2, S3.** Thin throwaway code on spike branches; write `docs/spikes/S1..S3.md` with numbers, pass/fail against `part4`, and the branch taken. S1: streaming endpoint in a yocc-generated `ccocc` plus Angular fetch reader. S2: DataMapper field filtering for chat DTOs and events. S3: schema-constrained decision call on three providers, current model IDs verified. *Check:* if S1 picks polling, S2 needs non-mapper filtering, STOP and record owner approval (do not proceed with that branch).

**Stage 3 – Core types and configuration.** Item types, enums, `ConfigurationService` (layers, TTL, fail-closed), essential data (all 43 toggles and defaults), Backoffice config. *Check:* build and initialise; toggles editable and effective within TTL; protected toggles refused; unit tests.

**Stage 4 – Deterministic data services.** DAOs, gateways, tools, rules per part2. Customer-scoped everything. *Check:* unit tests per rule; integration tests as customer A vs B for every customer-scoped DAO and tool; budget planner never exceeds budget (property test); no query text built from request data.

**Stage 5 – Sanitiser, adapters, router, prompts.** *Check:* canary tests (unique price, margin, email, order code never in captured outbound payloads); contract tests from recorded fixtures; no key needed in CI; `syntheticDataOnly` blocks free-tier outside demo.

**Stage 6 – Orchestrator.** Registries, validator, workflows for every intent, limits, provenance, slots, text-vs-table, trace, audit, usage caps, interest events, retention jobs, welcome service. *Check:* all ten scenarios pass end to end against the deterministic fake provider; unknown tool rejected; looping fake stopped by caps; audit has no prompts or personal text.

**Stage 7 – Facades and OCC.** Facades per sample style, controllers per sample annotations except `@Secured({ROLE_CUSTOMERGROUP})`, `UserBindingGuard`, streaming with heartbeat and timeout, fallback, config endpoint. *Check:* HTTP tests with customer tokens: other `userId` → 403; stream arrives incrementally; fallback returns the same blocks.

**Stage 8 – Frontend and theme.** Per part3 2b.1–2b.3 and part6. *Check:* component tests, SSE parser fixtures, axe checks, contrast check, e2e SC-01 with fake backend, keyboard-only walk-through, reduced motion, screenshot baseline.

**Stage 9 – Packs and seed data.** *Check:* regeneration byte-identical in CI; all five packs load together; relative dates correct; S5 recorded; SC-10 switch works after TTL + reload.

**Stage 10 – Hardening and measurement.** S4, S6, S7, S8; injection corpus; load test with fake provider. *Check:* S4 numbers against 10 s; S7 zero leaks; any result that triggers an owner-approval branch (A3, S5 alternative) is reported, not applied.

**Stage 11 – Documentation and handover.** README, extension guide (add a tool, workflow, rule, gateway), provider terms note (free Gemini = synthetic data only; paid or ZDR for client data; Anthropic Fable/Mythos excluded from ZDR), privacy note (interest events, retention, notice), demo script SC-01..SC-10, upgrade notes. *Check:* a new developer adds a sample tool following only the guide.

## 8. Acceptance (the whole build)

- Every one of the 43 capability IDs has its toggle, code and a passing check from `part4` section 3.1.
- SC-01..SC-10 each pass end to end with the fake provider and have a demo-script entry.
- Zero cross-customer leaks (S7); zero canary leaks; no key in git history; secret scan clean.
- Protected decisions untouched; `owner-approvals.md` lists every pending approval with evidence and options.
- Convention digest followed; deviations only where the owner approved.

## 9. Every stage ends with

A short summary: what changed, what was verified (and how), what was assumed, what needs the owner. No long recaps.
