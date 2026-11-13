import { Injectable, computed, inject, signal } from '@angular/core';
import { ChatApi } from '../core/chat-api';
import { addListFromLines, addToList, addUserMessage, applyEvent, initialState, setQuantity, toggleShortlist,
  type ChatState, type ListEntry } from '../core/chat-state';
import { buildRequest } from '../core/context-envelope';
import { SessionPersistence } from '../core/session-persistence';
import type { ChatResponse, PublicConfig, PurchaseListLine, WelcomeItem } from '../core/models';
import { CC_AUTH, CC_CONFIG } from './tokens';

/** Signals store local to the feature (not NgRx). State lives in memory and sessionStorage only (D07, A11). */
@Injectable({ providedIn: 'root' })
export class ChatStore
{
  private readonly auth = inject(CC_AUTH);
  private readonly cfg = inject(CC_CONFIG);
  private readonly api = new ChatApi({ baseUrl: this.cfg.occBaseUrl, getToken: () => this.auth.token(), heartbeatTimeoutMs: this.cfg.heartbeatTimeoutMs });
  private readonly persistence = new SessionPersistence(typeof sessionStorage === 'undefined' ? undefined : sessionStorage,
    { maxTurns: this.cfg.maxTurns, maxChars: this.cfg.maxChars });
  private abort?: AbortController;
  private userId?: string;
  private counter = 0;

  readonly state = signal<ChatState>(initialState());
  readonly config = signal<PublicConfig | undefined>(undefined);
  readonly welcome = signal<WelcomeItem[]>([]);
  readonly validatedList = signal<{ lines: PurchaseListLine[]; totalFormatted?: string; demoLabel?: string; issues: string[] } | undefined>(undefined);
  readonly streaming = computed(() => this.state().status === 'streaming');

  constructor()
  {
    this.auth.userId$.subscribe(id => this.onUser(id));
  }

  private onUser(id: string | undefined): void
  {
    if (this.userId && this.userId !== id)
    {
      this.persistence.clear(this.userId);     // logout or user change: nothing stays behind
      this.cancel();
      this.state.set(initialState());
      this.welcome.set([]);
    }
    this.userId = id;
    if (id)
    {
      const saved = this.persistence.load(id);
      if (saved)
      {
        this.state.update(s => ({ ...s, purchaseList: saved.purchaseList, shortlist: saved.shortlist,
          thread: saved.turns.map((t, i) => ({ id: `p${i}`, role: t.role, text: t.role === 'user' ? t.text : undefined,
            blocks: t.role === 'assistant' ? [{ blockId: `p${i}`, type: 'text' as const, text: t.text }] : [] })) }));
      }
    }
  }

  private persist(): void
  {
    if (!this.userId)
    {
      return;
    }
    const s = this.state();
    const turns = s.thread.map(i => ({ role: i.role, text: i.role === 'user' ? i.text ?? '' : i.blocks.filter(b => b.type === 'text').map(b => b.text ?? '').join(' ') }))
      .filter(t => t.text);
    this.persistence.save(this.userId, { turns, purchaseList: s.purchaseList, shortlist: s.shortlist });
  }

  async loadConfig(): Promise<void>
  {
    const get = async <T>(path: string): Promise<T | undefined> =>
    {
      try
      {
        const r = await fetch(`${this.cfg.occBaseUrl}${path}`, { headers: { Authorization: `Bearer ${this.auth.token() ?? ''}` } });
        return r.ok ? ((await r.json()) as T) : undefined;
      }
      catch { return undefined; }
    };
    this.config.set(await get<PublicConfig>('/chat/config'));
    const w = await get<{ items?: WelcomeItem[] }>('/chat/welcome');
    this.welcome.set(w?.items ?? []);
  }

  async send(message: string): Promise<void>
  {
    const text = message.trim();
    if (!text || this.streaming())
    {
      return;
    }
    const id = `m${++this.counter}-${Date.now()}`;
    const request = buildRequest(this.state(), text, this.cfg.defaultLocale, id,
      { maxTurns: this.cfg.maxTurns, maxChars: this.cfg.maxChars, maxMessageLength: this.config()?.maxMessageLength ?? 1000 });
    this.state.update(s => addUserMessage(s, id, text));
    this.abort = new AbortController();
    try
    {
      await this.api.stream(request, { onEvent: e => this.state.update(s => applyEvent(s, e)) }, this.abort.signal);
    }
    catch
    {
      this.state.update(s => applyEvent(s, { event: 'error', code: 'network' }));
    }
    finally
    {
      this.state.update(s => (s.status === 'streaming' ? { ...s, status: 'idle' } : s));
      this.persist();
    }
  }

  cancel(): void
  {
    this.abort?.abort();
    this.state.update(s => (s.status === 'streaming' ? { ...s, status: 'idle', activeSteps: [] } : s));
  }

  addToList(entry: ListEntry): void { this.state.update(s => addToList(s, entry)); this.persist(); this.revalidate(); }
  addLines(lines: PurchaseListLine[], origin: string): void { this.state.update(s => addListFromLines(s, lines, origin)); this.persist(); this.revalidate(); }
  setQuantity(code: string, qty: number): void { this.state.update(s => setQuantity(s, code, qty)); this.persist(); this.revalidate(); }
  toggleShortlist(code: string): void { this.state.update(s => toggleShortlist(s, code)); this.persist(); }

  /** The server is the only source of prices and availability (A11): every change revalidates. */
  async revalidate(): Promise<void>
  {
    const lines = this.state().purchaseList;
    if (lines.length === 0)
    {
      this.validatedList.set(undefined);
      return;
    }
    try
    {
      const r = await fetch(`${this.cfg.occBaseUrl}/chat/purchase-list/validate`, { method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${this.auth.token() ?? ''}` }, body: JSON.stringify({ lines }) });
      if (r.ok)
      {
        const body = await r.json();
        this.validatedList.set({ lines: body.lines ?? [], totalFormatted: body.totalFormatted, demoLabel: body.demoLabel, issues: body.issues ?? [] });
      }
    }
    catch { /* offline: the local list still works */ }
  }

  async feedback(responseId: string, rating: 'UP' | 'DOWN'): Promise<void>
  {
    try
    {
      await fetch(`${this.cfg.occBaseUrl}/chat/feedback`, { method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${this.auth.token() ?? ''}` },
        body: JSON.stringify({ responseId, rating }) });
    }
    catch { /* feedback is best effort */ }
  }

}
