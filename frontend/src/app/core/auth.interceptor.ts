import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { Session } from './session.service';

/** A 401 on any API call (expired/revoked session) sends the user to the login page. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const session = inject(Session);
  return next(req).pipe(catchError((err: unknown) => {
    const isAuthCall = req.url.includes('/auth/login') || req.url.includes('/auth/me') || req.url.includes('/setup');
    if (err instanceof HttpErrorResponse && err.status === 401 && !isAuthCall) {
      session.me.set(null);
      void router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
    }
    return throwError(() => err);
  }));
};
