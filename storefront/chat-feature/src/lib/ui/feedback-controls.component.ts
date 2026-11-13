import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { ChatStore } from './chat.store';
import { CcTPipe } from './t.pipe';

/** Thumbs (CF-07): sends rating and response id only. No text and no customer reference. */
@Component({
  selector: 'cc-feedback-controls', standalone: true, imports: [CcTPipe], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="fb" role="group" aria-label="Rate this answer">
      <button type="button" [attr.aria-pressed]="rating() === 'UP'" [disabled]="rating() !== null" (click)="rate('UP')">👍<span class="sr-only"> {{ 'feedback.up' | ccT }}</span></button>
      <button type="button" [attr.aria-pressed]="rating() === 'DOWN'" [disabled]="rating() !== null" (click)="rate('DOWN')">👎<span class="sr-only"> {{ 'feedback.down' | ccT }}</span></button>
      @if (rating() !== null) { <span role="status">{{ 'feedback.thanks' | ccT }}</span> }
    </div>`,
  styles: [`.fb { display: flex; gap: .5rem; align-items: center; } button { min-width: 44px; min-height: 44px; background: var(--cc-color-surface); border: 1px solid var(--cc-color-ink); border-radius: 6px; cursor: pointer; }
    button[aria-pressed=true] { background: var(--cc-color-ink); } button:focus-visible { outline: 3px solid var(--cc-color-ink); outline-offset: 2px; box-shadow: 0 0 0 2px var(--cc-color-surface); }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }`]
})
export class FeedbackControlsComponent
{
  private readonly store = inject(ChatStore);
  readonly responseId = input.required<string>();
  readonly rating = signal<'UP' | 'DOWN' | null>(null);

  rate(value: 'UP' | 'DOWN'): void
  {
    this.rating.set(value);
    void this.store.feedback(this.responseId(), value);
  }
}
