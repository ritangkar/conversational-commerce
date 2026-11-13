import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ChatStore } from './chat.store';
import { CcTPipe } from './t.pipe';

/** D08: shown whenever interest events are on. Wording is a Backoffice field (placeholder until legal review). */
@Component({
  selector: 'cc-interest-notice', standalone: true, imports: [CcTPipe], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (store.config()?.interestEventsEnabled) {
      <aside class="interest" aria-labelledby="cc-interest-title">
        <strong id="cc-interest-title">{{ 'interest.title' | ccT }}</strong>
        <span> {{ store.config()?.interestNoticeText }}</span>
      </aside>
    }`,
  styles: [`.interest { font-size: .85rem; color: var(--cc-color-grey-700); padding: .5rem .75rem; border-top: 1px solid var(--cc-color-border); }`]
})
export class InterestNoticeComponent { readonly store = inject(ChatStore); }
