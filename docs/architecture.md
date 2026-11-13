# Architecture

Facts are separated from assumptions: **[F]** implemented here, **[A]** assumption, **[R]** recommendation. Design source: `docs/design/`.

**[F] Principle.** AI interprets and communicates. Java is the executor of record. The model returns a closed-enum decision or IDs; the backend validates, executes, hydrates prices/stock/images and streams validated blocks.

## Backend: seven Java files (owner request)

| File | Role |
| --- | --- |
| `ccocc/.../controllers/ChatController` | All six endpoints, user-binding check (403), SSE emitter with heartbeat, session propagation to the stream worker |
| `ccfacades/.../chat/ChatFacade`, `impl/DefaultChatFacade` | Input validation (owner sample style), customer from the session, manual `*Data` mapping, delegates to the service |
| `cccore/.../service/ChatService`, `impl/DefaultChatService` | The whole backend logic: configuration, audit, rules, sanitiser, provider routing, 20 tools, 14 workflows, orchestration, caps, interest events, welcome, feedback, purchase-list validation, retention |
| `cccore/.../dao/ChatDao`, `impl/ChatDaoImpl` | All FlexibleSearch queries and the few writes; returns plain value types |

One extra class, `ccsampledata/.../ChatSampleDataSetup`, is sample-data loading only (system setup plus relative-date demo orders) and is not production code. Everything else is XML, ImpEx, prompts/schemas (resource files) and the storefront.

**Why one large service [F]:** requested to keep the file count small. Cost: `DefaultChatService` is about 2,700 lines, extension is by editing or overriding that bean (see `docs/extension-guide.md`) rather than by adding SPI beans, and PL-08 (extension points) is weaker than designed. Sections are marked with banner comments.

## Layers and verification state

| Layer | Path | Verified here |
| --- | --- | --- |
| Service + `ChatDao` interface | `backend/cccore/src/.../service`, `.../dao/ChatDao.java` | Compiled and tested: 55 JUnit tests (`backend/cccore/tools-run.sh`) against an in-memory `ChatDao` and a fake OpenAI-shaped transport |
| `ChatDaoImpl`, items, Spring XML | `backend/cccore/{src/.../dao/impl,resources}` | **Not compiled** (needs SAP Commerce) |
| Facade | `backend/ccfacades` | Compiled against POJOs generated from `tools/beangen/beans.json` and a stub `UserService`; not run |
| OCC controller | `backend/ccocc` | **Not compiled**; S1/S2 not run |
| Backoffice config | `backend/ccbackoffice` | XML well-formed only |
| Sample data | `backend/ccsampledata`, `tools/packgen` | Generator tested; ImpEx/loader never loaded |
| Storefront | `storefront/chat-feature` | Core logic tested (vitest incl. real HTTP SSE); components compile under Angular strict templates; never rendered |

## Turn flow (streaming)
1. Browser POSTs the bounded message and context with a bearer token. 2. Controller checks `userId` is `current` or the principal (403 + audit otherwise). 3. Facade validates length. 4. Service: `PL-04`, usage cap. 5. Language detect; translate-in with protected tokens if not English. 6. `callModel` sanitises (allow-list per task, NEVER fields, masking, outbound guard) then routes `DECIDE`. 7. Decision validation (intent, capability, ranges, filter whitelist). 8. The intent's workflow runs deterministic tools through `runTool` (unknown/disabled rejected; parallel groups; step, time and tool-call caps). 9. `compose` calls `COMPOSE`/`EXPLAIN` with facts and slot names only; text is accepted only if slots resolve, no loose digits, no unknown codes, and it matches the table; otherwise a fixed template. 10. Blocks stream first, then text chunks, then `done` with the response id and trace.

## Security invariants (each has a test)
No price, margin, uid or email in any model payload (`SecurityTest.canaryValues...`). `callModel` is the only path to a model and always sanitises first; there is no public method that takes pre-built payload text. Tools take no customer argument; the customer is `TurnState.customerId` (`IsolationAndLoadTest`). IDs from the model are dropped unless a tool returned them this turn. `audit()` rejects keys outside a whitelist. Sanitiser off = no model call.

## Known limits [F]
- A limit hit returns a notice instead of partial blocks (AS-15). Quick order has no model parse of residual lines (AS-16). Welcome panel is template text only (N3 pending).
- The usage counter is not atomic across nodes. The retention job is a Groovy scripting job calling `ChatService.purgeExpiredData()` (syntax unverified).
- S1 (SSE through OCC/CCv2), S2 (DataMapper), S3 (real providers), S4 (latency), S5 (pack coexistence), S6 (scale), S8 (translation) have not been run.
