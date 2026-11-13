# Capability status (all 43 IDs)

Legend: **Service-tested** = covered by a passing test in `backend/cccore/testsrc` or `storefront/chat-feature/test` on dummy data, run in this repository. **Written, not run** = code exists but needs a SAP platform, a browser or a real provider to run. **Not built** = missing. All backend logic is in `DefaultChatService` (sections: rules, sanitiser, tools, workflows, orchestration). Every ID has its toggle `capability.<ID>.enabled` seeded by essential data (`tools/packgen/test_generate.py` checks 43 toggles).

| ID | Code | Status |
| --- | --- | --- |
| CF-01 | `chat-page.component.ts` | Written; Angular strict-template compile passes; no browser run |
| CF-02 | `DefaultChatService`, `CLARIFY`, `wfFindProduct` clarification | Service-tested (`ScenarioTest`, `SecurityTest`) |
| CF-03 | `Blocks`, `block-renderer.component.ts` | Service-tested server side; UI compile only |
| CF-04 | `session-persistence.ts`, `chat.store.ts` | Core tested (vitest); logout clearing written, not run in Spartacus |
| CF-05 | `DefaultChatService` | Service-tested with fake translator only (SC-09); S8 not run |
| CF-06 | `DefaultChatService`, contact text, `unavailable` notice | Service-tested |
| CF-07 | `feedback-controls.component.ts`, `DefaultChatFacade` | Written, not run |
| CF-08 | `TraceStep` returned by tools; `why-this-panel.component.ts` | Service-tested (trace present); UI compile only |
| PD-01 | `toolSearchProducts` | Service-tested (SC-02) |
| PD-02 | `toolSearchProducts` filters, whitelist in `DefaultChatService` | Service-tested |
| PD-03 | `DefaultChatService` tools, `wfProductQa` | Written; no dedicated scenario test |
| PD-04 | `DefaultChatService` (tool, `wfCompare`, text-vs-table check) | Service-tested (SC-07) |
| PD-05 | attribute mapping in manifests; compatibility compare via attributes | Partial: data present, no compatibility-specific logic or test |
| PD-06 | `DefaultChatService` tools | Service-tested (via SC-03 replacement) |
| PD-07 | `DefaultChatService` tools | Service-tested (SC-02) |
| PA-01 | product card add, `purchase-list.component.ts` | UI compile only |
| PA-02 | `ChatStore`, `DefaultChatFacade` | Core tested (quantities); facade not run |
| PA-03 | `DefaultChatService` tools | Service-tested (SC-03) |
| PA-04 | `planBudget`, `toolPlanBudget` | Service-tested incl. 2,000-case property test (SC-04) |
| PA-05 | quantities from history averages (`wfBudgetPlan`, `DefaultChatService`) | Service-tested indirectly |
| PA-06 | `DefaultChatService` tools | Service-tested (SC-08) |
| PA-07 | `wfReorder` | Written; one canary/isolation use, no happy-path scenario test |
| OM-01 | `DefaultChatService` tools | Service-tested (SC-06) |
| OM-02 | `DefaultChatService` tools, `wfOrderLookup` | Service-tested (SC-06) |
| OM-03 | FAQ + order summaries | Written; FAQ not scenario-tested beyond the workflow |
| RI-01 | `DefaultChatService` tools | Service-tested (SC-01) |
| RI-02 | `DefaultChatService` tools, netting | Service-tested (SC-01) |
| RI-03 | `DefaultChatService`, `welcome-panel.component.ts` | Service-tested (SC-05) |
| RI-04 | `DefaultChatService` tools | Service-tested (SC-05) |
| RI-05 | `wfNextOrder` | Service-tested (SC-01) |
| CI-01 | `DefaultChatService` | Service-tested (SC-01, SC-08) |
| CI-02 | `DefaultChatService` tools | Written; not scenario-tested |
| CI-03 | `DefaultChatService` | Service-tested (canary: margin never leaves) |
| CI-04 | `DefaultChatService`, `CCInterestEvent` | Service-tested (switch, no text) |
| KN-01 | `DefaultChatService` tools, `wfFaq` | Written; not scenario-tested |
| PL-01 | `DefaultChatService`, items, Backoffice config | Service-tested (layers, TTL, fail-closed); Backoffice not run |
| PL-02 | `callProvider`, router in `DefaultChatService` | Contract-tested on synthetic fixtures; real provider shapes UNVERIFIED (S3) |
| PL-03 | `DefaultChatService`, `DefaultChatService` | Service-tested (canaries) |
| PL-04 | `DefaultChatService`, `DefaultChatService` | Service-tested |
| PL-05 | `DefaultChatService` | Service-tested (no text, no personal data) |
| PL-06 | `DefaultChatService` | Service-tested |
| PL-07 | `tools/packgen`, `CcSampleDataSystemSetup` | Generator tested; ImpEx and loader never loaded into a platform (S5 not run) |
| PL-08 | alias pattern in `cccore-spring.xml`; one service/DAO to extend (weaker than designed SPIs), `docs/extension-guide.md` | Written; guide not yet followed by a new developer |
