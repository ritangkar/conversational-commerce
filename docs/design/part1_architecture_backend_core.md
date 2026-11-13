# UC1 Design, part 1: status, architecture and backend core (sections 0, 1 and first half of 2a)

Labels: **[V]** verified, **[A]** assumption, **[R]** recommendation. Date of evidence: 2026-11-13.

## 0. Status, approvals and evidence

| ID | Outcome | Effect on design |
| --- | --- | --- |
| A1 | Adopted | PL-01/03/04/05 are fail-closed: sanitisation off blocks all external model calls; config, orchestrator and audit cannot be switched off silently |
| A2 | Adopted | `syntheticDataOnly` provider flag; free-tier Gemini refused outside demo sites |
| A3 | Not adopted | Translate-out stays; revisit only if spike S4 shows the 10 s target cannot be met |
| A4 | Adopted | One demo dealer per pack plus one all-packs dealer |
| A5 | Adopted | Chat endpoints `@Secured` = `ROLE_CUSTOMERGROUP` only; `userId` bound to the authenticated principal |
| A6 | Owner named `com.conversational.commerce` | Packages `com.conversational.commerce.*`. **[A]** Derived: bean prefix `cc`, item-type prefix `CC`, extensions `cccore`, `ccfacades`, `ccocc`, `ccbackoffice`, `ccsampledata` |
| A7, A9, A10, A11, A13, A14 | Adopted | Two tables for events and feedback; AI suggests enumerated event types only; margin in its own item type; purchase list and context held in the browser; validate-then-reveal streaming; audit as structured logs |
| A8 | Open | Notice text is a configurable field with placeholder wording |
| A12 | **[A]** | SAP Commerce 2211-jdk21 (Jakarta, Spring 6), Composable Storefront Angular 19 / Node 22 |

Evidence re-checked 2026-11-13: OpenAI no-training default is **[V]** (openai.com/business-data). Anthropic API deletes inputs and outputs within 30 days **[V]** (privacy.claude.com); the 7-day figure on third-party sites is unresolved, do not quote it. Fable 5 and Mythos 5 require 30-day retention and are excluded from ZDR since 2026-06-09 **[V]**, so never route ZDR clients to them. SAP Learning says OCC extensions are best created with the yocc template **[V]**; the "name ends with `occ`" rule and web-spring import path are secondary until spikes S1/S2. KBA 3363029: 2211.36–43 and 221121.x = Angular 19 / Node 22 **[V]**; newer releases unchecked. E12, E14, E16, E17, E18, E19 stay unverified and are gated by spikes or "verify before use".

## 1. Architecture overview

The model proposes; SAP Commerce Java validates, executes and hydrates; the browser only renders validated blocks.

| Module | Responsibility | Convention anchor |
| --- | --- | --- |
| `cccore` | Item types, services, DAOs, orchestrator, tool and workflow registries, provider adapters, sanitiser, rules, audit, usage caps | `ChatService`/`DefaultChatService`, `XxxDao`/`XxxDaoImpl` |
| `ccfacades` | `facade-beans.xml` (`*Data`), facades | `ChatFacade`/`DefaultChatFacade` |
| `ccocc` | **[R]** generated from the yocc template; controllers, `occ-beans.xml` (`*WsDTO`), web Spring config | `ChatController extends BaseController` |
| `ccbackoffice` | `*-backoffice-config.xml` for the custom types | no example supplied |
| `ccsampledata` | Pack manifests, generators, ImpEx, `@SystemSetup` loaders | no example supplied |
| Spartacus feature library | Lazy-loaded chat route, components, state, theming | no example supplied |

Layering never changes: Controller → Facade → Service → DAO or gateway. The orchestrator is a service; the controller sees only the facade. The sanitiser sits in front of every provider adapter, so no code path reaches a model without it.

**Runtime flow of one turn (streaming):** (1) browser POSTs message + bounded context with bearer token; (2) controller authenticates and binds the customer; (3) facade validates length, usage cap; (4) sanitise, call the `DECIDE` model; (5) validate intent and tool against catalogue, enabled capabilities and active pack; (6) run the workflow's tools, independent ones in parallel, emitting `step_started`/`step_completed`; (7) sanitise, call `JUDGE`/`EXPLAIN`/`COMPOSE` as the workflow needs; (8) validate IDs (provenance), fill slots, check text against tables, hydrate prices/images/stock; (9) stream `block` events, `text_delta` chunks, `done`; write the audit event. Non-streaming returns the same blocks as one `ChatResponseWsDTO`.

