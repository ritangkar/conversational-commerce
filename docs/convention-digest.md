# Convention digest (Stage 0)

Source: `docs/design/part5_conventions_prompt_open_items.md` section 7 and `docs/examples/*`.
Labels: **[F]** fact seen in the owner's samples, **[A]** assumption, **[R]** recommendation. Sample files are transcriptions of screenshots; truncated lines are marked TRUNCATED and are not reconstructed.

## 1. What I will follow (binding, [F])

| Area | Rule |
| --- | --- |
| Packages | Base `com.conversational.commerce` (placeholder `com.fpp`). Facade DTOs `...facades.data.chat.*`, OCC DTOs `...occ.chat.*` |
| Names | Interface + `DefaultX`; `XxxDao` + `XxxDaoImpl`; facade DTOs end in `Data`; OCC DTOs end in `WsDTO`. Bean prefix `cc` (placeholder `fpp`), type prefix `CC` (placeholder `Cpp`) [A N1] |
| DTOs | XML beans: `facade-beans.xml` (`*Data`), `occ-beans.xml` (`*WsDTO`), `<property name type/>` |
| Controller | `@Controller`, `@RequestMapping("/{baseSiteId}/users/{userId}")`, `@ApiVersion("v2")`, `extends BaseController`; per method `@Secured`, `@PostMapping(value, consumes=JSON)`, `@ResponseBody`, `@ResponseStatus(OK)`, `@Operation(operationId, summary, description)`, `@ApiBaseSiteIdAndUserIdParam`; params `@Parameter(...) @RequestBody final XWsDTO request`, `@ApiFieldsParam @RequestParam(defaultValue = DEFAULT_FIELD_SET) final String fields`. Convert with `getDataMapper().map(...)`; controller calls the facade only. **Deviation required by A5:** `@Secured({ ROLE_CUSTOMERGROUP })` only |
| Injection | `@Resource(name = "ccChatFacade")` on private fields (controller/facade); plain `@Resource` in service/DAO. No constructor injection |
| Facade | Validates with `StringUtils.isBlank`, max-length constant, `IllegalArgumentException("message must not be empty")` etc., trims, then delegates to a service |
| Service | Property keys as `private static final String`; `ConfigurationService.getConfiguration()` with `getString`/`getInt` defaults; `validateParameterNotNull(x, "x must not be null")` |
| HTTP | Shared `java.net.http.HttpClient` with connect timeout; per-request timeout from config; Jackson `ObjectMapper` (no string concatenation of JSON) |
| Errors (service) | Non-200: `LOG.warn` with status only, then `SystemException`; null content: `SystemException`; `InterruptedException`: restore interrupt flag. **Remaining catch blocks are truncated, see unknowns** |
| Logging | `private static final Logger LOG = LogManager.getLogger(X.class)`; status/metadata only |
| DAO | Interface returns models or `SearchPageData<Model>`; `FlexibleSearchService` for single lookups, `PagedFlexibleSearchService` + `SortQueryData` for pages; customer passed in and filtered in the query |
| FlexibleSearch | Constants built from `Model._TYPECODE`/`Model.FIELD`/`ItemModel.PK`, `{r:...}` aliases, named params (`?param`) via `addQueryParameter` or Map; nothing from a request concatenated |
| Filters / sort | `buildFilterClause(params, filters, ...)` with `StringBuilder`, appends `AND`, null/blank checks, default "active only", dates truncated, `toDate` exclusive next day; `createSortQuery(sortCode, query)` falls back to `DEFAULT_SORT` |
| Results | `CollectionUtils.isNotEmpty(r.getResult()) ? r.getResult().get(0) : null`; blank input returns null |
| Style | Allman braces; `final` on parameters and locals. Single-line `if` with braces on the same line appears once in `findByReportNumber` [F, owner to confirm which wins] |
| Future scope | `// FUTURE SCOPE:` at unit-level order access (D33), unit-level replenishment (D34), live stock, real cart, carrier tracking |

## 2. Unknowns (not invented; defaults only where labelled)

| # | Unknown | Needed by | Unattended default (recorded in assumptions.md) |
| --- | --- | --- | --- |
| U1 | Full `ChatFacade`/`DefaultChatFacade`/`ChatService` incl. imports, constants, `@Resource` fields | 5-7 | Compose from the visible fragments |
| U2 | Full DAO (`buildFilterClause`, `createSortQuery`, `PagedFlexibleSearchService` use, `DEFAULT_SORT`) | 4 | Implement from the textual convention table only |
| U3 | Truncated catch blocks after `InterruptedException` | 5 | `catch (IOException)` -> `SystemException` with status-free message; labelled ASSUMPTION |
| U4 | Controller exception handling / error response | 7 | None; OCC default handler (400 for `IllegalArgumentException`) as the design states |
| U5 | Spring bean XML (service, DAO, facade, OCC web) | 3 | Standard `<bean id class/>` with `ccXxx` ids; ASSUMPTION |
| U6 | `items.xml` and Backoffice config samples | 3 | Standard platform layout; ASSUMPTION |
| U7 | Populator/converter sample | 7 | Not used; map via DataMapper and builders |
| U8 | Unit/integration test samples | 4 | JUnit 5 + Mockito for unit tests; platform `ServicelayerTest` for integration; ASSUMPTION |
| U9 | Angular/Spartacus samples and lint rules | 8 | Angular style guide, ESLint defaults, standalone components; ASSUMPTION |
| U10 | Javadoc style; brace confirmation | 0 | Short Javadoc on public types only; Allman everywhere |
| U11 | Versions and javax vs jakarta | 0 | 2211-jdk21, Jakarta, Angular 19, Node 22 (A12) |
| U12 | Meaning of `fpp` vs `Cpp` | 0 | One prefix `cc` for beans/packages, `CC` for item types (N1) |
| U13 | Imports of the sample (`javax.annotation.Resource` vs `jakarta.annotation.Resource`) | 4 | Jakarta, matching A12 |

## 3. Design conflicts with the samples (handled in the design, section 7 table)
Streaming endpoint beside the sample-shaped `POST /chat`; `@Secured` narrowed to `ROLE_CUSTOMERGROUP` plus `UserBindingGuard`; no controller exception handling shown.
