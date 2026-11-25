import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { Api } from './api/api.service';
import { Session } from './session.service';

/** Redirects to /setup on a fresh install, otherwise requires a session. */
export const authGuard: CanActivateFn = async (_route, state) => {
  const session = inject(Session);
  const router = inject(Router);
  const api = inject(Api);
  if (session.me()) return true;
  if (await session.load()) return true;
  try {
    const status = await firstValueFrom(api.setupStatus());
    if (status.pending) return router.parseUrl('/setup');
  } catch { /* backend unreachable: show login */ }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

export const setupGuard: CanActivateFn = async () => {
  const api = inject(Api);
  const router = inject(Router);
  try {
    const status = await firstValueFrom(api.setupStatus());
    return status.pending ? true : router.parseUrl('/login');
  } catch {
    return true;
  }
};
