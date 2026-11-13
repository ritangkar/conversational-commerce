// UNVERIFIED against the owner's Spartacus version. Feature wiring is based on Spartacus' lazy-loaded feature configuration.
import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { AuthGuard } from '@spartacus/storefront';
import { CC_AUTH, CC_CONFIG, type CcConfig } from '../ui/tokens';
import { SpartacusAuthAdapter } from './spartacus-auth.adapter';

export const DEFAULT_CC_CONFIG: CcConfig = {
  occBaseUrl: '/occ/v2/electronics/users/current',   // ASSUMPTION: replace with the OCC base URL of the host storefront
  route: 'ai-assistant', defaultLocale: 'en', heartbeatTimeoutMs: 25000, maxTurns: 6, maxChars: 2000, storefrontCheckoutUrl: '/cart' };

/** Host app: RouterModule.forChild([{ path: 'ai-assistant', canActivate: [AuthGuard], loadChildren: () => import('@conversational-commerce/chat').then(m => m.CHAT_ROUTES) }]) */
@NgModule({
  imports: [RouterModule],
  providers: [{ provide: CC_AUTH, useExisting: SpartacusAuthAdapter }, { provide: CC_CONFIG, useValue: DEFAULT_CC_CONFIG }]
})
export class ChatFeatureModule {}

export { AuthGuard };
