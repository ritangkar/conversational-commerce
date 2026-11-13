// TRANSCRIBED (approximately) from owner screenshot: query constant and named parameters from the DAO example (findByReportNumber).
// The first query line was partly truncated in the screenshot. Class declaration, @Resource FlexibleSearchService field,
// buildFilterClause(...), createSortQuery(...) and the PagedFlexibleSearchService usage were described in the review
// but their full source was NOT transcribed. "Cpp" is a placeholder prefix.
private static final String FIND_REPORT_BY_NUMBER =
    "SELECT {r:" + ItemModel.PK + "} FROM {" + CppDeliveryIssueReportModel._TYPECODE + " AS r}"
    + " WHERE {r:" + CppDeliveryIssueReportModel.REPORTNUMBER + "}=?reportNumber"
    + " AND {r:" + CppDeliveryIssueReportModel.CUSTOMER + "}=?customer";

final FlexibleSearchQuery query = new FlexibleSearchQuery(FIND_REPORT_BY_NUMBER);
query.addQueryParameter("reportNumber", reportNumber);
query.addQueryParameter("customer", customer);
// Result handling convention (from review): CollectionUtils.isNotEmpty(result.getResult()) ? result.getResult().get(0) : null
// Blank input returns null.
