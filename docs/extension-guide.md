# Extension guide

The backend is deliberately small: one service and one DAO. Extend by editing `DefaultChatService` / `ChatDaoImpl`, or by replacing a bean with a subclass through the Spring alias (`ccDefaultChatService` aliased as `ccChatService`, `ccDefaultChatDao` as `ccChatDao`). Tests use `backend/cccore/testsrc/.../demo/EngineFixture` (in-memory `ChatDao` + fake OpenAI-shaped transport) and run with `backend/cccore/tools-run.sh`.

## Add a tool
1. Add the name and capability to the `TOOLS` map and a `case` in `runTool` (section "tools").
2. Write `private ToolResult toolYourThing(TurnState s, Map<String,Object> args)`. Read the customer from `s.customerId`, never from `args`. Validate every argument with `argStrings/argString/argNumber/argMap` (type, size, range). Return `new ToolResult(value, codes, trace)`: `codes` feed the provenance set, `trace` is whitelisted why-this metadata. A tool must not write data or return prices/margin to anything a model sees.
3. Need new data? Add a method to `ChatDao`, implement it in `ChatDaoImpl` (named parameters, customer in the query) and in the test `DemoDataset`.
4. Seed `capability.<ID>.enabled` in `tools/packgen/generate.py` if it needs a new capability ID (and add it to `ChatService.CAPABILITIES`).
5. Test as customer A and B (see `IsolationAndLoadTest`) and call `service.executeTool("customer", "yourThing", args)`.

## Add a workflow (intent)
Add the intent to the `Intent` enum, the `decision.v1.json` schema enum and `decide.v1.txt`; add a `case` in `runWorkflow` and a `wfYourIntent(TurnState, Decision)` returning `Output(blocks, templateKey, facts, slots, table, actions)`. Call tools through `step(s, labelKey, tool, args)` so steps stream and limits apply; use `runParallel` for independent calls. Add the fallback sentence to `TEMPLATES` with `{{S:slot}}` placeholders.

## Add a rule
Rules are `public static` methods with record results (section "rules") and no platform dependency. Pass configuration values as arguments. Add a unit test in `RulesTest` (a property test if the rule has an invariant, as `planBudget` does).

## Replace data access (gateway-style override)
Subclass `ChatDaoImpl` (or implement `ChatDao`) and re-point the alias: `<alias name="yourDao" alias="ccChatDao"/>`. Keep the customer filter in every customer-scoped query. Hooks marked `// FUTURE SCOPE:` show where unit-level access, live stock, a real cart and carrier tracking plug in.

## Add a provider
Add a `case` to the two switches in `callProvider` (request shape, response parse), a contract test with a recorded synthetic fixture (`ProviderContractTest`, `testsrc/fixtures/providers`). Never log a body. Verify the shape and model IDs against the provider's current documentation and record it in `docs/verification-log.md`. Model IDs and routes are configuration (`route.<task>.*`), never code.

## Add a language or pack
Language: no code; run spike S8. Pack: add `tools/packgen/manifests/<pack>.json`, run `python3 tools/packgen/generate.py`, commit the generated files.
