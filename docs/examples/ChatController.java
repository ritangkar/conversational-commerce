// TRANSCRIBED from owner screenshot. Some lines were truncated in the screenshot (marked "..." below).
// Imports, the class Javadoc and any exception handling were NOT visible. Do not assume them.
// Package base "com.fpp" and the "fpp" bean prefix are placeholders (public repo uses com.conversational.commerce / cc).
@Controller
@RequestMapping(value = "/{baseSiteId}/users/{userId}")
@ApiVersion("v2")
public class ChatController extends BaseController
{
    @Resource(name = "fppChatFacade")
    private ChatFacade chatFacade;

    @Secured({ SecuredAccessConstants.ROLE_CUSTOMERGROUP, SecuredAccessConstants.ROLE_CUSTOMERMANAGERGROUP, SecuredAccessConstants.ROLE_TRUSTED_CLIENT })
    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    @Operation(operationId = "chat", summary = "...", description = "...")   // TRUNCATED in screenshot
    @ApiBaseSiteIdAndUserIdParam
    public ChatResponseWsDTO chat(
            @Parameter(description = "Chat request", required = true) @RequestBody final ChatRequestWsDTO request,
            @ApiFieldsParam @RequestParam(defaultValue = DEFAULT_FIELD_SET) final String fields)
    {
        final ChatRequestData requestData = getDataMapper().map(request, ChatRequestData.class);
        final ChatResponseData responseData = chatFacade.chat(requestData);
        return getDataMapper().map(responseData, ChatResponseWsDTO.class, fields);
    }
}
