# Assumptions register

Each entry lists the file it affects. Code carries a matching `// ASSUMPTION:` marker. Owner may overrule any entry.

| ID | Assumption | Affects | Status |
| --- | --- | --- | --- |
| AS-01 | SAP Commerce 2211-jdk21, Jakarta, Spring 6; Angular 19, Node 22 (design A12) | all backend imports, `storefront/chat-feature/package.json`, CI | Owner to confirm |
| AS-02 | Names derived: package `com.conversational.commerce`, bean prefix `cc`, item prefix `CC`, extensions `cccore`, `ccfacades`, `ccocc`, `ccbackoffice`, `ccsampledata` (N1) | all | Owner to confirm |
| AS-03 | Spring bean XML, `items.xml`, Backoffice config use standard platform layout (U5, U6) | Stage 3 files | Replace when samples arrive |
| AS-04 | Test style JUnit 5 / Mockito / ServicelayerTest (U8) | all tests | Replace when samples arrive |
| AS-05 | Angular standalone components, ESLint defaults, Spartacus feature-library conventions (U9) | `storefront/chat-feature` | Replace when samples arrive |
| AS-06 | Service catch blocks beyond `InterruptedException` mapped to `SystemException` without bodies (U3) | provider adapters | Replace when full sample arrives |
| AS-07 | Javadoc on public types only; Allman braces everywhere (U10) | all Java | Owner to confirm |
| AS-08 | CI uses GitHub Actions; secret scanning via gitleaks plus GitHub push protection | `.github/workflows` | Recommendation |
| AS-09 | SAP Commerce binaries are licensed and not in the repo; backend build and platform tests run only in an environment with a platform installation | CI, Stage 3 onward | Fact about licensing, owner to supply a build environment |
