import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

@Component({
  selector: 'cc-clarification-chips', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="chips" role="group" aria-label="Quick replies">
    @for (o of options(); track o) { <button type="button" (click)="pick.emit(o)">{{ o }}</button> }</div>`,
  styles: [`.chips { display: flex; flex-wrap: wrap; gap: .5rem; }
    button { min-height: 44px; border-radius: 22px; border: 1px solid var(--cc-color-ink); background: var(--cc-color-surface); color: var(--cc-color-ink); padding: 0 1rem; cursor: pointer; font: inherit; }
    button:focus-visible { outline: 3px solid var(--cc-color-ink); outline-offset: 2px; box-shadow: 0 0 0 2px var(--cc-color-surface); }`]
})
export class ClarificationChipsComponent
{
  readonly options = input.required<string[]>();
  readonly pick = output<string>();
}
