package com.conversational.commerce.core.dao;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** Filter values for searchOrders. All values are bound as named parameters; none is concatenated into the query. */
public class OrderSearchFilter
{
    private List<String> statusCodes;
    private BigDecimal minTotal;
    private BigDecimal maxTotal;
    private Date fromDate;
    private Date toDate;

    public List<String> getStatusCodes()
    {
        return statusCodes;
    }

    public void setStatusCodes(final List<String> statusCodes)
    {
        this.statusCodes = statusCodes;
    }

    public BigDecimal getMinTotal()
    {
        return minTotal;
    }

    public void setMinTotal(final BigDecimal minTotal)
    {
        this.minTotal = minTotal;
    }

    public BigDecimal getMaxTotal()
    {
        return maxTotal;
    }

    public void setMaxTotal(final BigDecimal maxTotal)
    {
        this.maxTotal = maxTotal;
    }

    public Date getFromDate()
    {
        return fromDate;
    }

    public void setFromDate(final Date fromDate)
    {
        this.fromDate = fromDate;
    }

    public Date getToDate()
    {
        return toDate;
    }

    public void setToDate(final Date toDate)
    {
        this.toDate = toDate;
    }
}
