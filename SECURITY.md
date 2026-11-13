# Security

- No API keys, real customer data or client-specific models in this repository. Keys live only in `local.properties` (gitignored), environment variables or a Cloud secret under `cc.provider.key.<alias>`.
- Secret scanning: `tools/ci/secret-scan.sh` and gitleaks run in CI. Enable GitHub secret scanning and push protection in the repository settings (owner action).
- The model never sees prices, totals, margin, priority, names, emails, internal PKs or other customers' data, and has no write tool. Customer identity comes only from the Spring Security context.
- Logs never contain prompts, message text or personal data.
- Report vulnerabilities privately via GitHub Security Advisories on this repository. Do not open public issues for them.
