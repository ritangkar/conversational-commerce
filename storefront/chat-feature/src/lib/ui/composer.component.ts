import { ChangeDetectionStrategy, Component, ElementRef, ViewChild, inject, signal } from '@angular/core';
import { ChatStore } from './chat.store';
import { CcTPipe } from './t.pipe';

@Component({
  selector: 'cc-composer', standalone: true, imports: [CcTPipe], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form (submit)="submit($event)" class="composer">
      <label for="cc-message">{{ 'composer.label' | ccT }}</label>
      <textarea id="cc-message" #box rows="2" [maxLength]="store.config()?.maxMessageLength ?? 1000"
        aria-describedby="cc-hint" [value]="text()" (input)="text.set(box.value)" (keydown)="onKey($event)"></textarea>
      <p id="cc-hint" class="hint">{{ 'composer.hint' | ccT }}</p>
      <div class="actions">
        @if (store.streaming()) { <button type="button" (click)="stop()">{{ 'composer.stop' | ccT }}</button> }
        @else { <button type="submit" class="send" [disabled]="text().trim().length === 0">{{ 'composer.send' | ccT }}</button> }
      </div>
    </form>`,
  styles: [`.composer { display: grid; gap: .25rem; } textarea { font: inherit; padding: .5rem; border: 1px solid var(--cc-color-grey-500); border-radius: var(--cc-radius, 8px); }
    .hint { margin: 0; font-size: .8rem; color: var(--cc-color-grey-700); } .actions { display: flex; justify-content: flex-end; }
    button { min-height: 44px; padding: 0 1.25rem; border-radius: 6px; border: 1px solid var(--cc-color-ink); font: inherit; cursor: pointer; background: var(--cc-color-surface); color: var(--cc-color-ink); }
    .send { background: var(--cc-color-primary); color: var(--cc-color-ink); border-color: var(--cc-color-primary); } .send:disabled { opacity: .6; cursor: not-allowed; }
    button:focus-visible, textarea:focus-visible { outline: 3px solid var(--cc-color-ink); outline-offset: 2px; box-shadow: 0 0 0 2px var(--cc-color-surface); }`]
})
export class ComposerComponent
{
  readonly store = inject(ChatStore);
  readonly text = signal('');
  @ViewChild('box') box?: ElementRef<HTMLTextAreaElement>;

  onKey(event: KeyboardEvent): void
  {
    if (event.key === 'Enter' && !event.shiftKey)
    {
      event.preventDefault();
      this.submit(event);
    }
    else if (event.key === 'Escape' && this.store.streaming())
    {
      this.stop();
    }
  }

  submit(event: Event): void
  {
    event.preventDefault();
    const value = this.text();
    this.text.set('');
    void this.store.send(value).then(() => this.box?.nativeElement.focus());
  }

  stop(): void { this.store.cancel(); this.box?.nativeElement.focus(); }
}
