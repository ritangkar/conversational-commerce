# Verification log

Rule: verify platform and provider facts before relying on them; record source and date. **Nothing below was verified against a live source in this session unless it says so.** The session had no SAP Commerce installation and no provider keys.

| Date | Fact | Source | Result |
| --- | --- | --- | --- |
| 2026-11-13 | Design evidence (OpenAI no-training default, Anthropic 30-day deletion, Fable/Mythos ZDR exclusion, KBA 3363029 versions) | design part1 section 0 | Carried from the design; not re-verified |
| 2026-11-13 | OpenAI chat-completions request shape (`response_format.json_schema.strict`, `store`, `max_completion_tokens`) | author's memory | **UNVERIFIED**; contract-tested only on synthetic fixtures |
| 2026-11-13 | Anthropic messages request shape (forced `tool_choice` with `input_schema`, `anthropic-version`, `x-api-key`) | author's memory | **UNVERIFIED** |
| 2026-11-13 | Gemini `generateContent` shape (`responseMimeType`, `responseSchema`, `x-goog-api-key`, `usageMetadata`) | author's memory | **UNVERIFIED**; `additionalProperties` is stripped on the assumption that the schema subset rejects it |
| 2026-11-13 | Current model IDs for all three providers | not checked | **Not verified**; routes are empty in essential data |
| 2026-11-13 | SAP imports: `BaseController`, `ApiVersion`, `SecuredAccessConstants`, `ApiFieldsParam`, `ApiBaseSiteIdAndUserIdParam`, `PagedFlexibleSearchService`, `SortQueryData`, `CommercePriceService`, classification and reference types | author's memory | **UNVERIFIED**; compare with the owner's real imports |
| 2026-11-13 | `Registry`/`JaloSession` calls in `SessionPropagator` | author's memory | **UNVERIFIED** (S1) |
| 2026-11-13 | ImpEx syntax for catalog, classification, B2B, user rights, cron job | author's memory | **UNVERIFIED** (E17) |
| 2026-11-13 | Spartacus `AuthService`, `UserIdService`, `AuthGuard` | author's memory; local type stubs only | **UNVERIFIED** |
| 2026-11-13 | Angular 19 strict-template compile of all components | `ngc` run in this repository | **Verified** (compile only) |
| 2026-11-13 | WCAG contrast ratios of the token pairs | `tools/ci/contrast.py` run in this repository | **Verified** by computation; hex values still need PwC confirmation |
