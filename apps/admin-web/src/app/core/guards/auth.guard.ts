import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionService } from '../services/session.service';

/**
 * authGuard: exige sesión (JWT en memoria). Sin token redirige a /login.
 * El backend es la autoridad final; el guard solo es UX.
 */
export const authGuard: CanActivateFn = () => {
  const session = inject(SessionService);
  const router = inject(Router);
  if (session.isAuthenticated()) {
    return true;
  }
  router.navigateByUrl('/login', { replaceUrl: true });
  return false;
};
