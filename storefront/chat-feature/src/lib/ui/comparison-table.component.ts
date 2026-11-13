import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { Comparison } from '../core/models';

@Component({
  selector: 'cc-comparison-table', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="wrap" tabindex="0" role="region" aria-label="Product comparison">
      <table>
        <caption class="sr-only">Comparison of {{ data().columns.join(', ') }}</caption>
        <thead><tr><th scope="col">Attribute</th>@for (c of data().columns; track c) { <th scope="col">{{ c }}</th> }</tr></thead>
        <tbody>
          @for (r of data().rows; track r.attributeLabel) {
            <tr><th scope="row">{{ r.attributeLabel }}</th>@for (v of r.values; track $index) { <td>{{ v }}</td> }</tr>
          }
        </tbody>
      </table>
      @for (n of data().notes ?? []; track n) { <p class="note">{{ n }}</p> }
    </div>`,
  styles: [`
    .wrap { overflow-x: auto; }
    table { border-collapse: collapse; min-width: 100%; color: var(--cc-color-ink-soft); }
    th, td { border: 1px solid var(--cc-color-border); padding: .4rem .6rem; text-align: left; }
    thead th { background: var(--cc-color-surface-alt); }
    .note { color: var(--cc-color-grey-700); font-size: .85rem; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }`]
})
export class ComparisonTableComponent { readonly data = input.required<Comparison>(); }
