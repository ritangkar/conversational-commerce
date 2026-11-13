# UC1 Design, continuation C: traceability and spike plan (sections 3 and 4)

## 3.1 Capability-to-design traceability (all 43 IDs)

"Toggle" is the Backoffice key `capability.<ID>.enabled` in every row. Rows marked **fail-closed** follow A1.

| ID | Design element | Tools, workflow or SPI | Acceptance check (automated unless noted) |
| --- | --- | --- | --- |
| CF-01 | Chat route, `POST /chat/stream`, `POST /chat` fallback, step events, ARIA live regions | All workflows via `ChatOrchestrationService` | e2e: page opens for a dealer; steps appear; text reveals progressively; keyboard walk-through (manual); toggle off hides the route |
| CF-02 | `DECIDE` task, `DecisionValidator`, clarification counter | `DecisionSchema`, catalogue check | Vague prompt gets at most `clarification.maxQuestions` questions; invented tool rejected; intent and confidence in audit |
| CF-03 | `BlockRenderer`, response contract, provenance check, hydration | `getProductsByIds`, `PriceGateway` | Fake-model output with a fake ID drops it and audits `id_rejected`; card price equals the Commerce price; no model payload contains a price |
| CF-04 | `sessionStorage` store, `ContextEnvelopeBuilder`, egress re-sanitising | none | Refresh keeps the thread; logout and tab close clear it (manual); no server-side transcript table exists |
| CF-05 | `TranslateStep`, placeholders, merged detect+translate+classify | `translateText`, `ModelRouter` | Identifiers survive (S8); English flow identical with toggle off; reply language matches |
| CF-06 | `OFF_TOPIC`/`UNSUPPORTED`/`CLARIFY` workflows, `contact.text` | no tools | Off-topic redirected; unsupported declined; contact text shown only when configured and unresolved |
| CF-07 | `CCFeedback`, `POST /chat/feedback`, `FeedbackControlsComponent` | `FeedbackFacade` | Row has rating, response id, intent, tools, no text; toggle off hides controls |
| CF-08 | `TraceBuilder`, `WhyThisPanelComponent` | Whitelisted `TraceData` | Canary margin and prompt text never appear in a trace; panel lists sources and steps |
| PD-01 | `CcProductSearchDao`, `CandidateProvider`, `JUDGE` | `searchProducts` | Code, description, use-case and constraint prompts return grounded results; no model-built query runs; thin data produces a clarifying question |
| PD-02 | Category tree and facets through `CandidateProvider` | `searchProducts` (facets) | Browse and filter by conversation on every pack |
| PD-03 | Approved-field list in config | `getProductDetails` | Every stated attribute traces to a retrieved field; missing data stated |
| PD-04 | Deterministic comparison builder, text-versus-table check | `getComparisonAttributes` | Table values equal backend data; contradictory text replaced by template |
| PD-05 | Configured compatibility attribute | `getProductDetails` + compatibility rule | With the attribute present: a verdict; absent: "cannot verify" |
| PD-06 | Similarity rules, `StockGateway` demo | `getAlternatives`, `getStockInfo` | Alternatives come only from retrieved candidates; demo stock labelled |
| PD-07 | `PriceGateway` eligibility, candidate cap | `getBudgetEligibleProducts` | Canary price absent from all captured outbound payloads; results respect the dealer's price |
| PA-01 | Client-held list, `POST /chat/purchase-list/validate`, `PurchaseListGateway` | none for the action | Label visible; no AI path adds items or orders |
| PA-02 | Editable list in `ChatStore`, server re-validation | `reviewPurchaseList` input | Edits persist across refresh in the tab; invalid lines flagged |
| PA-03 | Grammar parser, residual model parse, validation ladder | `validateQuickOrderLines` | Every line validated; unresolvable line reported, not guessed |
| PA-04 | Bounded knapsack in minor units | `planBudgetPurchase` | Total never exceeds budget (property test); exclusions listed |
| PA-05 | Median or recent-mean quantity rule | `getReplenishmentRecommendations` | Labelled as estimate; rule unit-tested |
| PA-06 | `BasketRules` | `reviewPurchaseList` | Each flag carries a data trace; toggling a recommendation type changes the flags |
| PA-07 | Order-to-list mapper | `getOrderById`, `getAlternatives`, `getStockInfo` | Only the caller's own orders; discontinued line gets a substitute |
| OM-01 | `CcOrderDao`, `OrderGateway` | `getLatestOrder`, `getOrderById`, `getOrderHistory`, `getOrderTracking` | Customer A cannot read B (S7); stored status distinguished from live carrier data |
| OM-02 | `OrderSearchFilter`, `buildFilterClause`, `PagedFlexibleSearchService` | `searchOrders` | Filters validated; date and currency ambiguity triggers a clarifying question |
| OM-03 | Summary from order DTO, FAQ link | `getFaqAnswer` | Summary values equal backend values |
| RI-01 | `ReplenishmentRule` | `getReplenishmentRecommendations` | Median gap, minimum orders, due/overdue; facts separated from estimates; per-user only |
| RI-02 | Pending netting | `getPendingOrders` | Suggested quantity subtracts pending quantity |
| RI-03 | `WelcomeService`, `GET /chat/welcome`, template-first text | RI-01, RI-02, RI-04 | Panel shows grounded items only and hides when empty |
| RI-04 | `PatternChangeRule` | `detectPurchasePatternChanges` | Observation shows the numbers; no cause stated (phrase test) |
| RI-05 | List assembler | RI-01 + RI-02 | Customer approves each addition; nothing is added silently |
| CI-01 | `CoPurchaseRule`, category rules, `BusinessRanker` | `getComplementaryProducts` | Suggestions come from rules and counts; AI only explains |
| CI-02 | Bundle builder | `getBundleSuggestions` | Bundles suppressed on sparse data |
| CI-03 | `CCProductBusinessAttribute`, `BusinessRanker` after the LLM stage, weights in config | ranker | Margin canary never in prompts, responses or trace; toggles apply without redeploy |
| CI-04 | `CCInterestEvent`, `InterestEventService`, notice, purge job | `recordInterestEvent` | Notice shown when on; only enums and codes stored; purge removes rows past retention |
| KN-01 | `CCConfigEntry` FAQ entries, lexical shortlist, model picks IDs | `getFaqAnswer` | Answer text equals the approved entry; none found is said plainly |
| PL-01 | `CCConfiguration`, `CCConfigEntry`, Backoffice config, layered reads. **Fail-closed** | `ConfigurationService` | Every capability has a toggle; change takes effect within the cache TTL; disabling a protected capability is refused |
| PL-02 | `ModelProviderAdapter` x3, `ModelRouter`, `syntheticDataOnly` guard | adapters | Routing changes by config only; no key in source or Backoffice; secret scan passes |
| PL-03 | `FieldPolicy`, `EgressPolicy`, `OutboundGuard`. **Fail-closed** | sanitiser | Canary tests pass; free text masked best-effort (criterion reworded, see section 6) |
| PL-04 | `ToolRegistry`, `WorkflowRegistry`, limits. **Fail-closed** | orchestrator | Unknown tool rejected; step and time caps stop loops |
| PL-05 | `cc.audit` logger, `AuditSink` SPI. **Fail-closed** | audit | Required fields present; no prompt or personal text in logs |
| PL-06 | `CCUsageCounter`, `UsageCapService` | pre-decision check | Cap reached gives a graceful notice and no model call |
| PL-07 | Pack manifests, generators, loaders, theme tokens, `activePack` | `SampleOrderLoader` | Switch changes theme, products, orders, prompts after TTL and reload (S5) |
| PL-08 | SPIs: `ChatTool`, `WorkflowTemplate`, `RecommendationRule`, gateways | Spring discovery by bean type | A sample client tool added in a test module works without core changes |

