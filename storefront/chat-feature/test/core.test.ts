import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { SseFrameParser } from '../src/lib/core/sse-frame-parser';
import { ChatApi } from '../src/lib/core/chat-api';
import { addListFromLines, addToList, addUserMessage, applyEvent, initialState, setQuantity, toggleShortlist } from '../src/lib/core/chat-state';
import { SessionPersistence } from '../src/lib/core/session-persistence';
import { buildRequest } from '../src/lib/core/context-envelope';
import { applyTerminology } from '../src/lib/core/terminology';
import { startBackend, type Backend } from './fake-backend';
import type { ChatStreamEvent } from '../src/lib/core/models';

const limits = { maxTurns: 4, maxChars: 200, maxMessageLength: 50 };

describe('SseFrameParser fixtures', () =>
{
  it('parses frames split across chunks and ignores heartbeats', () =>
  {
    const p = new SseFrameParser();
    const frames = [...p.push('event: step_sta'), ...p.push('rted\ndata: {"stepId":"s1"}\n\n: hb\n\nevent: done\nda'), ...p.push('ta: {}\n\n')];
    expect(frames).toEqual([{ event: 'step_started', data: '{"stepId":"s1"}' }, { event: 'done', data: '{}' }]);
    expect(p.heartbeats).toBe(1);
  });
  it('handles CRLF, multi-line data and unknown fields', () =>
  {
    const frames = new SseFrameParser().push('retry: 5\r\nevent: block\r\ndata: a\r\ndata: b\r\n\r\n');
    expect(frames).toEqual([{ event: 'block', data: 'a\nb' }]);
  });
  it('emits nothing for an incomplete frame', () =>
  {
    expect(new SseFrameParser().push('event: block\ndata: {')).toEqual([]);
  });
});

describe('ChatApi against the fake backend (SC-01)', () =>
{
  let backend: Backend;
  beforeAll(async () => { backend = await startBackend(); });
  afterAll(async () => { await backend.close(); });
  const api = (heartbeatTimeoutMs = 400) => new ChatApi({ baseUrl: backend.url, getToken: () => 'tok', heartbeatTimeoutMs });
  const req = () => buildRequest(initialState(), 'prepare my next order', 'en', 'c1', limits);

  it('sends the bearer token and delivers events incrementally', async () =>
  {
    backend.mode = 'ok';
    const times: number[] = [];
    const events: ChatStreamEvent[] = [];
    const t0 = Date.now();
    await api().stream(req(), { onEvent: e => { events.push(e); times.push(Date.now() - t0); } }, new AbortController().signal);
    expect(events.map(e => e.event).slice(0, 4)).toEqual(['step_started', 'step_completed', 'block', 'block']);
    expect(events.at(-1)?.event).toBe('done');
    const stepStart = times[0];
    const lastBlock = times[events.findIndex(e => e.event === 'done')];
    expect(lastBlock - stepStart).toBeGreaterThanOrEqual(40); // spaced out, not one burst
    expect(backend.seen.at(-1)?.auth).toBe('Bearer tok');
    let state = addUserMessage(initialState(), 'u1', 'hi');
    events.forEach(e => (state = applyEvent(state, e)));
    const text = state.thread.at(-1)!.blocks.find(b => b.type === 'text')!.text;
    expect(text).toContain('estimates from your usual rhythm');
    expect(state.status).toBe('idle');
  });

  it('falls back to POST /chat on a 500 and returns the same blocks', async () =>
  {
    backend.mode = 'stream500';
    const events: ChatStreamEvent[] = [];
    let fell = false;
    await api().stream(req(), { onEvent: e => events.push(e), onFallback: () => (fell = true) }, new AbortController().signal);
    expect(fell).toBe(true);
    expect(events.filter(e => e.event === 'block').map(e => e.block!.blockId)).toEqual(['b-due', 'b-text']);
    expect(events.at(-1)?.event).toBe('done');
  });

  it('falls back when the stream ends before done', async () =>
  {
    backend.mode = 'noDone';
    let fell = false;
    await api().stream(req(), { onEvent: () => {}, onFallback: () => (fell = true) }, new AbortController().signal);
    expect(fell).toBe(true);
  });

  it('heartbeat watchdog aborts a silent stream and falls back', async () =>
  {
    backend.mode = 'silent';
    const events: ChatStreamEvent[] = [];
    let fell = false;
    const t0 = Date.now();
    await api(150).stream(req(), { onEvent: e => events.push(e), onFallback: () => (fell = true) }, new AbortController().signal);
    expect(fell).toBe(true);
    expect(Date.now() - t0).toBeLessThan(2000);
    expect(events.at(-1)?.event).toBe('done');
  });

  it('a user abort stops without fallback', async () =>
  {
    backend.mode = 'slow';
    const c = new AbortController();
    let fell = false;
    const p = api(2000).stream(req(), { onEvent: () => {}, onFallback: () => (fell = true) }, c.signal);
    setTimeout(() => c.abort(), 50);
    await p;
    expect(fell).toBe(false);
  });
});

