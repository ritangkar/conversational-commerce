import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { OrderSummary } from '../core/models';

@Component({
  selector: 'cc-order-summary', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="orders">
      @for (o of orders(); track o.code) {
        <li>
          <strong>{{ o.code }}</strong> · {{ o.placedOn }} · <span class="status">{{ o.statusLabel }}</span> · {{ o.totalFormatted }}
          @if (o.trackingReference) { <div>Tracking reference: {{ o.trackingReference }}</div> }
          <small>Source: {{ o.statusSource }}</small>
        </li>
      }
    </ul>`,
  styles: [`.orders { list-style: none; padding: 0; margin: 0; display: grid; gap: .5rem; }
    li { background: var(--cc-color-surface); border: 1px solid var(--cc-color-border); border-radius: var(--cc-radius, 8px); padding: .6rem; }
    small { color: var(--cc-color-grey-700); }`]
})
export class OrderSummaryComponent { readonly orders = input.required<OrderSummary[]>(); }
