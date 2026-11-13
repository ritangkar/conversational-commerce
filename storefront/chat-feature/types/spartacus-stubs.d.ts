// Local type stubs so the library can be type-checked without Spartacus. They are NOT the real Spartacus API (UNVERIFIED).
declare module '@spartacus/core' {
  import { Observable } from 'rxjs';
  export const OCC_USER_ID_CURRENT: string;
  export class AuthService { isUserLoggedIn(): Observable<boolean>; getToken?(): Observable<{ access_token?: string } | undefined>; }
  export class UserIdService { getUserId(): Observable<string>; }
}
declare module '@spartacus/storefront' { export class AuthGuard {} }