describe('state, persistence, envelope', () =>
{
  it('quantity edits and list merging stay local and bounded', () =>
  {
    let s = addToList(initialState(), { productCode: 'A', quantity: 2 });
    s = addToList(s, { productCode: 'A', quantity: 3 });
    expect(s.purchaseList[0].quantity).toBe(5);
    expect(setQuantity(s, 'A', 0).purchaseList).toEqual([]);
    expect(setQuantity(s, 'A', 9_999_999).purchaseList[0].quantity).toBe(100000);
    s = addListFromLines(s, [{ productCode: 'B', quantity: 1 }, { productCode: 'C', quantity: 0 }, { productCode: 'D', quantity: 1, issue: 'x' }], 'quick order');
    expect(s.purchaseList.map(l => l.productCode)).toEqual(['A', 'B']);
    expect(toggleShortlist(toggleShortlist(s, 'A'), 'A').shortlist).toEqual([]);
  });

  it('persists per user, bounded, schema-versioned, and clears', () =>
  {
    const mem = new Map<string, string>();
    const storage = { getItem: (k: string) => mem.get(k) ?? null, setItem: (k: string, v: string) => void mem.set(k, v), removeItem: (k: string) => void mem.delete(k) };
    const p = new SessionPersistence(storage, { maxTurns: 2, maxChars: 20 });
    p.save('u1', { turns: [{ role: 'user', text: 'one' }, { role: 'assistant', text: 'two' }, { role: 'user', text: 'three' }], purchaseList: [{ productCode: 'A', quantity: 1 }], shortlist: ['A'] });
    expect([...mem.keys()]).toEqual(['cc.chat.u1']);
    expect(p.load('u1')?.turns.map(t => t.text)).toEqual(['two', 'three']);
    expect(p.load('u2')).toBeUndefined();
    mem.set('cc.chat.u1', JSON.stringify({ schemaVersion: 99 }));
    expect(p.load('u1')).toBeUndefined();
    mem.set('cc.chat.u1', JSON.stringify({ schemaVersion: 1, turns: [{ role: 'x', text: 1 }], purchaseList: [{ productCode: 'A', quantity: -3 }], shortlist: [5] }));
    expect(p.load('u1')).toMatchObject({ turns: [], purchaseList: [], shortlist: [] });
    p.clear('u1');
    expect(mem.size).toBe(0);
    expect(() => new SessionPersistence(undefined, { maxTurns: 1, maxChars: 1 }).save('u', { turns: [], purchaseList: [], shortlist: [] })).not.toThrow();
  });

  it('never stores prices or card data', () =>
  {
    const mem = new Map<string, string>();
    const p = new SessionPersistence({ getItem: k => mem.get(k) ?? null, setItem: (k, v) => void mem.set(k, v), removeItem: () => {} }, { maxTurns: 5, maxChars: 500 });
    p.save('u', { turns: [], purchaseList: [{ productCode: 'A', quantity: 1, lineFormatted: 'INR 5' } as any], shortlist: [] });
    expect(mem.get('cc.chat.u')).not.toContain('INR');
  });

  it('builds a bounded request with no prices', () =>
  {
    let s = addUserMessage(initialState(), 'u1', 'x'.repeat(150));
    s = addUserMessage(s, 'u2', 'second');
    const r = buildRequest(s, '  hello  ', 'hi', 'id', { ...limits, maxChars: 100 });
    expect(r.message).toBe('hello');
    expect(r.context.turns.map(t => t.text)).toEqual(['second']);
    expect(JSON.stringify(r)).not.toMatch(/price|INR/i);
    expect(buildRequest(s, 'y'.repeat(500), 'en', 'id', limits).message).toHaveLength(50);
  });
});

describe('terminology', () =>
{
  it('replaces words but never identifiers or numbers', () =>
  {
    expect(applyTerminology('The product EL-4821 costs 20 products', { product: 'tyre' })).toBe('The tyre EL-4821 costs 20 products');
    expect(applyTerminology('x', undefined)).toBe('x');
  });
});
