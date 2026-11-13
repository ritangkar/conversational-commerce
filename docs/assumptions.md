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
| AS-10 | ImpEx syntax, `SampleOrderLoader`, `AbstractSystemSetup` use, classification layout and user-rights rows written from memory; never loaded into a platform | `backend/ccsampledata/**`, `tools/packgen/generate.py` | Verify in S5 |
| AS-11 | Pack manifests are JSON, not YAML, to avoid a dependency; validated by a small subset validator, not full JSON Schema | `tools/packgen` | Owner may prefer YAML |
| AS-12 | Bean XML files are named `<extension>-beans.xml` (platform convention) rather than `facade-beans.xml`/`occ-beans.xml`; content follows the sample | `backend/ccfacades/resources`, `backend/ccocc/resources` | Confirm file naming |
| AS-13 | Engine value types are Java records; engine classes are SAP-independent and use `@Resource(name = "ccXxx")` fields plus setters for tests | `backend/cccore/.../engine` | Setters are an addition to the owner's convention |
| AS-14 | PL-03 and PL-04 can be toggled off but then fail closed; PL-01 cannot be turned off; PL-05 needs `capability.PL-05.confirmDisable` (A1 reading) | `DefaultCcConfigurationService` | Owner to confirm the interpretation |
| AS-15 | A tool or time limit returns a notice, not partial blocks | `DefaultChatOrchestrationService` | Design says partial results |
| AS-16 | Quick order uses the deterministic grammar only, no model parse of residual lines | `QuickOrderWorkflow` | Design lists a model parse |
| AS-17 | A product is "discontinued" when not APPROVED; its replacement is the first FOLLOWUP reference | `PlatformCatalogGateway`, ImpEx | Owner to confirm the model |
| AS-18 | Worker threads for streaming re-activate the captured JaloSession so prices use the customer's context | `SessionPropagator` | Verify in S1 |
| AS-19 | No `LICENSE` file added; the licence is the owner's decision | repo root | Owner to choose |