## 3.2 Scenario traceability (SC-01 to SC-10)

| ID | Workflow | Tools in order | Capabilities | Observable pass check | Spikes it exercises |
| --- | --- | --- | --- | --- | --- |
| SC-01 Next-order assistant | `NEXT_ORDER` | history ‖ replenishment ‖ pending → complementary → byIds | CF-01, CF-02, RI-01, RI-02, RI-05, CI-01, PA-05, PA-06, PA-01, PA-02, CF-08 | Suggested list with cards; due dates labelled as estimates; one item noted as already pending; add-ons with reasons; list awaits approval | S1, S4, S7 |
| SC-02 Need-based discovery | `FIND_PRODUCT` | search → budget → details → byIds | CF-02, PD-01, PD-02, PD-07, PD-04, CF-03 | Clarifying question, then 3 cards under budget; captured payloads contain no price | S3, S6 |
| SC-03 Messy quick order | `QUICK_ORDER` | validate lines → search → alternatives → stock | PA-03, PD-06, PA-02, PD-01 | Validated lines, one clarified, one substitution, one unresolvable line reported, editable list | S3 |
| SC-04 Budget planner | `BUDGET_PLAN` | history ‖ replenishment ‖ eligibility → plan | PA-04, PA-05, RI-01, PA-02, CF-03 | Budgeted list; Java totals ≤ 5 lakh; exclusions explained | S4 |
| SC-05 Pattern-change alert | `PATTERN_ALERT` | pattern change ‖ history | RI-03, RI-04, RI-01, CF-08 | Panel shows "about 40% below" with both numbers and no cause | none |
| SC-06 Order intelligence | `ORDER_LOOKUP` | search orders → latest ‖ tracking | OM-01, OM-02, OM-03, CF-03 | Summary cards; stored status vs carrier data stated; another dealer's order refused | S7 |
| SC-07 Comparison | `COMPARE` | byIds → comparison attributes | PD-04, PD-03, PD-05, CF-08, CF-03 | Table from backend; grounded recommendation; one "cannot verify" moment; trace panel opens | S3 |
| SC-08 Basket optimisation | `BASKET_REVIEW` | pending ‖ replenishment ‖ complementary | PA-06, RI-02, CI-01, CI-03, PA-02 | Four flags as seeded; toggling a recommendation type in Backoffice changes flags without redeploy | S5 |
| SC-09 Multilingual | `NEXT_ORDER` wrapped by translate | translate-in → SC-01 tools → translate-out | CF-05, RI-05, CF-01 | Hindi answer; codes unchanged; progress indicators shown | S4, S8 |
| SC-10 Industry switch | `SWITCH_PACK` | none | PL-07, PL-01, CF-01 | After the TTL and reload, theme, terminology, sample prompts, products and dealer data match the pack | S5 |

