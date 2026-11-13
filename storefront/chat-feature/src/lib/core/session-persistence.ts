import type { ContextTurn } from './models';
import type { ListEntry } from './chat-state';

export const SCHEMA_VERSION = 1;

export interface Persisted
{
  schemaVersion: number;
  turns: ContextTurn[];
  purchaseList: ListEntry[];
  shortlist: string[];
}

export interface Limits { maxTurns: number; maxChars: number; }

const key = (userId: string) => `cc.chat.${userId}`;

/**
 * sessionStorage persistence (D07, CF-04): per user, schema-versioned, bounded. Stores turn text, product codes, quantities
 * and notes only. Prices and hydrated card data are never stored. Every storage call is wrapped: storage can be blocked.
 */
export class SessionPersistence
{
  constructor(private readonly storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> | undefined, private readonly limits: Limits) {}

  save(userId: string, data: Omit<Persisted, 'schemaVersion'>): void
  {
    try
    {
      this.storage?.setItem(key(userId), JSON.stringify({ schemaVersion: SCHEMA_VERSION, ...this.bound(data) }));
    }
    catch { /* storage blocked or full: the chat still works */ }
  }

  load(userId: string): Persisted | undefined
  {
    try
    {
      const raw = this.storage?.getItem(key(userId));
      if (!raw)
      {
        return undefined;
      }
      const parsed = JSON.parse(raw);
      return parsed?.schemaVersion === SCHEMA_VERSION ? this.sanitise(parsed) : undefined;
    }
    catch
    {
      return undefined;
    }
  }

  clear(userId: string): void
  {
    try { this.storage?.removeItem(key(userId)); } catch { /* ignore */ }
  }

  private bound(data: Omit<Persisted, 'schemaVersion'>): Omit<Persisted, 'schemaVersion'>
  {
    let budget = this.limits.maxChars;
    const turns: ContextTurn[] = [];
    for (let i = data.turns.length - 1; i >= 0 && turns.length < this.limits.maxTurns; i--)
    {
      const t = data.turns[i];
      if (t.text.length > budget)
      {
        break;
      }
      budget -= t.text.length;
      turns.unshift({ role: t.role, text: t.text });
    }
    return {
      turns,
      purchaseList: data.purchaseList.slice(0, 100).map(l => ({ productCode: l.productCode, quantity: l.quantity, note: l.note, origin: l.origin })),
      shortlist: data.shortlist.slice(-10) };
  }

  /** Stored data is untrusted too (tab edits): keep only well-formed fields. */
  private sanitise(p: any): Persisted
  {
    const turns = Array.isArray(p.turns) ? p.turns.filter((t: any) => (t?.role === 'user' || t?.role === 'assistant') && typeof t.text === 'string') : [];
    const list = Array.isArray(p.purchaseList)
      ? p.purchaseList.filter((l: any) => typeof l?.productCode === 'string' && Number.isInteger(l.quantity) && l.quantity > 0)
      : [];
    const shortlist = Array.isArray(p.shortlist) ? p.shortlist.filter((c: any) => typeof c === 'string') : [];
    return { schemaVersion: SCHEMA_VERSION, turns, purchaseList: list, shortlist };
  }
}
