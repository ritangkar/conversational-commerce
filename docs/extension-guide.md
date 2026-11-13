# Extension guide

Every extension point is a Spring bean of an SPI type. Core code is not edited. Examples use the engine in `backend/cccore/src/.../core/engine`; tests use `testsrc/.../demo/EngineFixture` as a wiring reference.

## Add a tool (about 10 minutes)
1. Implement `ChatTool` (`name()`, `capabilityId()`, `execute(TurnState, Map<String,Object>)`). Read the customer from `state.customerId`, never from `args`. Validate every argument with `ToolArgs` (type, size, range). Return `ToolResult.of(value, codes, TraceStep...)`: `codes` feed the provenance set, `TraceStep` is whitelisted why-this metadata.
2. A tool must not write data and must not return prices or margin to anything that reaches a model.
3. Register the bean in your extension's Spring file and add it to the `ccChatTools` list (override the list bean or add a `ListMergeDirective`).
4. Add the capability toggle `capability.<ID>.enabled` (an entry in your essential data) if it needs its own switch.
5. Test: add it to `EngineFixture` (`tools.add(...)`) and call `toolRegistry.execute(state, "yourTool", args)`; assert customer isolation as customer A vs B, as `IsolationAndLoadTest` does.

Sample: `SearchProductsTool` is the smallest complete tool.

## Add a workflow
Implement `WorkflowTemplate`: `intent()` and `run(TurnState, Decision)` returning `Output(blocks, templateKey, facts, slots, table, suggestedActions, interestCode)`. Call tools only through `tool(state, labelKey, name, args)` so steps are streamed and limits apply; use `toolRegistry.executeParallel` for independent calls. Add a `TemplateTexts` key for the fallback text with `{{S:slot}}` placeholders. New intents also need an entry in `Intent`, the `decision.v1.json` enum and the `decide.v1.txt` prompt.

## Add a rule
Rules are plain classes in `engine/rules` with no platform imports and no injection. Pass configuration values as method arguments. Add a unit test (see `RulesTest`; use a property test if the rule has an invariant, as the budget planner does).

## Add or replace a gateway
Gateways (`CatalogGateway`, `PriceGateway`, `OrderGateway`, `StockGateway`, `FaqGateway`, `CoPurchaseGateway`, `BusinessAttributeGateway`) are overridden by Spring alias: define your bean and `<alias name="yourBean" alias="ccOrderGateway"/>`. Keep the customer filter in the query. Hooks marked `// FUTURE SCOPE:` show where unit-level access, live stock, a real cart and carrier tracking plug in.

## Add a provider
Extend `AbstractHttpAdapter` (`endpoint`, `headers`, `buildBody`, `extractContent`, `extractUsage`), register it in `ccModelAdapters`, add a contract test with a recorded synthetic fixture (see `ProviderContractTest`). Never log a body. Verify request shape and model IDs against the provider's current documentation and record it in `docs/verification-log.md`.

## Add a language or pack
Language: no code; add `TRANSLATE` coverage and run spike S8. Pack: add `tools/packgen/manifests/<pack>.json`, run `python3 tools/packgen/generate.py`, commit the generated files.
