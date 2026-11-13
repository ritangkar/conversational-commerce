import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import type { ProductCard } from '../core/models';
import { CcTPipe } from './t.pipe';

@Component({
  selector: 'cc-product-card', standalone: true, imports: [CcTPipe], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <article class="card" [attr.aria-label]="card().name">
      @if (card().imageUrl) { <img [src]="card().imageUrl" alt="" width="96" height="96" loading="lazy"> }
      <h3>{{ card().name }} <span class="code">{{ card().code }}</span></h3>
      @if (card().priceFormatted) { <p class="price">{{ card().priceFormatted }}</p> }
      @if (card().stockLabel) {
        <p class="stock">{{ card().stockLabel }}
          @if (card().stockIsDemo) { <span class="demo">{{ 'card.demoStock' | ccT }}</span> }
        </p>
      }
      <button type="button" class="primary" (click)="add.emit(card())">{{ 'card.add' | ccT }}<span class="sr-only"> {{ card().name }}</span></button>
      <button type="button" class="secondary" [attr.aria-pressed]="shortlisted()" (click)="shortlist.emit(card())">{{ 'card.compare' | ccT }}<span class="sr-only"> {{ card().name }}</span></button>
    </article>`,
  styles: [`
    .card { background: var(--cc-color-surface); border: 1px solid var(--cc-color-border); border-radius: var(--cc-radius, 8px); padding: .75rem; display: grid; gap: .25rem; }
    h3 { margin: 0; font-size: 1rem; color: var(--cc-color-ink); }
    .code { color: var(--cc-color-grey-700); font-weight: 400; font-size: .85rem; }
    .price { margin: 0; font-weight: 600; color: var(--cc-color-ink); }
    .stock { margin: 0; color: var(--cc-color-success); }
    .demo { background: var(--cc-color-amber); color: var(--cc-color-ink); padding: 0 .4rem; border-radius: 4px; margin-left: .25rem; font-size: .75rem; }
    button { min-height: 44px; border-radius: 6px; border: 1px solid var(--cc-color-ink); font: inherit; cursor: pointer; }
    .primary { background: var(--cc-color-primary); color: var(--cc-color-ink); border-color: var(--cc-color-primary); }
    .secondary { background: var(--cc-color-surface); color: var(--cc-color-ink); }
    .secondary[aria-pressed=true] { background: var(--cc-color-ink); color: var(--cc-color-surface); }
    button:focus-visible { outline: 3px solid var(--cc-color-ink); outline-offset: 2px; box-shadow: 0 0 0 2px var(--cc-color-surface); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }`]
})
export class ProductCardComponent
{
  readonly card = input.required<ProductCard>();
  readonly shortlisted = input(false);
  readonly add = output<ProductCard>();
  readonly shortlist = output<ProductCard>();
}
