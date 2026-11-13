import type { ChatRequest } from './models';
import type { ChatState } from './chat-state';
import { SCHEMA_VERSION } from './session-persistence';

export interface EnvelopeLimits { maxTurns: number; maxChars: number; maxMessageLength: number; }

/** Builds the request body: bounded context from the thread, the list and the shortlist. Never any price or card data. */
export function buildRequest(state: ChatState, message: string, locale: string, clientRequestId: string, limits: EnvelopeLimits): ChatRequest
{
  const text = message.trim().slice(0, limits.maxMessageLength);
  let budget = limits.maxChars;
  const turns: ChatRequest['context']['turns'] = [];
  for (let i = state.thread.length - 1; i >= 0 && turns.length < limits.maxTurns; i--)
  {
    const item = state.thread[i];
    const t = item.role === 'user'
      ? item.text ?? ''
      : item.blocks.filter(b => b.type === 'text').map(b => b.text ?? '').join(' ');
    if (!t || t.length > budget)
    {
      continue;
    }
    budget -= t.length;
    turns.unshift({ role: item.role, text: t });
  }
  return {
    message: text, clientRequestId, uiLocale: locale,
    context: { turns, shortlistCodes: state.shortlist.slice(-10), schemaVersion: SCHEMA_VERSION },
    purchaseList: state.purchaseList.map(l => ({ productCode: l.productCode, quantity: l.quantity, note: l.note, origin: l.origin })) };
}
