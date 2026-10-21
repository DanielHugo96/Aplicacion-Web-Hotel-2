import { Routes } from '@angular/router';
import { rolGuard } from '../../core/guards/rol.guard';

const panelReportes = rolGuard('ADMIN', 'EMPLEADO');

/**
 * Rutas de reportes bajo el shell del panel. Reutiliza el `authGuard` del
 * padre y el `rolGuard` de A/E: CLIENTE no tiene `/app/inicio` ni
 * `/app/reportes` ([`08-admin-web.md` §2](../especificacion/08-admin-web.md)).
 */
export const reportesRoutes: Routes = [
  {
    path: 'inicio',
    loadComponent: () =>
      import('../inicio/inicio.component').then((m) => m.InicioComponent),
    canActivate: [panelReportes],
  },
  {
    path: 'reportes',
    loadComponent: () =>
      import('./reportes.component').then((m) => m.ReportesComponent),
    canActivate: [panelReportes],
  },
];
