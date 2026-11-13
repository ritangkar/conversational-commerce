// UNVERIFIED against the owner's Spartacus version (docs/verification-log.md, Stage 8). This is the only file that imports @spartacus/*.
import { Injectable, inject } from '@angular/core';
import { AuthService, OCC_USER_ID_CURRENT, UserIdService } from '@spartacus/core';
import { Observable, map, switchMap, of, firstValueFrom } from 'rxjs';
import type { CcAuth } from '../ui/tokens';

@Injectable({ providedIn: 'root' })
export class SpartacusAuthAdapter implements CcAuth
{
  private readonly auth = inject(AuthService);
  private readonly userIds = inject(UserIdService);
  private latestToken?: string;

  /** Emits the user id while logged in and undefined after logout, so the store clears on logout. */
  readonly userId$: Observable<string | undefined> = this.auth.isUserLoggedIn().pipe(
    switchMap(loggedIn => (loggedIn ? this.userIds.getUserId() : of(undefined))),
    map(id => (id && id !== 'anonymous' ? id : undefined)));

  constructor()
  {
    this.auth.getToken?.().subscribe((t: { access_token?: string } | undefined) => (this.latestToken = t?.access_token));
  }

  token(): string | undefined { return this.latestToken; }
}

export { OCC_USER_ID_CURRENT, firstValueFrom };
