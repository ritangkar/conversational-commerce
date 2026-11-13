import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CcTPipe } from './t.pipe';

@Component({
  selector: 'cc-notice', standalone: true, imports: [CcTPipe], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p class="notice" role="note"><span aria-hidden="true">ℹ</span> {{ key() ? ('notice.' + key() | ccT) : '' }} {{ fallback() }}</p>`,
  styles: [`.notice { margin: 0; padding: .5rem .75rem; background: var(--cc-color-surface-alt); border-left: 4px solid var(--cc-color-amber); color: var(--cc-color-ink-soft); }`]
})
export class NoticeComponent
{
  readonly key = input<string | undefined>();
  readonly fallback = input<string | undefined>();
}