**SPIs (Spring-registered, discovered by bean type):** `ModelProviderAdapter` (Gemini, OpenAI, Anthropic), `ChatTool`, `WorkflowTemplate`, `RecommendationRule`, `CandidateProvider` (FlexibleSearch default, Solr optional), `CatalogGateway`, `PriceGateway`, `OrderGateway`, `CustomerGateway`, `StockGateway` (demo values, **future scope** live stock), `PurchaseListGateway` (**future scope** real cart), `SanitisationRule`, `AuditSink`. Clients override by bean alias; no core edit needed to add a tool.

**Orchestration:** one schema-constrained decision call → backend validation → workflow template (deterministic Java tools, parallel groups, hard step/time limits) → optional relevance/explanation call with no tool authority and IDs/enums output → composition call → validate-then-reveal.

**Configuration layers:** `project.properties` safe defaults → `local.properties`/environment/Cloud secret → Backoffice items (non-secret) → service with short-TTL cache cleared on save. **Audit:** metadata-only structured events through logger `cc.audit`. **Pack switching:** `activePack` on the configuration item; every tool scopes by the pack resolved in the backend; visible after cache TTL plus page reload.

## 2a. Backend core design

Follow the conventions in the Coding Convention Reference exactly: interface + `Default*`, XML-declared beans, `@Resource` injection, constants for config keys and queries, `validateParameterNotNull`.

### Item types (cccore `items.xml`; six types)

| Type | Key attributes | Notes |
| --- | --- | --- |
| `CCConfiguration` | `code` (unique), `activePack`, `syntheticDataOnly`, `enabled` | One row per base site. Holds the pack switch |
| `CCConfigEntry` | `configuration`, `entryType` (enum), `packCode`, `key`, `value` (localised), `enabled`, `sortOrder` | Unique on (`configuration`, `entryType`, `packCode`, `key`). Types: TOGGLE, THRESHOLD, PROMPT, TERMINOLOGY, PROVIDER_ROUTE, PACK, FAQ, SEARCH_FIELD, ATTRIBUTE_MAPPING, RECOMMENDATION_WEIGHT |
| `CCInterestEvent` | `customer`, `eventType` (VIEWED, COMPARED, SHORTLISTED, REQUESTED_NOT_FOUND), `productCode` or `categoryCode` or `normalisedToken`, `packCode`, `occurredAt` | Dedicated table (D08). No text column. Index (`customer`, `occurredAt`). Written only by `InterestEventService` |
| `CCFeedback` | `responseId`, `rating`, `intent`, `toolsUsed`, `auditRef`, `createdAt` | No customer, no text. Own retention |
| `CCProductBusinessAttribute` | `product` (unique), `priority`, `margin`, `packCode` | Restricted Backoffice permission group; only `BusinessRanker` may read it |
| `CCUsageCounter` | `customer`, `periodKey`, `messageCount`, `tokenCount` | Unique (`customer`, `periodKey`); batch increments |

Enums: `CCProviderCode` (GEMINI, OPENAI, ANTHROPIC), `CCEventType`, `CCFeedbackRating` (UP, DOWN), `CCConfigEntryType`. Reuse OOTB `Product`, `Order`, `Consignment`, `B2BCustomer`, `Category`, classification features.

**Backoffice [A]:** editor-area, list-view, simple-search per type; create-wizard for configuration and entries; business attribute visible only to group `ccbusinessmanagergroup`; events and feedback read-only. Match the owner's sample when supplied.

**Entry keys (seeded by essential data):** `capability.<ID>.enabled` for all 43 IDs; `orchestration.maxSteps` (8), `orchestration.maxSeconds` (9), `clarification.maxQuestions` (2); `context.maxTurns`, `context.maxChars`; `replenishment.windowDays`, `.minOrders`, `.dueRatio`; `patternChange.thresholdPct`, `.minSample`, `.recentOrders`; `budget.candidateCap`; `privacy.interestEvents.enabled` (true), `.retentionDays` (90), `.noticeText`; `contact.text`; `route.<task>.provider`, `.model`, `.fallback`; `usage.cap.messages`, `.tokens`, `.period`; `stock.demoNotice`; `purchaseList.label`. Defaults marked in brackets are **[A]** starting points for spike S4.

**Fail-closed (A1):** `ConfigurationService` refuses to disable PL-01, PL-03, PL-04 or PL-05 by toggle alone. PL-03 off makes `ModelGateway` throw before any external call. PL-04 off makes chat return the CF-06 unavailable notice. PL-05 off needs a second confirming entry and writes a final audit line.

