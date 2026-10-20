import { inject } from '@angular/core';
import { CanActivateFn, UrlTree, Router } from '@angular/router';
import { SessionService } from '../services/session.service';

function homeByRole(): string {
  const session = inject(SessionService);
  const rol = session.getRol();
  return rol === 'CLIENTE' ? '/mi-estadia' : '/app/inicio';
}

/**
 * rolGuard(['ADMIN'|'EMPLEADO'|'CLIENTE']): exige el rol dado.
 * Ruta sin permiso → redirige a la pantalla inicial del rol sin mostrar datos.
 * El backend valida la autorización de verdad; el guard solo oculta UX.
 */
export const rolGuard = (...roles: string[]): CanActivateFn => () => {
  const session = inject(SessionService);
  const router = inject(Router);
  const rol = session.getRol();
  if (!rol) {
    router.navigateByUrl('/login', { replaceUrl: true });
    return false;
  }
  if (roles.includes(rol)) {
    return true;
  }
  router.navigateByUrl(homeByRole(), { replaceUrl: true });
  return false;
};
