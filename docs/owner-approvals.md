# Owner approvals

Nothing below has been applied beyond what is stated.

| # | Item | Touches | Evidence / options | Default now |
| --- | --- | --- | --- | --- |
| N1 | Confirm derived names (`cc`, `CC`, `cccore`...) | D37 | Only the package base was named | Used as derived |
| N2 | Reword PL-03 criterion for free text | PL-03 | No filter can guarantee free text | Reworded in docs |
| N3 | Welcome-text caching | D07 | Memory-only short TTL vs template-only | Not implemented (template-only) |
| N4 | Spike fallbacks changing protected decisions (S1 polling buffer, S2 non-mapper filtering, S4/S8 compose-in-language, S5 site per pack) | D07, D37, D17, D32 | See part4 section 4 | Not adopted |
| N5 | Languages beyond Hindi for S8 | CF-05 | Owner picks two | Hindi only |
| N6 | Interest-event notice wording / consent, legal review | D08 | Configurable field | Placeholder |
| N7 | Platform versions (A12) | constraints | Assumed | Assumed |
| N8 | PwC base theme with pack accents only | D32, part6 | Implement as designed | Flagged; hex values to be confirmed against PwC brand portal |
| M1-M13 | Missing examples (docs/examples/MISSING_EXAMPLES.md) | conventions | Needed per stage | Assumptions in assumptions.md |
| B1 | Build environment with a licensed SAP Commerce installation for Stages 3-10 | delivery | None available in this session | Owner to provide or run commands |
| B2 | Licence for the public repository | D01 | Not chosen; no `LICENSE` added | none |
| B3 | Interpretation of A1 for PL-03/PL-04 toggles (AS-14) and partial results on limits (AS-15) | A1, D13 | Documented in assumptions | as implemented |
| B4 | Quick order without model parse of residual lines (AS-16) | PA-03 | Trade-off: fewer model calls, unresolved lines go back to the dealer | as implemented |
| B5 | HTTP-level S7 and S1/S2/S3/S4/S5/S6/S8 not run | acceptance | See `docs/spikes/` | no spike branch that changes a protected decision was triggered or applied |

Protected decisions D01-D37 were not changed. N3 (welcome caching) is not implemented; N8 (PwC theme) is implemented as designed and flagged in `docs/theming.md`.
| B6 | Backend consolidated into one service and one DAO as requested (AS-20). Trade-offs: `DefaultChatService` is about 2,700 lines; PL-08 extension points (separate tool/workflow/gateway beans, registries) are replaced by editing or overriding one bean; the DAO returns value types, not models | PL-08, D37 | Alternative: restore the layered engine (it is in git history before this change) | as implemented, per the owner's request |

