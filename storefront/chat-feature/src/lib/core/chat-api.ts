import { SseFrameParser } from './sse-frame-parser';
import type { ChatRequest, ChatResponse, ChatStreamEvent, ChatBlock, StreamEventName } from './models';

export interface ChatApiOptions
{
  baseUrl: string;                       // e.g. https://host/occ/v2/{baseSiteId}/users/current
  getToken: () => string | undefined;     // Spartacus access token; never stored by this client
  fetchImpl?: typeof fetch;
  heartbeatTimeoutMs?: number;            // watchdog: no bytes for this long means the stream is dead
}

export interface StreamHandlers
{
  onEvent: (event: ChatStreamEvent) => void;
  onFallback?: () => void;
}

const KNOWN: StreamEventName[] = ['step_started', 'step_completed', 'block', 'text_delta', 'done', 'error'];

export class ChatApi
{
  private readonly fetchImpl: typeof fetch;
  private readonly heartbeatTimeoutMs: number;

  constructor(private readonly options: ChatApiOptions)
  {
    this.fetchImpl = options.fetchImpl ?? ((...a) => fetch(...a));
    this.heartbeatTimeoutMs = options.heartbeatTimeoutMs ?? 25000;
  }

  private headers(): Record<string, string>
  {
    const token = this.options.getToken();
    const h: Record<string, string> = { 'Content-Type': 'application/json' };
    if (token)
    {
      h['Authorization'] = `Bearer ${token}`;
    }
    return h;
  }

  /**
   * Streams one turn. On connect failure, a non-2xx, a heartbeat timeout or a stream that ends before 'done', it retries
   * once through POST /chat and replays the same blocks without animation. A user abort is never retried.
   */
  async stream(request: ChatRequest, handlers: StreamHandlers, signal: AbortSignal): Promise<void>
  {
    let finished = false;
    try
    {
      finished = await this.readStream(request, handlers, signal);
    }
    catch (e)
    {
      if (signal.aborted)
      {
        return;
      }
    }
    if (finished || signal.aborted)
    {
      return;
    }
    handlers.onFallback?.();
    const response = await this.chat(request, signal);
    for (const block of response.blocks)
    {
      handlers.onEvent({ event: 'block', block: block });
      if (block.type === 'text' && block.text)
      {
        handlers.onEvent({ event: 'text_delta', stepId: block.blockId, delta: block.text });
      }
    }
    handlers.onEvent({ event: 'done', responseId: response.responseId, trace: response.trace });
  }

  async chat(request: ChatRequest, signal?: AbortSignal): Promise<ChatResponse>
  {
    const res = await this.fetchImpl(`${this.options.baseUrl}/chat`, {
      method: 'POST', headers: this.headers(), body: JSON.stringify(request), signal });
    if (!res.ok)
    {
      throw new Error(`chat failed with status ${res.status}`);
    }
    return (await res.json()) as ChatResponse;
  }

  /** @returns true if 'done' was received. */
  private async readStream(request: ChatRequest, handlers: StreamHandlers, signal: AbortSignal): Promise<boolean>
  {
    const watchdog = new AbortController();
    const onAbort = () => watchdog.abort();
    signal.addEventListener('abort', onAbort);
    let timer: ReturnType<typeof setTimeout> | undefined;
    const arm = () =>
    {
      if (timer) { clearTimeout(timer); }
      timer = setTimeout(() => watchdog.abort(), this.heartbeatTimeoutMs);
    };
    try
    {
      arm();
      const res = await this.fetchImpl(`${this.options.baseUrl}/chat/stream`, {
        method: 'POST', headers: { ...this.headers(), Accept: 'text/event-stream' }, body: JSON.stringify(request),
        signal: watchdog.signal });
      if (!res.ok || !res.body)
      {
        return false;
      }
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      const parser = new SseFrameParser();
      for (;;)
      {
        const { done, value } = await reader.read();
        if (done)
        {
          return false;
        }
        arm();   // any bytes, including ": hb" comments, prove the connection is alive
        for (const frame of parser.push(decoder.decode(value, { stream: true })))
        {
          const parsed = this.toEvent(frame.event, frame.data);
          if (parsed)
          {
            handlers.onEvent(parsed);
            if (parsed.event === 'done')
            {
              return true;
            }
            if (parsed.event === 'error')
            {
              return false;
            }
          }
        }
      }
    }
    finally
    {
      if (timer) { clearTimeout(timer); }
      signal.removeEventListener('abort', onAbort);
      watchdog.abort();
    }
  }

  private toEvent(name: string, data: string): ChatStreamEvent | undefined
  {
    if (!KNOWN.includes(name as StreamEventName))
    {
      return undefined;
    }
    try
    {
      const body = data ? JSON.parse(data) : {};
      return { ...body, event: name as StreamEventName } as ChatStreamEvent;
    }
    catch
    {
      return undefined;
    }
  }
}

export type { ChatBlock };
