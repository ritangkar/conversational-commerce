import type { Routes } from '@angular/router';
import { ChatPageComponent } from './ui/chat-page.component';

/**
 * Lazy route. The host app adds the Spartacus auth guard (spartacus/chat-feature.module.ts) and excludes the path from SSR.
 * Default path 'ai-assistant', configurable through CcConfig.route.
 */
export const CHAT_ROUTES: Routes = [{ path: '', component: ChatPageComponent }];
