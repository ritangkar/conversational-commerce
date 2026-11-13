import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { Step } from '../core/chat-state';
import { CcTPipe } from './t.pipe';

@Component({
  selector: 'cc-step-indicator', standalone: true, imports: [CcTPipe], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ul class="cc-steps" aria-label="Progress">
      @for (s of steps(); track s.stepId) {
        <li [class.done]="s.done" [class.failed]="s.failed">
          <span class="dot" aria-hidden="true"></span>
          <span>{{ s.labelKey | ccT }}</span>
          <span class="sr-only">{{ s.done ? (s.failed ? '(failed)' : '(done)') : '(in progress)' }}</span>
        </li>
      }
    </ul>`,
  styles: [`
    .cc-steps { list-style: none; margin: 0; padding: 0; color: var(--cc-color-grey-700); font-size: .875rem; }
    li { display: flex; align-items: center; gap: .5rem; padding: .125rem 0; }
    .dot { width: .6rem; height: .6rem; border-radius: 50%; background: var(--cc-color-primary); animation: cc-pulse 1.2s ease-in-out infinite; }
    .done .dot { background: var(--cc-color-grey-500); animation: none; }
    .failed .dot { background: var(--cc-color-red); animation: none; }
    @keyframes cc-pulse { 50% { opacity: .35; } }
    @media (prefers-reduced-motion: reduce) { .dot { animation: none; } }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }`]
})
export class StepIndicatorComponent { readonly steps = input.required<Step[]>(); }
