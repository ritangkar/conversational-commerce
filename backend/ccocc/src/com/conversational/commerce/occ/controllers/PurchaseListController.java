package com.conversational.commerce.occ.controllers;

// ASSUMPTION (AS-04): import lines of the owner sample were not visible; verify against the owner ChatController.
import com.conversational.commerce.occ.security.UserBindingGuard;
import de.hybris.platform.commercewebservices.core.v2.controllers.BaseController;
import de.hybris.platform.commercewebservicescommons.annotation.ApiVersion;
import de.hybris.platform.commercewebservicescommons.constants.SecuredAccessConstants;
import de.hybris.platform.webservicescommons.swagger.ApiBaseSiteIdAndUserIdParam;
import de.hybris.platform.webservicescommons.swagger.ApiFieldsParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.conversational.commerce.facades.chat.PurchaseListFacade;
import com.conversational.commerce.facades.data.chat.PurchaseListData;
import com.conversational.commerce.facades.data.chat.PurchaseListResultData;
import com.conversational.commerce.occ.chat.PurchaseListResultWsDTO;
import com.conversational.commerce.occ.chat.PurchaseListWsDTO;

@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}")
@ApiVersion("v2")
public class PurchaseListController extends BaseController
{
    @Resource(name = "ccPurchaseListFacade")
    private PurchaseListFacade purchaseListFacade;

    @Resource(name = "ccUserBindingGuard")
    private UserBindingGuard userBindingGuard;

    @Secured({ SecuredAccessConstants.ROLE_CUSTOMERGROUP })
    @PostMapping(value = "/chat/purchase-list/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    @Operation(operationId = "validatePurchaseList", summary = "Validate the demo purchase list", description = "Validates and prices the browser-held list. Demo purchase list, not your cart.")
    @ApiBaseSiteIdAndUserIdParam
    public PurchaseListResultWsDTO validate(@PathVariable final String userId,
            @Parameter(description = "Purchase list", required = true) @RequestBody final PurchaseListWsDTO list,
            @ApiFieldsParam @RequestParam(defaultValue = DEFAULT_FIELD_SET) final String fields)
    {
        userBindingGuard.assertCurrentUser(userId);
        final PurchaseListData data = getDataMapper().map(list, PurchaseListData.class);
        final PurchaseListResultData result = purchaseListFacade.validate(data);
        return getDataMapper().map(result, PurchaseListResultWsDTO.class, fields);
    }
}
