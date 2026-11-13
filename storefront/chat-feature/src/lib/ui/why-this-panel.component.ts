import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { TraceStep } from '../core/models';
import { CcTPipe } from './t.pipe';

/** Renders whitelisted trace metadata only (CF-08). It never receives ranking weights, margin, prompts or model text. */
@Component({
  selector: 'cc-why-this-panel', standalone: true, imports: [CcTPipe], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (trace().length > 0) {
      <details><summary>{{ 'why.title' | ccT }}</summary>
        <ol>@for (t of trace(); track $index) {
          <li><strong>{{ t.label }}</strong>
            @if (t.source) { <div>{{ 'why.source' | ccT }}: {{ t.source }}</div> }
            @if (t.window) { <div>{{ 'why.window' | ccT }}: {{ t.window }}</div> }
            @if (t.threshold) { <div>{{ 'why.threshold' | ccT }}: {{ t.threshold }}</div> }
          </li> }</ol>
      </details>
    }`,
  styles: [`summary { cursor: pointer; min-height: 44px; display: flex; align-items: center; color: var(--cc-color-primary-strong); } ol { color: var(--cc-color-ink-soft); }`]
})
export class WhyThisPanelComponent { readonly trace = input.required<TraceStep[]>(); }
