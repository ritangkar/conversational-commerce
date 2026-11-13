# Capability status (all 43 IDs)

Legend: **Engine-tested** = covered by a passing test in `backend/cccore/testsrc` or `storefront/chat-feature/test` on dummy data, run in this repository. **Written, not run** = code exists but needs a SAP platform, a browser or a real provider to run. **Not built** = missing. Every ID has its toggle `capability.<ID>.enabled` seeded by essential data (`tools/packgen/test_generate.py` checks 43 toggles).

| ID | Code | Status |
| --- | --- | --- |
| CF-01 | `storefront/chat-feature/src/lib/ui/chat-page.component.ts` | Written; Angular strict-template compile passes; no browser run |
| CF-02 | `DecisionValidator`, `CLARIFY`, `FindProductWorkflow` clarification | Engine-tested (`ScenarioTest`, `SecurityTest`) |
| CF-03 | `Blocks`, `block-renderer.component.ts` | Engine-tested server side; UI compile only |
| CF-04 | `session-persistence.ts`, `chat.store.ts` | Core tested (vitest); logout clearing written, not run in Spartacus |
| CF-05 | `TranslateStep`, `LanguageDetector` | Engine-tested with fake translator only (SC-09); S8 not run |
| CF-06 | `StaticWorkflows`, contact text, `unavailable` notice | Engine-tested |
| CF-07 | `feedback-controls.component.ts`, `DefaultFeedbackFacade` | Written, not run |
| CF-08 | `TraceBuilder` role is played by `TraceStep` in tools; `why-this-panel.component.ts` | Engine-tested (trace present); UI compile only |
| PD-01 | `SearchProductsTool` | Engine-tested (SC-02) |
| PD-02 | `SearchProductsTool` filters, whitelist in `DecisionValidator` | Engine-tested |
| PD-03 | `ProductDetailsTool`, `ProductQaWorkflow` | Written; no dedicated scenario test |
| PD-04 | `ComparisonTool`, `CompareWorkflow`, `TextTableChecker` | Engine-tested (SC-07) |
| PD-05 | attribute mapping in manifests; compatibility compare via attributes | Partial: data present, no compatibility-specific logic or test |
| PD-06 | `AlternativesTool`, `StockInfoTool` | Engine-tested (via SC-03 replacement) |
| PD-07 | `BudgetEligibleTool` | Engine-tested (SC-02) |
| PA-01 | product card add, `purchase-list.component.ts` | UI compile only |
| PA-02 | `ChatStore`, `DefaultPurchaseListFacade` | Core tested (quantities); facade not run |
| PA-03 | `QuickOrderParser`, `QuickOrderValidateTool` | Engine-tested (SC-03) |
| PA-04 | `BudgetPlanner`, `PlanBudgetTool` | Engine-tested incl. 2,000-case property test (SC-04) |
| PA-05 | quantities from history averages (`BudgetPlanWorkflow`, `ReplenishmentRule`) | Engine-tested indirectly |
| PA-06 | `BasketReviewRule`, `ReviewListTool` | Engine-tested (SC-08) |
| PA-07 | `ReorderWorkflow` | Written; one canary/isolation use, no happy-path scenario test |
| OM-01 | `OrderTools` | Engine-tested (SC-06) |
| OM-02 | `SearchOrdersTool`, `OrderLookupWorkflow` | Engine-tested (SC-06) |
| OM-03 | FAQ + order summaries | Written; FAQ not scenario-tested beyond the workflow |
| RI-01 | `ReplenishmentRule`, `ReplenishmentTool` | Engine-tested (SC-01) |
| RI-02 | `PendingOrdersTool`, netting | Engine-tested (SC-01) |
| RI-03 | `WelcomeService`, `welcome-panel.component.ts` | Engine-tested (SC-05) |
| RI-04 | `PatternChangeRule`, `PatternChangeTool` | Engine-tested (SC-05) |
| RI-05 | `NextOrderWorkflow` | Engine-tested (SC-01) |
| CI-01 | `ComplementaryTools`, `CoPurchaseRule` | Engine-tested (SC-01, SC-08) |
| CI-02 | `BundleSuggestionsTool` | Written; not scenario-tested |
| CI-03 | `BusinessRanker` | Engine-tested (canary: margin never leaves) |
| CI-04 | `InterestEventService`, `CCInterestEvent` | Engine-tested (switch, no text) |
| KN-01 | `FaqTool`, `FaqWorkflow` | Written; not scenario-tested |
| PL-01 | `DefaultCcConfigurationService`, items, Backoffice config | Engine-tested (layers, TTL, fail-closed); Backoffice not run |
| PL-02 | adapters, `DefaultModelRouter` | Contract-tested on synthetic fixtures; real provider shapes UNVERIFIED (S3) |
| PL-03 | `DefaultSanitiser`, `OutboundGuard`, `SanitisedPayload` | Engine-tested (canaries) |
| PL-04 | `ToolRegistry`, `WorkflowRegistry`, `DecisionValidator` | Engine-tested |
| PL-05 | `AuditEvent` whitelist, `Log4jAuditSink` | Engine-tested (no text, no personal data) |
| PL-06 | `UsageCapService` | Engine-tested |
| PL-07 | `tools/packgen`, `CcSampleDataSystemSetup` | Generator tested; ImpEx and loader never loaded into a platform (S5 not run) |
| PL-08 | SPIs, alias pattern in `cccore-spring.xml`, `docs/extension-guide.md` | Written; guide not yet followed by a new developer |
