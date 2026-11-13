# UC1 Design, continuation D: Coding Convention Reference, Claude Code implementation prompt, open items (sections 5, 6 and 7)

---

# 7. Coding Convention Reference (carried complete from the handoff, mandatory)

**Provenance:** owner-supplied screenshots of sample code (a chat/controller/facade/service example plus a DAO example). The owner states they are samples but correct. Patterns are binding. The class names and the `fpp`/`Cpp` prefixes are placeholders and must not be assumed for the accelerator; the owner has named `com.conversational.commerce` as the public-repo package base (A6). Several lines are truncated in the screenshots: request a clearer example before relying on those details.

**Mandatory instruction:** every later stage follows these conventions. Claude Code must inspect the owner's supplied examples and follow them. Anything marked "Not visible / unknown" is requested from the owner, not invented.

**Legend:** *Confirmed from screenshots* = directly visible. *Likely convention requiring validation* = inferred. *Not visible / unknown* = not seen.

| Area | Observation | Status |
| --- | --- | --- |
| Package naming | Base prefix `com.fpp`. DTO beans: `com.fpp.facades.data.chat.*` (facade DTOs) and `com.fpp.occ.chat.*` (OCC ws DTOs). | Confirmed from screenshots |
| Class naming | Interface + Default implementation: `ChatFacade`/`DefaultChatFacade`, `ChatService`/`DefaultChatService`; DAO interface + DaoImpl (`CppDeliveryIssueDao`/`CppDeliveryIssueDaoImpl`). Facade DTOs end in `Data`; OCC DTOs end in `WsDTO`. | Confirmed from screenshots |
| Prefix usage | `fpp` appears on packages and Spring bean names; `Cpp` appears on a DAO/model example. Whether these are two intentional prefixes is not known. | Likely convention requiring validation |
| DTO definition | DTOs declared in XML beans files: `facade-beans.xml` for `*Data` and `occ-beans.xml` for `*WsDTO`, with property name and type (e.g. `java.lang.String`). | Confirmed from screenshots |
| Controller structure | `@Controller`, `@RequestMapping("/{baseSiteId}/users/{userId}")`, `@ApiVersion("v2")`, class extends `BaseController`. | Confirmed from screenshots |
| Endpoint annotations | `@Secured({ROLE_CUSTOMERGROUP, ROLE_CUSTOMERMANAGERGROUP, ROLE_TRUSTED_CLIENT})`, `@PostMapping(value, consumes = APPLICATION_JSON_VALUE)`, `@ResponseBody`, `@ResponseStatus(HttpStatus.OK)`, `@Operation(operationId, summary, description)`, `@ApiBaseSiteIdAndUserIdParam`. | Confirmed from screenshots |
| Endpoint parameters | `@Parameter(description, required) @RequestBody final XxxWsDTO request`; `@ApiFieldsParam @RequestParam(defaultValue = DEFAULT_FIELD_SET) final String fields`. | Confirmed from screenshots |
| Controller mapping | `getDataMapper().map(source, Target.class[, fields])` to convert WsDTO <-> Data; controller calls the facade only. | Confirmed from screenshots |
| Controller exception handling | No controller-level exception handling visible. | Not visible / unknown |
| Dependency injection | `@Resource(name = "beanName")` on private fields for facade/service in controller/facade; plain `@Resource` for services in service/DAO. Constructor injection not seen. | Confirmed from screenshots |
| Facade design | Interface method takes/returns `*Data`; implementation validates input (null/blank via `StringUtils.isBlank`, max length constant) and delegates to a service. Facade throws `IllegalArgumentException` for invalid input. | Confirmed from screenshots |
| Service design | Interface with simple method contracts; Default implementation uses constants for config keys, `validateParameterNotNull(...)` for arguments, `ConfigurationService` for properties. | Confirmed from screenshots |
| Configuration | Property keys as `private static final String` constants read via `ConfigurationService.getConfiguration()` with defaults (`getString`/`getInt`). Safe defaults in `project.properties`; secrets only in `local.properties` (gitignored), Cloud Portal secret property, or environment variable. | Confirmed from screenshots |
| External HTTP calls | `java.net.http.HttpClient` (shared, with connect timeout), per-request timeout from config, Jackson `ObjectMapper` for building/reading JSON (never string concatenation). | Confirmed from screenshots |
| Error handling (service) | Non-200 -> `LOG.warn` with status only (body may echo user content) then throw `SystemException`; missing/null response content -> `SystemException`; `InterruptedException` -> restore interrupt flag. Remaining catch blocks truncated. | Confirmed from screenshots |
| Logging | Logger `LOG = LogManager.getLogger(Class.class)`; log status/metadata only, not sensitive bodies. | Confirmed from screenshots |
| Validation patterns | Facade-level input validation with `IllegalArgumentException`; service/DAO-level `validateParameterNotNull` with messages like "X must not be null". | Confirmed from screenshots |
| DAO design | Interface returns models or `SearchPageData<Model>`; Impl uses `FlexibleSearchService` for single lookups and `PagedFlexibleSearchService` with `SortQueryData` for paged queries; customer passed to the DAO and filtered in the query. | Confirmed from screenshots |
| FlexibleSearch conventions | Queries as `private static final String` constants built from model constants (`Model._TYPECODE`, `Model.FIELD`, `ItemModel.PK`) with `{r:...}` alias syntax; all values as named parameters (`?param`) via Map or `FlexibleSearchQuery.addQueryParameter`; nothing from the request concatenated into query text. | Confirmed from screenshots |
| Dynamic filters | `buildFilterClause(params, filters, ...)` appends `AND` clauses with a `StringBuilder` and populates the params map; null/blank checks; default "active only"; dates truncated, `toDate` handled as exclusive next day. | Confirmed from screenshots |
| Sorting | `createSortQuery(sortCode, query)` helper builds `SortQueryData`; list of sort options; unknown sort code falls back to `DEFAULT_SORT`. | Confirmed from screenshots |
| Result handling | `CollectionUtils.isNotEmpty(result.getResult()) ? result.getResult().get(0) : null` for single lookups; blank input returns null. | Confirmed from screenshots |
| Existing model reuse | Examples reference OOTB `ItemModel`, `OrderModel`, `ProductModel`, `CustomerModel`, `EnumerationValueModel`, plus custom `Cpp*` models. Reuse of OOTB models for orders/products/customers is visible. | Confirmed from screenshots |
| Future-scope comments | Owner convention: add comments where future scope exists (e.g. unit-level access, unit-level replenishment, live stock, real cart). | Confirmed from screenshots |
| Formatting | Allman braces on new lines for blocks; `final` on parameters/locals; Javadoc/comment style not visible. Some single-line `if` with braces on same line appears in `findByReportNumber`. | Likely convention requiring validation |
| Spring bean XML | Service/DAO/facade bean definitions (`xxx-spring.xml`) and OCC web spring config not visible. | Not visible / unknown |
| items.xml | No `items.xml` example visible. | Not visible / unknown |
| Populators/converters | No populator or converter examples visible; mapping shown only via `getDataMapper()` and manual Data construction. | Not visible / unknown |
| Unit/integration tests | No tests visible. | Not visible / unknown |
| Frontend | No Angular/Spartacus examples supplied yet. Ask the owner for examples if frontend conventions must be preserved. | Not visible / unknown |

