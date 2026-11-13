import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import type { ChatBlock, ProductCard, Recommendation } from '../core/models';
import { applyTerminology } from '../core/terminology';
import { ChatStore } from './chat.store';
import { ClarificationChipsComponent } from './clarification-chips.component';
import { ComparisonTableComponent } from './comparison-table.component';
import { NoticeComponent } from './notice.component';
import { OrderSummaryComponent } from './order-summary.component';
import { ProductCardComponent } from './product-card.component';
import { RecommendationListComponent } from './recommendation-list.component';

/** Switches on block.type. Unknown types render nothing: the browser only renders validated blocks (A13). */
@Component({
  selector: 'cc-block-renderer', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ProductCardComponent, ComparisonTableComponent, OrderSummaryComponent, RecommendationListComponent,
    ClarificationChipsComponent, NoticeComponent],
  template: `
    @switch (block().type) {
      @case ('text') { <p class="text">{{ term(block().text) }}</p> }
      @case ('productCards') {
        <div class="cards">
          @for (c of block().productCards ?? []; track c.code) {
            <cc-product-card [card]="c" [shortlisted]="store.state().shortlist.includes(c.code)"
              (add)="addCard($event)" (shortlist)="store.toggleShortlist($event.code)"/>
          }
        </div>
      }
      @case ('comparison') { @if (block().comparison; as cmp) { <cc-comparison-table [data]="cmp"/> } }
      @case ('orderSummary') { <cc-order-summary [orders]="block().orderSummaries ?? []"/> }
      @case ('purchaseList') {
        <div>
          <ul class="lines">
            @for (l of block().purchaseListLines ?? []; track l.productCode) {
              <li>{{ l.name }} <span class="code">{{ l.productCode }}</span> × {{ l.quantity }} {{ l.lineFormatted }}
                @if (l.issue) { <em>({{ l.issue }})</em> }</li>
            }
          </ul>
          <button type="button" (click)="store.addLines(block().purchaseListLines ?? [], 'assistant')">Add these lines to my demo purchase list</button>
        </div>
      }
      @case ('recommendationList') { <cc-recommendation-list [items]="block().recommendations ?? []" (add)="addRec($event)"/> }
      @case ('clarification') {
        <p>{{ block().text }}</p>
        <cc-clarification-chips [options]="block().clarification ?? []" (pick)="store.send($event)"/>
      }
      @case ('notice') { <cc-notice [key]="block().noticeKey" [fallback]="block().text"/> }
    }`,
  styles: [`.text { margin: 0; white-space: pre-wrap; color: var(--cc-color-ink-soft); } .cards { display: grid; gap: .75rem; grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr)); }
    .lines { margin: 0 0 .5rem; padding-left: 1.1rem; } .code { color: var(--cc-color-grey-700); font-size: .85rem; }
    button { min-height: 44px; background: var(--cc-color-primary); color: var(--cc-color-ink); border: 0; border-radius: 6px; padding: 0 1rem; cursor: pointer; font: inherit; }
    button:focus-visible { outline: 3px solid var(--cc-color-ink); outline-offset: 2px; box-shadow: 0 0 0 2px var(--cc-color-surface); }`]
})
export class BlockRendererComponent
{
  readonly store = inject(ChatStore);
  readonly block = input.required<ChatBlock>();

  term(text: string | undefined): string { return applyTerminology(text ?? '', this.store.config()?.terminology); }
  addCard(card: ProductCard): void { this.store.addToList({ productCode: card.code, quantity: 1, origin: 'product card' }); }
  addRec(r: Recommendation): void { this.store.addToList({ productCode: r.code, quantity: r.quantity ?? 1, origin: 'recommendation' }); }
}