## 4 Spike plan (S1 to S8)

Order: S1 and S2 first (they decide transport and mapping), S3 in parallel, S5 and S6 next, S4 on the integrated flow, S7 and S8 continuous from the first tool. No durations are given because no benchmark exists; the owner sets the calendar.

| Spike | Method | Pass criteria | If it fails, the design changes to |
| --- | --- | --- | --- |
| **S1 Streaming path** | Minimal `ccocc` endpoint emits 20 events at 500 ms plus `: hb` heartbeats; Angular fetch client reads them; run locally, behind a buffering proxy, and on the target CCv2 route or equivalent | First event within 1 s; events arrive spaced, not in one burst; a 60 s idle stream survives on heartbeats; bearer auth works; cancel stops the server task within 5 s; route is not server-rendered; logout clears the store | 1) proxy and header settings (no buffering, no compression). 2) NDJSON over POST. 3) Polling: `POST` starts a run, `GET` reads events after a cursor, which needs a short-lived server-side run buffer and **owner approval** because it touches D07. The non-streaming `POST /chat` ships in every case |
| **S2 DataMapper** | Map `ChatResponseData` and `ChatStreamEventData` with `fields` values `DEFAULT`, `BASIC`, `FULL` in the yocc extension | Field filtering honoured for blocks and events | Facade-level field filtering, or Jackson mapping in the controller. Either departs from the sample's `getDataMapper()` pattern, so list it for **owner approval (D37)** |
| **S3 Provider adapters** | Decision call with a schema on Gemini, OpenAI and Anthropic, 50 prompts each; confirm current model IDs, limits and terms | At least 95% schema-valid on first try; latency recorded per task; fallback chain works; terms recorded | Add a repair retry; drop a provider from default routing; keep it available but off |
| **S4 Latency** | 20 runs each of SC-01 and SC-09 on a paid tier, with and without merged calls and parallel tools; record time to first step, per-stage time, total | p50 ≤ 8 s and p90 ≤ 10 s to `done`; first step within 1 s. Free-tier results are recorded, not gated | Merge calls; template-first explanations; cache welcome text; smaller models; reduce candidates. If SC-09 still misses, propose A3 (compose in language) for **owner approval (D17)** |
| **S5 Pack coexistence** | Load all five packs; switch `activePack`; test dealers, scoping, relative dates, TTL | Switch visible after TTL and reload; no cross-pack results; every pack's fixtures present | Separate catalogs per pack on one store; or one base site per pack, which breaks the single switch and needs **owner approval (D32)** |
| **S6 Search scale** | 50,000+ products with classification attributes; FlexibleSearch candidate query against a Solr provider | FlexibleSearch p95 ≤ 1 s for the candidate step with `limit`, else Solr is the default provider | Ship the Solr `CandidateProvider` as default for large catalogues |
| **S7 Isolation** | Automated: each customer-scoped tool and endpoint as customer A against B's data; `userId` mismatch; model-supplied customer args; injection prompts | Zero leaks | Block release; fix before any other work |
| **S8 Translation safety** | 200 strings with codes, order numbers and brands in Hindi plus two languages the owner picks | 100% identifier survival after at most one retry; reviewer acceptance at least 90% | Narrow the language list; fall back to English with a notice; A3 for **owner approval** |

**Recording results:** each spike writes a short result file in `docs/spikes/` stating the outcome, the numbers and the branch taken. A failed spike with an owner-approval branch stops the dependent build stage until the owner decides.