### Code excerpts transcribed from the owner's screenshots

DTO beans (`facade-beans.xml` / `occ-beans.xml`):

```xml
<bean class="com.fpp.facades.data.chat.ChatRequestData">
    <property name="message" type="java.lang.String"/>
</bean>
<bean class="com.fpp.occ.chat.ChatResponseWsDTO">
    <property name="reply" type="java.lang.String"/>
</bean>
```

Controller (excerpt; some lines truncated in the screenshot):

```java
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}")
@ApiVersion("v2")
public class ChatController extends BaseController
{
    @Resource(name = "fppChatFacade")
    private ChatFacade chatFacade;

    @Secured({ SecuredAccessConstants.ROLE_CUSTOMERGROUP, SecuredAccessConstants.ROLE_CUSTOMERMANAGERGROUP, SecuredAccessConstants.ROLE_TRUSTED_CLIENT })
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    @Operation(operationId = "chat", summary = "...", description = "...")
    @ApiBaseSiteIdAndUserIdParam
    public ChatResponseWsDTO chat(
            @Parameter(description = "Chat request", required = true) @RequestBody final ChatRequestWsDTO request,
            @ApiFieldsParam @RequestParam(defaultValue = DEFAULT_FIELD_SET) final String fields)
    {
        final ChatRequestData requestData = getDataMapper().map(request, ChatRequestData.class);
        final ChatResponseData responseData = chatFacade.chat(requestData);
        return getDataMapper().map(responseData, ChatResponseWsDTO.class, fields);
    }
}
```

