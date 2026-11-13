# Architecture

Facts are separated from assumptions: **[F]** implemented here, **[A]** assumption, **[R]** recommendation. Design source: `docs/design/`.

**[F] Principle.** AI interprets and communicates. Java is the executor of record. The model returns a closed-enum decision or IDs; the backend validates, executes, hydrates prices/stock/images and streams validated blocks.

## Layers and where they live

| Layer | Path | Verified here |
| --- | --- | --- |
| Engine (platform-independent): config, rules, sanitiser, provider adapters/router, tools, workflows, orchestrator, audit, usage caps, interest events, retention, welcome | `backend/cccore/src/.../core/engine` | Compiled and tested with JUnit 5 against local stubs (`backend/cccore/tools-run.sh`) |
| SAP-bound: `items.xml`, DAOs, platform gateways, Spring XML, retention CronJob | `backend/cccore/{resources,src/.../dao,platform,job}` | **Not compiled** (needs SAP Commerce) |
| Facades: `facade-beans` (generated), assembler, facades | `backend/ccfacades` | **Not compiled** |
| OCC: `occ-beans` (generated), controllers, `UserBindingGuard`, SSE | `backend/ccocc` | **Not compiled**; S1/S2 not run |
| Backoffice config | `backend/ccbackoffice` | XML well-formed only |
| Sample data: ImpEx and order plans (generated), loader, `@SystemSetup` | `backend/ccsampledata`, `tools/packgen` | Generator tested; ImpEx/loader never loaded |
| Storefront | `storefront/chat-feature` | Core logic tested (vitest incl. real HTTP SSE); components compile under Angular strict templates; never rendered in a browser |

## Turn flow (streaming)
1. Browser POSTs the bounded message and context with a bearer token. 2. `UserBindingGuard` rejects any `userId` other than `current` or the principal. 3. Facade validates length (sample style). 4. `UsageCapService` and `PL-04` check. 5. Language detection; translate-in with protected tokens if not English. 6. `Sanitiser` builds the payload (allow-list, masking, `OutboundGuard`); `ModelRouter` calls `DECIDE`. 7. `DecisionValidator` checks intent, capability, ranges and filter whitelist. 8. The intent's `WorkflowTemplate` runs deterministic tools through `ToolRegistry` (parallel groups, step/time caps). 9. `Composer` calls `COMPOSE`/`EXPLAIN` with facts and slot names only; text is accepted only if slots resolve, no loose digits, no unknown codes, and it matches the table; otherwise a fixed template is used. 10. Blocks stream first, then text chunks, then `done` with the trace.

## Security invariants (each has a test)
No price, margin, uid or email in any model payload (`SecurityTest.canaryValues...`). `SanitisedPayload` has a package-private constructor, so adapters cannot be handed unsanitised text. Tools take no customer argument; the customer is `TurnState.customerId` (`SecurityTest.toolsTake...`, `IsolationAndLoadTest`). IDs from the model are dropped unless a tool returned them this turn. Audit keys are whitelisted. Sanitiser off = no model call.

## Known limits [F]
- A limit hit returns a notice instead of partial blocks (the design says partial results; not implemented).
- The `QUICK_ORDER` residual-line model parse from the design is not implemented; unresolved lines go back to the dealer.
- Welcome panel uses template text only (N3 pending).
- `PlatformUsageCounterStore` is not atomic across nodes.
- S1 (SSE through CCv2), S2 (DataMapper), S3 (real providers), S4 (latency), S5 (pack coexistence), S6 (scale), S8 (translation safety) have not been run.
