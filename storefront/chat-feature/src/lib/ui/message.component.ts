import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import type { ThreadItem } from '../core/chat-state';
import { BlockRendererComponent } from './block-renderer.component';
import { FeedbackControlsComponent } from './feedback-controls.component';
import { WhyThisPanelComponent } from './why-this-panel.component';

@Component({
  selector: 'cc-message', standalone: true, imports: [BlockRendererComponent, FeedbackControlsComponent, WhyThisPanelComponent], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (item().role === 'user') {
      <div class="bubble user"><span class="sr-only">You said:</span> {{ item().text }}</div>
    } @else {
      <div class="bubble assistant">
        <span class="sr-only">Assistant:</span>
        @for (b of item().blocks; track b.blockId) { <cc-block-renderer [block]="b"/> }
        @if (!item().streaming && item().trace) { <cc-why-this-panel [trace]="item().trace!"/> }
        @if (!item().streaming && item().responseId) { <cc-feedback-controls [responseId]="item().responseId!"/> }
      </div>
    }`,
  styles: [`.bubble { padding: .75rem 1rem; border-radius: var(--cc-radius, 8px); max-width: 52rem; display: grid; gap: .75rem; }
    .user { background: var(--cc-color-ink); color: var(--cc-color-surface); justify-self: end; }
    .assistant { background: var(--cc-color-surface-alt); color: var(--cc-color-ink-soft); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }`]
})
export class MessageComponent { readonly item = input.required<ThreadItem>(); }