Facade validation:

```java
if (request == null || StringUtils.isBlank(request.getMessage()))
{
    throw new IllegalArgumentException("message must not be empty");
}
final String message = request.getMessage().trim();
if (message.length() > MAX_MESSAGE_LENGTH)
{
    throw new IllegalArgumentException("message must not exceed " + MAX_MESSAGE_LENGTH ...);
}
```

DAO query constant and named parameters:

```java
private static final String FIND_REPORT_BY_NUMBER =
    "SELECT {r:" + ItemModel.PK + "} FROM {" + CppDeliveryIssueReportModel._TYPECODE + " AS r}"
    + " WHERE {r:" + CppDeliveryIssueReportModel.REPORTNUMBER + "}=?reportNumber"
    + " AND {r:" + CppDeliveryIssueReportModel.CUSTOMER + "}=?customer";

final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_REPORT_BY_NUMBER);
query.addQueryParameter("reportNumber", reportNumber);
query.addQueryParameter("customer", customer);   // transcribed approximately; first query line partly truncated in screenshot
```

### Conventions that conflict with the design, and how the design handles them

| Gap or conflict from the handoff | Handling in this design |
| --- | --- |
| Streaming vs the sample controller that returns one mapped WsDTO | Second endpoint `POST /chat/stream`; events are `ChatStreamEventWsDTO` mapped one by one; non-streaming `POST /chat` keeps the sample shape (S2 may force a variation: owner approval) |
| Sample `@Secured` includes `ROLE_TRUSTED_CLIENT` and `ROLE_CUSTOMERMANAGERGROUP` | A5: customer group only, plus `UserBindingGuard` |
| PA-01 "backend maintains list" vs D07 | A11: list held in the browser, validated by the backend |
| CI-04 "AI classifies events" vs D08a | A9: AI suggests an enum; the backend decides and writes |
| D13 vs security-critical capabilities | A1: fail-closed semantics |
| PL-03 "never appear" vs free text | Guarantee for backend-supplied data; free text best-effort (criterion rewording listed in section 6) |
| SC-10 "reload" vs PL-07 "live" | Config switch + cache TTL + reload |
| D08 dedicated table vs sharing | A7: two tables, shared Java purge base class only |
| javax `@Resource` in sample vs Jakarta platform | A12: assumed 2211-jdk21 (Jakarta). Claude Code checks the owner's imports and matches them |
| `@Operation`/`@Parameter` imply recent 2205+/2211 | Inference only; confirmed by A12 |
| No controller exception handling, Spring XML, items.xml, populator, test or Angular examples | Requested from the owner (section 6); nothing invented |

---

# 5. Claude Code implementation prompt

Paste everything between the two lines of `=====` into Claude Code as the first message of the project. Run the stages in order; each stage ends with a check you can read.

=====

## UC1 build prompt for Claude Code

### 0. Who you are building for
You are implementing "UC1 – SAP Commerce AI-First Conversational B2B Commerce Accelerator" as a public GitHub repository that clients drop into their own OCC, facade and core layers. AI is the interpreter and communicator; SAP Commerce Java is the executor of record. You write production code, tests and docs in stages, and stop at every checkpoint marked STOP.

