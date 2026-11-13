import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ChatStore } from './chat.store';
import { CcTPipe } from './t.pipe';
import { CC_CONFIG } from './tokens';

/** Editable, browser-held list (PA-01, PA-02). Always labelled as a demo list: it is NOT the cart (D03). */
@Component({
  selector: 'cc-purchase-list', standalone: true, imports: [CcTPipe], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="list" aria-labelledby="cc-list-title">
      <h2 id="cc-list-title">{{ 'list.title' | ccT }}</h2>
      <p class="demo" role="note">{{ label() }}</p>
      @if (store.state().purchaseList.length === 0) { <p>{{ 'list.empty' | ccT }}</p> }
      <ul>
        @for (l of store.state().purchaseList; track l.productCode) {
          <li>
            <span>{{ nameOf(l.productCode) }} <span class="code">{{ l.productCode }}</span></span>
            <label>
              <span class="sr-only">{{ 'list.quantity' | ccT }} {{ l.productCode }}</span>
              <input type="number" min="0" max="100000" [value]="l.quantity" (change)="setQty(l.productCode, $event)">
            </label>
            <span>{{ lineOf(l.productCode) }}</span>
            <button type="button" (click)="store.setQuantity(l.productCode, 0)">{{ 'list.remove' | ccT }}<span class="sr-only"> {{ l.productCode }}</span></button>
          </li>
        }
      </ul>
      @for (i of store.validatedList()?.issues ?? []; track i) { <p class="issue" role="alert">{{ i }}</p> }
      @if (store.validatedList()?.totalFormatted; as total) { <p><strong>{{ total }}</strong></p> }
      @if (store.state().purchaseList.length > 0) { <a [href]="cfg.storefrontCheckoutUrl">{{ 'list.checkout' | ccT }}</a> }
    </section>`,
  styles: [`.list { border: 1px solid var(--cc-color-border); border-radius: var(--cc-radius, 8px); padding: .75rem; background: var(--cc-color-surface); }
    h2 { margin: 0 0 .25rem; font-size: 1.1rem; }
    .demo { background: var(--cc-color-amber); color: var(--cc-color-ink); display: inline-block; padding: .1rem .5rem; border-radius: 4px; margin: 0 0 .5rem; }
    ul { list-style: none; margin: 0; padding: 0; display: grid; gap: .5rem; }
    li { display: grid; grid-template-columns: 1fr 5rem auto auto; gap: .5rem; align-items: center; }
    input { min-height: 44px; width: 5rem; }
    button { min-height: 44px; }
    .code { color: var(--cc-color-grey-700); font-size: .85rem; }
    .issue { color: var(--cc-color-red); }
    a { color: var(--cc-color-primary-strong); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }`]
})
export class PurchaseListComponent
{
  readonly store = inject(ChatStore);
  readonly cfg = inject(CC_CONFIG);
  readonly label = computed(() => this.store.validatedList()?.demoLabel ?? this.store.config()?.purchaseListLabel ?? 'Demo purchase list, not your cart');

  nameOf(code: string): string { return this.store.validatedList()?.lines.find(l => l.productCode === code)?.name ?? ''; }
  lineOf(code: string): string { return this.store.validatedList()?.lines.find(l => l.productCode === code)?.lineFormatted ?? ''; }
  setQty(code: string, event: Event): void { this.store.setQuantity(code, Number((event.target as HTMLInputElement).value)); }
}
