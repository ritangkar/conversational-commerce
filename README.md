# UC1 - SAP Commerce AI-First Conversational B2B Commerce Accelerator

A drop-in module for SAP Commerce OCC, facade and core layers plus a Spartacus feature library: a full-page purchasing assistant for logged-in B2B dealers. **AI interprets and communicates; SAP Commerce Java is the executor of record.** The model never sees prices, margin or other customers' data, has no write tool and never places orders.

## Status (honest)

All code is written, but only part of it could be run in the environment that produced it (no SAP Commerce, no provider keys, no browser run).

| Part | State |
| --- | --- |
| Backend service (`DefaultChatService` + `ChatDao` interface): config, rules, sanitiser, provider routing, tools, workflows, orchestration, audit, caps, events | **Built and tested**: 55 JUnit tests pass on dummy data, including SC-01..SC-10, canary leak, isolation, injection, property and load tests. Run `backend/cccore/tools-run.sh` (needs Java 21 and network for jars) |
| SAP-bound code (`ChatDaoImpl`, items, controller, Backoffice, sample-data loader, Spring XML; the facade compiles against stubs) | **Written, not compiled or run on SAP.** Imports and wiring are UNVERIFIED; see `docs/assumptions.md` and `docs/verification-log.md` |
| Storefront feature library | Core logic tested (13 vitest tests incl. real HTTP SSE); components compile under Angular strict templates; never rendered; no axe, e2e or screenshot run |
| Pack generator, ImpEx, order plans | Generator tested (determinism, 43 toggles, fixtures); ImpEx never loaded |
| Providers | Contract-tested on synthetic fixtures only; request shapes and model IDs UNVERIFIED |
| Spikes S1-S8 | Mostly not run: `docs/spikes/` |

Next steps for the owner: provide a SAP Commerce 2211 build environment, the missing owner examples (`docs/examples/MISSING_EXAMPLES.md`), provider keys for S3/S4/S8, and decisions in `docs/owner-approvals.md`.

## Backend files
Controller `ChatController`; facade `ChatFacade` + `DefaultChatFacade`; service `ChatService` + `DefaultChatService`; DAO `ChatDao` + `ChatDaoImpl`. Plus one sample-data class (`ChatSampleDataSetup`). Everything else is XML/ImpEx/resources. See `docs/architecture.md`.

## Layout
`backend/{cccore,ccfacades,ccocc,ccbackoffice,ccsampledata}`, `storefront/chat-feature`, `tools/{packgen,beangen,ci}`, `docs/`. Design inputs are in `docs/design/` and the owner's code samples in `docs/examples/` (read-only).

## Checks you can run now
```
backend/cccore/tools-run.sh              # engine tests (Java 21)
python3 -I tools/packgen/test_generate.py # pack generator
python3 -I tools/ci/contrast.py          # WCAG contrast
python3 tools/beangen/generate.py --check
tools/ci/selftest.sh                     # secret scanner catches a planted key
cd storefront/chat-feature && npm ci && npm test && npx ngc -p tsconfig.lib.json
```

## Documents
`docs/architecture.md`, `docs/extension-guide.md`, `docs/demo-script.md`, `docs/capability-status.md` (all 43 IDs), `docs/privacy.md`, `docs/provider-terms.md`, `docs/theming.md`, `docs/convention-digest.md`, `docs/assumptions.md`, `docs/owner-approvals.md`, `docs/verification-log.md`, `docs/spikes/`.
