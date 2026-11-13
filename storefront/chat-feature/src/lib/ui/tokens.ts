import { InjectionToken } from '@angular/core';
import type { Observable } from 'rxjs';

/** The only coupling to the storefront's auth. A Spartacus adapter provides this (see spartacus/spartacus-auth.adapter.ts). */
export interface CcAuth
{
  /** Emits the logged-in user's id, or undefined after logout. Logout must clear the chat store (CF-04, D07). */
  userId$: Observable<string | undefined>;
  token(): string | undefined;
}

export interface CcConfig
{
  occBaseUrl: string;          // e.g. https://host/occ/v2/electronics/users/current
  route: string;               // default 'ai-assistant'
  defaultLocale: string;
  heartbeatTimeoutMs: number;
  maxTurns: number;
  maxChars: number;
  storefrontCheckoutUrl: string;
}

export const CC_AUTH = new InjectionToken<CcAuth>('CC_AUTH');
export const CC_CONFIG = new InjectionToken<CcConfig>('CC_CONFIG');
