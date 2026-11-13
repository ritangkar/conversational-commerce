import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import type { Recommendation } from '../core/models';
import { CcTPipe } from './t.pipe';

@Component({
  selector: 'cc-recommendation-list', standalone: true, imports: [CcTPipe], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="recs">
      @for (r of items(); track r.code + r.reasonKey) {
        <li>
          <span><strong>{{ r.name || r.code }}</strong> <span class="code">{{ r.code }}</span>
            @if (r.quantity) { · {{ r.quantity }} }</span>
          <span class="why">{{ 'reason.' + r.reasonKey | ccT }}
            @if (r.estimate) { <span class="est">{{ 'estimate.label' | ccT }}</span> }</span>
          @if (r.quantity) {
            <button type="button" (click)="add.emit(r)">{{ 'card.add' | ccT }}<span class="sr-only"> {{ r.name || r.code }}</span></button>
          }
        </li>
      }
    </ul>`,
  styles: [`.recs { list-style: none; padding: 0; margin: 0; display: grid; gap: .5rem; }
    li { display: grid; gap: .25rem; background: var(--cc-color-surface); border: 1px solid var(--cc-color-border); border-radius: var(--cc-radius, 8px); padding: .6rem; }
    .code, .why { color: var(--cc-color-grey-700); font-size: .85rem; }
    .est { background: var(--cc-color-amber); color: var(--cc-color-ink); padding: 0 .4rem; border-radius: 4px; margin-left: .25rem; }
    button { min-height: 44px; justify-self: start; background: var(--cc-color-primary); color: var(--cc-color-ink); border: 0; border-radius: 6px; padding: 0 1rem; cursor: pointer; font: inherit; }
    button:focus-visible { outline: 3px solid var(--cc-color-ink); outline-offset: 2px; box-shadow: 0 0 0 2px var(--cc-color-surface); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }`]
})
export class RecommendationListComponent
{
  readonly items = input.required<Recommendation[]>();
  readonly add = output<Recommendation>();
}
