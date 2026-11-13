package com.conversational.commerce.core.engine.tools;

import com.conversational.commerce.core.engine.gateway.StockGateway;
import com.conversational.commerce.core.engine.model.Domain.StockInfo;
import com.conversational.commerce.core.engine.orchestrator.ChatTool;
import com.conversational.commerce.core.engine.orchestrator.ToolResult;
import com.conversational.commerce.core.engine.orchestrator.TurnState;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Map;

public class StockInfoTool implements ChatTool
{
    @Resource(name = "ccStockGateway")
    private StockGateway stockGateway;

    public void setStockGateway(final StockGateway stockGateway)
    {
        this.stockGateway = stockGateway;
    }

    @Override
    public String name()
    {
        return "getStockInfo";
    }

    @Override
    public String capabilityId()
    {
        return "PD-06";
    }

    @Override
    public ToolResult execute(final TurnState state, final Map<String, Object> args)
    {
        // Only codes this turn already produced may be looked up (provenance).
        final List<StockInfo> infos = ToolArgs.strings(args, "codes", 20).stream().filter(state.provenance::contains)
                .map(stockGateway::stockOf).toList();
        return ToolResult.of(infos, infos.stream().map(StockInfo::code).toList());
    }
}
