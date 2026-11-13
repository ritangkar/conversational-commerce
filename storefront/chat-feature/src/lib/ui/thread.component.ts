import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ChatStore } from './chat.store';
import { MessageComponent } from './message.component';
import { StepIndicatorComponent } from './step-indicator.component';
import { CcTPipe } from './t.pipe';

@Component({
  selector: 'cc-thread', standalone: true, imports: [MessageComponent, StepIndicatorComponent, CcTPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="thread" role="log" aria-live="polite" aria-relevant="additions text" [attr.aria-label]="'thread.label' | ccT">
      @if (store.state().thread.length === 0) { <p class="empty">{{ 'thread.empty' | ccT }}</p> }
      @for (item of store.state().thread; track item.id) { <cc-message [item]="item"/> }
      @if (store.streaming() && store.state().activeSteps.length > 0) { <cc-step-indicator [steps]="store.state().activeSteps"/> }
    </div>
    @if (store.state().status === 'error') { <p class="error" role="alert">{{ 'error.generic' | ccT }}</p> }`,
  styles: [`.thread { display: grid; gap: 1rem; } .empty { color: var(--cc-color-grey-700); } .error { color: var(--cc-color-red); }`]
})
export class ThreadComponent { readonly store = inject(ChatStore); }
