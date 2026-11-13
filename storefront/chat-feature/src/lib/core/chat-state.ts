import type { ChatBlock, ChatStreamEvent, PurchaseListLine, TraceStep } from './models';

export interface Step { stepId: string; labelKey: string; done: boolean; failed: boolean; }

export interface ThreadItem
{
  id: string;
  role: 'user' | 'assistant';
  text?: string;                 // user text
  blocks: ChatBlock[];           // assistant blocks (rendered from validated server data)
  responseId?: string;
  trace?: TraceStep[];
  streaming?: boolean;
}

export interface ListEntry { productCode: string; quantity: number; note?: string; origin?: string; }

export type Status = 'idle' | 'streaming' | 'error' | 'capped';

export interface ChatState
{
  thread: ThreadItem[];
  activeSteps: Step[];
  status: Status;
  purchaseList: ListEntry[];
  shortlist: string[];
  lastError?: string;
  /** Text revealed so far per block id; the full text lives in the block, the typewriter effect reads this. */
  revealed: Record<string, string>;
}

export const initialState = (): ChatState => ({
  thread: [], activeSteps: [], status: 'idle', purchaseList: [], shortlist: [], revealed: {} });

export function addUserMessage(state: ChatState, id: string, text: string): ChatState
{
  return {
    ...state,
    thread: [...state.thread, { id, role: 'user', text, blocks: [] }, { id: `${id}-a`, role: 'assistant', blocks: [], streaming: true }],
    activeSteps: [], status: 'streaming', lastError: undefined };
}

export function applyEvent(state: ChatState, event: ChatStreamEvent): ChatState
{
  const thread = [...state.thread];
  const last = thread.length - 1;
  const assistant = last >= 0 && thread[last].role === 'assistant' ? { ...thread[last] } : undefined;
  switch (event.event)
  {
    case 'step_started':
      return { ...state, activeSteps: [...state.activeSteps, { stepId: event.stepId ?? '', labelKey: event.labelKey ?? '', done: false, failed: false }] };
    case 'step_completed':
      return { ...state, activeSteps: state.activeSteps.map(s =>
        s.stepId === event.stepId ? { ...s, done: true, failed: event.status === 'failed' } : s) };
    case 'block':
      if (!assistant || !event.block)
      {
        return state;
      }
      assistant.blocks = [...assistant.blocks, event.block];
      thread[last] = assistant;
      return { ...state, thread, revealed: event.block.type === 'text' ? { ...state.revealed, [event.block.blockId]: '' } : state.revealed };
    case 'text_delta':
      if (!assistant)
      {
        return state;
      }
      assistant.blocks = assistant.blocks.map(b => b.blockId === event.stepId ? { ...b, text: (b.text ?? '') + (event.delta ?? '') } : b);
      thread[last] = assistant;
      return { ...state, thread };
    case 'done':
      if (assistant)
      {
        assistant.streaming = false;
        assistant.responseId = event.responseId ?? assistant.responseId;
        assistant.trace = event.trace ?? assistant.trace;
        thread[last] = assistant;
      }
      return { ...state, thread, status: 'idle', activeSteps: state.activeSteps.map(s => ({ ...s, done: true })) };
    case 'error':
      return { ...state, status: 'error', lastError: event.code ?? 'error', thread: finishAssistant(thread) };
    default:
      return state;
  }
}

function finishAssistant(thread: ThreadItem[]): ThreadItem[]
{
  const last = thread.length - 1;
  if (last >= 0 && thread[last].role === 'assistant')
  {
    thread[last] = { ...thread[last], streaming: false };
  }
  return thread;
}

export function setResponseId(state: ChatState, responseId: string): ChatState
{
  const thread = [...state.thread];
  const last = thread.length - 1;
  if (last >= 0 && thread[last].role === 'assistant')
  {
    thread[last] = { ...thread[last], responseId };
  }
  return { ...state, thread };
}

export function addToList(state: ChatState, entry: ListEntry): ChatState
{
  const existing = state.purchaseList.find(l => l.productCode === entry.productCode);
  const purchaseList = existing
    ? state.purchaseList.map(l => (l.productCode === entry.productCode ? { ...l, quantity: l.quantity + entry.quantity } : l))
    : [...state.purchaseList, entry];
  return { ...state, purchaseList };
}

export function setQuantity(state: ChatState, productCode: string, quantity: number): ChatState
{
  const q = Math.max(0, Math.min(100000, Math.floor(quantity)));
  return { ...state, purchaseList: q === 0
    ? state.purchaseList.filter(l => l.productCode !== productCode)
    : state.purchaseList.map(l => (l.productCode === productCode ? { ...l, quantity: q } : l)) };
}

export function addListFromLines(state: ChatState, lines: PurchaseListLine[], origin: string): ChatState
{
  return lines.filter(l => l.quantity > 0 && !l.issue).reduce(
    (s, l) => addToList(s, { productCode: l.productCode, quantity: l.quantity, origin }), state);
}

export function toggleShortlist(state: ChatState, code: string): ChatState
{
  const has = state.shortlist.includes(code);
  return { ...state, shortlist: has ? state.shortlist.filter(c => c !== code) : [...state.shortlist, code].slice(-10) };
}