### 1. Non-negotiable rules
1. **Inspect first.** Before writing any code, read the owner-supplied example code (the chat controller/facade/service example and the DAO example) and the Coding Convention Reference (section 7 of the design). Follow those patterns exactly: package naming, `Interface` + `DefaultX`, `XxxDao` + `XxxDaoImpl`, `*Data` beans in `facade-beans.xml` and `*WsDTO` beans in `occ-beans.xml`, `@Resource` injection, constants for property keys and queries, `validateParameterNotNull`, `IllegalArgumentException` in facades, `LOG` with status only, Allman braces, `final` on parameters and locals. Sample class names and the `fpp`/`Cpp` prefixes are placeholders. Use `com.conversational.commerce` as the package base, `cc` as the bean prefix and `CC` as the item-type prefix, unless the owner says otherwise.
2. **Never invent a style.** Where the reference says "Not visible / unknown" (controller exception handling, Spring bean XML, `items.xml`, Backoffice config, populators/converters, tests, Angular/Spartacus, Javadoc style), STOP and ask the owner for the example listed in section 6 of the design. Do not guess.
3. **Protected decisions D01–D37 are fixed.** If your work seems to need a change to one, STOP, write the evidence and options in `docs/owner-approvals.md`, and ask. Never change one silently.
4. **Security rules (always):** the model never builds or runs a query, never sees prices, totals, margin, priority, names, emails or other customers' data, never gets a tool that writes, and never places orders. Customer identity always comes from the Spring Security context, never from a request body or a model output. Every external model call goes through the sanitiser; if the sanitiser is off, calls fail closed. No key, real data or client-specific model in the repo. Logs never contain prompts, message text or personal data.
5. **Mark future scope** with a `// FUTURE SCOPE:` comment at each hook: unit-level order access (D33), unit-level replenishment (D34), live stock, real cart, carrier tracking.
6. **Label demo data** wherever it is shown (demo stock, placeholder purchase list).
7. **Verify before relying.** Check platform and provider facts against current documentation before use: model IDs and parameters for Gemini, OpenAI and Anthropic; the yocc template and OCC extension wiring; Spartacus APIs for your version. Record what you checked in `docs/verification-log.md` with source and date.

### 2. Repository layout
```
/
├── README.md, LICENSE, SECURITY.md, CONTRIBUTING.md
├── .gitignore                    # includes local.properties, *.key, .env
├── .github/workflows/            # build, tests, secret scan, pack check
├── docs/                         # architecture, extension guide, spikes/, verification-log.md, owner-approvals.md
├── tools/packgen/                # deterministic pack generator + manifests/ + schema/
├── backend/                      # SAP Commerce custom extensions
│   ├── cccore/                   # items.xml, services, DAOs, orchestrator, SPIs, adapters, sanitiser, rules, audit
│   ├── ccfacades/                # facade-beans.xml, facades
│   ├── ccocc/                    # yocc-generated: controllers, occ-beans.xml, web spring
│   ├── ccbackoffice/             # backoffice config
│   └── ccsampledata/             # generated ImpEx, SampleOrderLoader, @SystemSetup
└── storefront/
    └── chat-feature/             # Spartacus lazy-loaded feature library
```

### 3. Stages, prompts and checks

**Stage 0 – Intake (STOP at the end).**
Read the examples and the design. Produce `docs/convention-digest.md` summarising, in your own words, each confirmed convention and listing every unknown. Ask the owner for: controller error handling, Spring bean XML (service, DAO, facade, OCC web), `items.xml` and Backoffice samples, populator/converter sample, test samples, Angular/Spartacus samples and lint rules, Javadoc/brace confirmation, SAP Commerce / Spartacus / Angular / Node versions and javax vs jakarta, and the meaning of `fpp` vs `Cpp`.
*Check:* digest exists; every unknown has a named request; no code written.

**Stage 1 – Repository guardrails.**
Create the layout, `.gitignore`, secret scanning and push protection config, CI skeleton, `docs/` stubs and a `SECURITY.md`. Add the property-key constants file and a `project.properties` with safe defaults only.
*Check:* CI runs; a planted fake key in a test branch is caught by the scan; no real key anywhere.

