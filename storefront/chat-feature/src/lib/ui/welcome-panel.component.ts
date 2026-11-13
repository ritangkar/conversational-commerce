import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { WelcomeItem } from '../core/models';
import { CcTPipe } from './t.pipe';

@Component({
  selector: 'cc-welcome-panel', standalone: true, imports: [CcTPipe], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (items().length > 0) {
      <section class="welcome" aria-labelledby="cc-welcome-title">
        <h2 id="cc-welcome-title">{{ 'welcome.title' | ccT }}</h2>
        <ul>@for (i of items(); track $index) { <li>{{ i.textKey | ccT: i.params }}</li> }</ul>
      </section>
    }`,
  styles: [`.welcome { background: var(--cc-color-surface-alt); border-left: 4px solid var(--cc-color-primary); padding: .75rem 1rem; border-radius: var(--cc-radius, 8px); }
    h2 { margin: 0 0 .25rem; font-size: 1.1rem; } ul { margin: 0; padding-left: 1.1rem; color: var(--cc-color-ink-soft); }`]
})
export class WelcomePanelComponent { readonly items = input.required<WelcomeItem[]>(); }
