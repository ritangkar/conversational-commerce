# Privacy note

- **Model calls** receive only the minimum per task (the egress allow-list in `DefaultChatService`): the masked message, enabled intents, terminology, a few masked context turns and product text. Never prices, totals, margin, priority, names, emails, internal keys or other customers' data. Free text typed by the dealer is masked best-effort (emails, phones, long digit runs, order codes); the UI asks dealers not to type personal data. This is a guarantee for backend-supplied data and a best effort for free text (N2).
- **Chat history** is not stored server-side. The browser keeps turns, list and shortlist in `sessionStorage` under `cc.chat.<userId>`, cleared on logout and tab close.
- **Interest events** (D08, CI-04): enabled by default, shown to the dealer with a visible notice, switchable in Backoffice (`privacy.interestEvents.enabled`), stored in their own table with customer, enum type, product/category code or a letters-only token, pack and time; no text. Retention 90 days (`privacy.interestEvents.retentionDays`), purged by the scripting job `ccRetentionJob`. The notice wording is a placeholder pending legal review (N6).
- **Feedback** is metadata only (rating, response id, intent, tool names) in a separate table with no customer reference.
- **Audit** lines (`cc.audit`) carry whitelisted metadata only; prompts, message text, names, emails and prices are rejected at construction.
- Margin and priority live in `CCProductBusinessAttribute`, readable only by the ranking code in `DefaultChatService` and members of `ccbusinessmanagergroup`.
