// TRANSCRIBED from owner screenshot: only the input-validation block inside DefaultChatFacade.chat(...) was visible.
// The class declaration, @Resource field for the service, MAX_MESSAGE_LENGTH constant, and the call to the service were NOT fully visible.
// Part of the second exception message was truncated ("...").
if (request == null || StringUtils.isBlank(request.getMessage()))
{
    throw new IllegalArgumentException("message must not be empty");
}
final String message = request.getMessage().trim();
if (message.length() > MAX_MESSAGE_LENGTH)
{
    throw new IllegalArgumentException("message must not exceed " + MAX_MESSAGE_LENGTH ...);   // TRUNCATED in screenshot
}
