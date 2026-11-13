import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { ChatStore } from './chat.store';
import { ComposerComponent } from './composer.component';
import { InterestNoticeComponent } from './interest-notice.component';
import { PurchaseListComponent } from './purchase-list.component';
import { ThreadComponent } from './thread.component';
import { WelcomePanelComponent } from './welcome-panel.component';
import { CcTPipe } from './t.pipe';

/** Full-page assistant (CF-01). Client-side only: the route is excluded from SSR (see docs/spikes/S1.md). */
@Component({
  selector: 'cc-chat-page', standalone: true, changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ThreadComponent, ComposerComponent, WelcomePanelComponent, PurchaseListComponent, InterestNoticeComponent, CcTPipe],
  template: `
    <a class="skip" href="#cc-message">{{ 'page.skip' | ccT }}</a>
    <header class="head"><h1>{{ 'page.title' | ccT }}</h1></header>
    <main class="layout">
      <section class="chat">
        @if (store.state().thread.length === 0) { <cc-welcome-panel [items]="store.welcome()"/> }
        @if (store.state().thread.length === 0 && (store.config()?.samplePrompts?.length ?? 0) > 0) {
          <div class="samples" role="group" aria-label="Example questions">
            @for (p of store.config()!.samplePrompts!; track p) { <button type="button" (click)="store.send(p)">{{ p }}</button> }
          </div>
        }
        <cc-thread/>
        <cc-composer/>
        <cc-interest-notice/>
      </section>
      <aside class="side"><cc-purchase-list/></aside>
    </main>`,
  styles: [`:host { display: block; background: var(--cc-color-surface); color: var(--cc-color-ink-soft); font-family: var(--cc-font-family, inherit); }
    .head { background: var(--cc-color-ink); color: var(--cc-color-surface); border-bottom: 4px solid var(--cc-color-primary); padding: .75rem 1rem; display: flex; align-items: center; gap: .75rem; }
    .head::before { content: ''; width: 2rem; height: 2rem; background: var(--cc-logo-url, none) center / contain no-repeat; }
    h1 { margin: 0; font-size: 1.25rem; }
    .layout { display: grid; gap: 1rem; padding: 1rem; grid-template-columns: minmax(0, 1fr); max-width: 80rem; margin: 0 auto; }
    @media (min-width: 64rem) { .layout { grid-template-columns: minmax(0, 2fr) minmax(16rem, 1fr); } }
    .chat { display: grid; gap: 1rem; align-content: start; }
    .samples { display: flex; flex-wrap: wrap; gap: .5rem; }
    .samples button { min-height: 44px; border-radius: 22px; border: 1px solid var(--cc-color-ink); background: var(--cc-color-surface); color: var(--cc-color-ink); padding: 0 1rem; cursor: pointer; font: inherit; text-align: left; }
    .skip { position: absolute; left: -999px; } .skip:focus { left: 0; background: var(--cc-color-surface); padding: .5rem; z-index: 10; }
    button:focus-visible { outline: 3px solid var(--cc-color-ink); outline-offset: 2px; box-shadow: 0 0 0 2px var(--cc-color-surface); }`]
})
export class ChatPageComponent implements OnInit
{
  readonly store = inject(ChatStore);
  ngOnInit(): void { void this.store.loadConfig(); void this.store.revalidate(); }
}