**Secrets (D19):** `project.properties` holds safe defaults (timeouts, endpoints, `cc.provider.<code>.tier`, stream timeout, heartbeat, pool size), no keys. Keys only in `local.properties` (gitignored), environment variables or Cloud secret under `cc.provider.key.<alias>`. Backoffice stores the alias. Add secret scanning and push protection.

### DTO beans (XML beans)

`facade-beans.xml` in `com.conversational.commerce.facades.data.chat.*` (`*Data`); `occ-beans.xml` in `com.conversational.commerce.occ.chat.*` (`*WsDTO`). Every Data bean has a WsDTO twin.

| Bean | Main properties |
| --- | --- |
| `ChatRequestData` | `message`, `clientRequestId`, `uiLocale`, `context` (`ChatContextData`), `purchaseList` (list of `PurchaseListLineData`) |
| `ChatContextData` | `turns` (`ChatTurnData`: role, text, block references), `shortlistCodes`, `schemaVersion` |
| `ChatResponseData` | `responseId`, `language`, `blocks`, `suggestedActions`, `feedbackHandle`, `traceRef`, `notices` |
| `ChatBlockData` | `blockId`, `type`, `text`, `productCards`, `comparison`, `orderSummaries`, `purchaseListLines`, `recommendations`, `welcomeItems`, `clarification` |
| `ProductCardData` | `code`, `name`, `imageUrl`, `priceFormatted`, `stockLabel`, `stockIsDemo`, `reasonSlots` |
| `ComparisonData` | `columns`, `rows` (attribute label, values), `notes` |
| `OrderSummaryData` | `code`, `placedOn`, `statusLabel`, `totalFormatted`, `consignments`, `trackingReference`, `statusSource` |
| `PurchaseListLineData` | `productCode`, `quantity`, `note`, `origin` |
| `TraceData` | `steps` (label, source, window, threshold), whitelisted only |
| `ChatStreamEventData` | `event`, `stepId`, `labelKey`, `status`, `block`, `delta`, `code` |
| `FeedbackData`, `WelcomePanelData`, `PurchaseListResultData`, `ChatPublicConfigData` | per endpoint |

One flat `ChatBlockData` with typed optional sub-lists instead of inheritance **[A]**.

### OCC contracts (ccocc)

All endpoints: `@Controller`, `@RequestMapping("/{baseSiteId}/users/{userId}")`, `@ApiVersion("v2")`, `extends BaseController`, `@Secured({ ROLE_CUSTOMERGROUP })`, `@Operation`, `@ApiBaseSiteIdAndUserIdParam`. `UserBindingGuard` rejects any `userId` other than `current` or the principal's uid with 403 and an audit line.

| Method and path | Purpose | Request | Response |
| --- | --- | --- | --- |
| `POST /chat` | Non-streaming fallback, sample style | `ChatRequestWsDTO` | `ChatResponseWsDTO` |
| `POST /chat/stream` | Streaming turn | `ChatRequestWsDTO` | `text/event-stream` |
| `GET /chat/welcome` | Proactive panel (RI-03) | none | `WelcomePanelWsDTO` |
| `GET /chat/config` | Pack theme tokens, terminology, sample prompts, enabled capabilities, notice text | none | `ChatPublicConfigWsDTO` (no secrets) |
| `POST /chat/feedback` | Thumbs (CF-07) | `FeedbackWsDTO` | 200 |
| `POST /chat/purchase-list/validate` | Validate, price, check client-held list | `PurchaseListWsDTO` | `PurchaseListResultWsDTO` |

**Stream events** (SSE `event:` name, JSON `data:`): `step_started`, `step_completed`, `block`, `text_delta`, `done`, `error`; comment `: hb` heartbeat. After `done` the emitter completes. Events are `ChatStreamEventWsDTO` mapped with `getDataMapper()` and `fields`. **Branch from S2:** if the mapper ignores field filters in a yocc extension, map with the default field set and filter in the facade (owner approval, D37). Implementation: Spring `SseEmitter`/`ResponseBodyEmitter` **[V]** with config timeout; facade runs the turn on a bounded executor and calls a `ChatEventListener`; the controller adapts it to `emitter.send(...)`; the non-streaming endpoint uses a collecting listener so both paths share one orchestration. **Verify in S1:** no filter, CORS layer or response wrapper buffers the stream.

**Errors:** facade throws `IllegalArgumentException` for invalid input as in the sample. Controller-level exception handling was not shown: do not invent; request the owner's example; until then the OCC default handler returns 400.