**Stage 2 – Spikes S1, S2, S3 (STOP at the end).**
Build the thinnest throwaway versions inside `spikes/` branches: a streaming endpoint in a yocc-generated `ccocc` and an Angular fetch reader (S1); DataMapper field filtering for chat DTOs and stream events (S2); a schema-constrained decision call per provider over `HttpClient` + Jackson (S3). Record outcomes in `docs/spikes/S1.md`, `S2.md`, `S3.md` using the pass criteria in the design.
*Check:* each file states pass/fail, numbers and the design branch taken. If S1 chooses polling, or S2 requires a departure from `getDataMapper()`, STOP and ask for owner approval.

**Stage 3 – Core types and configuration.**
In `cccore`: the six item types and four enums, `ConfigurationService` with the three-layer read, cache TTL and fail-closed rules, Backoffice config in `ccbackoffice` (use the owner's sample), essential-data seeding of all capability toggles.
*Check:* platform builds and initialises; toggles editable in Backoffice and effective within the TTL; disabling a protected capability is refused; unit tests for config precedence.

**Stage 4 – Deterministic data services.**
DAOs (`CcOrderDao`, `CcProductSearchDao`, `CcCoPurchaseDao`, and the small ones) following the DAO convention exactly; gateways with default implementations; tools and rules: search, details, comparison, alternatives, budget eligibility, quick-order validation, orders, history, replenishment, pattern change, complementary, bundles, budget planner, basket review, FAQ, demo stock. Each carries `// FUTURE SCOPE:` where required.
*Check:* unit tests per rule; integration tests for every customer-scoped DAO as customer A vs B (S7 begins here); property test that the budget planner never exceeds the budget; no query built from request text.

**Stage 5 – Sanitiser, provider adapters, router.**
`FieldPolicy`, `EgressPolicy`, masking rules, `OutboundGuard`, token maps; three adapters and `ModelRouter` with the `syntheticDataOnly` guard, fallback and token counting; prompts and strict schemas as resource files; a deterministic fake provider for tests.
*Check:* canary tests: a unique price, margin, email and order code placed in test data never appear in any captured outbound payload; provider contract tests pass from recorded fixtures; no key needed in CI.

**Stage 6 – Orchestrator.**
`ToolRegistry`, `WorkflowRegistry`, `ChatOrchestrationService`, `DecisionValidator`, limits, parallel groups, provenance check, slot filler, text-vs-table check, `TraceBuilder`, audit events, usage caps, interest-event service, retention jobs, welcome service.
*Check:* each of the ten scenarios runs end to end against the fake provider and produces the expected blocks; unknown tool rejected; step and time caps stop a looping fake; audit lines contain no prompts or personal text.

**Stage 7 – Facades and OCC.**
`ChatFacade` (validation and mapping per the sample), `FeedbackFacade`, `WelcomeFacade`, `PurchaseListFacade`; `ChatController` and siblings with the sample's annotations except `@Secured({ ROLE_CUSTOMERGROUP })`; `UserBindingGuard`; streaming endpoint with heartbeat and timeout; non-streaming fallback; `/chat/config`.
*Check:* an HTTP test with a customer token: `userId` of another user returns 403; stream events arrive incrementally; the fallback returns the same blocks.

**Stage 8 – Frontend.**
Using the owner's Angular/Spartacus samples, build `chat-feature` per the design: route, store, fetch streaming client with fallback, all block components, purchase list, feedback, why-this panel, interest notice, theming, accessibility.
*Check:* component tests, SSE parser fixtures, axe checks pass; e2e of SC-01 against the fake backend; keyboard-only walk-through works; reduced-motion respected.

**Stage 9 – Packs and seed data.**
`tools/packgen`, manifests and schema for the five packs, committed generated ImpEx, `SampleOrderLoader` with relative dates, `@SystemSetup` options, all fixtures in design section 2b.4.
*Check:* regeneration is byte-identical in CI; all five packs load together; S5 recorded; SC-10 switch works after TTL and reload.

**Stage 10 – Hardening and measurement.**
Run S4, S6, S7, S8; injection corpus; load test with the fake provider; fix findings.
*Check:* S4 numbers recorded against the 10-second target; S7 shows zero leaks; STOP and report any result that triggers an owner-approval branch (A3, S5 alternative).

**Stage 11 – Documentation and handover.**
README, extension guide (how to add a tool, workflow, rule, gateway), provider-terms note (free-tier Gemini is for synthetic data only; paid or ZDR terms for client data; Anthropic Fable/Mythos models are excluded from ZDR), demo script for SC-01 to SC-10, upgrade notes for Spartacus/Angular.
*Check:* a new developer can add a sample tool following only the guide.

### 4. Definition of done for every stage
Code builds; tests pass; no new warnings from the owner's lint rules; conventions digest followed; `docs/owner-approvals.md` updated; one short summary of what changed and what was verified.

=====

---

# 6. Open items and outstanding owner approvals

## 6.1 Owner approvals still needed (nothing here has been applied)

| # | Item | Touches | Evidence and options | Default if you say nothing |
| --- | --- | --- | --- | --- |
| N1 | Confirm derived names: bean prefix `cc`, item types `CC*`, extensions `cccore`, `ccfacades`, `ccocc`, `ccbackoffice`, `ccsampledata` | D37, A6 | You named only `com.conversational.commerce` | Use as derived |
| N2 | Reword PL-03 acceptance criterion: guarantee applies to backend-supplied data; user-typed text is masked best-effort and the UI warns against typing personal data | PL-03 criterion (not a D item) | Feasibility finding; no filter can guarantee free text | Reword |
| N3 | Welcome-panel optional cached LLM phrasing held in memory with a short TTL, keyed by customer and data hash | D07 (no persisted history) | Keeps panel fast; memory only, never stored. Alternative: template-only text | Template-only; cache off |
| N4 | Spike branches that need approval if triggered: S1 polling run buffer (D07), S2 non-mapper field filtering (D37), S4/S8 compose-in-language (D17, A3), S5 one site per pack (D32) | D07, D37, D17, D32 | Each is described in section 4 with its trigger | Not adopted unless a spike fails |
| N5 | Languages for S8 beyond Hindi | CF-05 | Proposal: add two the owner picks | Hindi only |
| N6 | Interest-event notice wording and per-market consent approach, with legal review | D08, A8 | Configurable text field already designed | Placeholder text |
| N7 | Platform versions (A12) | constraints | Assumed 2211-jdk21, Jakarta, Angular 19, Node 22 **[A]** | Assumed until you confirm |

## 6.2 Examples needed from the owner (before the stage that depends on them)

1. Controller-level exception handling / error response convention (Stage 7).
2. Spring bean XML for service, DAO, facade and OCC web config (Stage 3).
3. `items.xml` sample and Backoffice config sample (Stage 3).
4. Populator/converter sample (Stage 7).
5. Unit and integration test samples (Stage 4).
6. Angular/Spartacus component, module, service and SCSS samples and lint rules (Stage 8).
7. Javadoc/comment style and Allman brace confirmation (Stage 0).
8. Platform, Spartacus, Angular, Node versions; javax vs jakarta (Stage 0).
9. Intended meaning of `fpp` vs `Cpp` and the public-repo prefix (resolved in part: package base given).
10. A clearer copy of the truncated service error-handling catch blocks and the full DAO query (Stages 4 and 5).

## 6.3 Evidence still unverified or conflicting

| Item | Status | Where it blocks |
| --- | --- | --- |
| E04 free-tier Gemini numbers | Structure verified; numbers not verified | S3, S4 (record real numbers) |
| E12 DataMapper in custom OCC extension | Secondary | S2 |
| E14 CCv2 ingress/CDN handling of SSE | Not verified, a gap | S1 |
| E16 OWASP LLM Top 10 2025 | Secondary | Security table in design; confirm against OWASP's own page |
| E17 ImpEx essential/project data conventions | Secondary | Stage 9 |
| E18 Backoffice config pattern | Secondary | Stage 3, until the owner's sample arrives |
| E19 single-site pack modelling | Not verified | S5 |
| E07 Anthropic retention | 30 days verified; a 7-day claim on third-party sites is unresolved and not to be quoted. Fable 5 / Mythos 5 require 30-day retention and exclude ZDR (verified 2026-11-13) | Provider notes |
| E10 newer Composable Storefront releases | Only 2211.19–43 and 221121.x checked | A12 |

Provider terms and platform versions may change after 2026-11-13; re-check at design freeze and before any client demo with real data.
