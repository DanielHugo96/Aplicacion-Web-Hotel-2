import { Routes } from '@angular/router';
import { rolGuard } from '../../core/guards/rol.guard';

const panelRecepcion = rolGuard('ADMIN', 'EMPLEADO');

/**
 * Rutas del módulo de recepción bajo el shell del panel. Todas son lazy y
 * reutilizan el `authGuard` del padre y el `rolGuard` de A/E: C nunca entra.
 * `/app/recepcion/check-in` se declara antes de `/app/recepcion/:id` para que
 * el path literal no se interprete como identificador, y
 * `/app/recepcion/:id/cierre` (cierre de estadía, F4) antes de `/app/recepcion/:id`.
 */
export const recepcionRoutes: Routes = [
  {
    path: 'recepcion',
    loadComponent: () =>
      import('./estadias/estadias-list.component').then((m) => m.EstadiasListComponent),
    canActivate: [panelRecepcion],
  },
  {
    path: 'recepcion/check-in',
    loadComponent: () =>
      import('./check-in/check-in.component').then((m) => m.CheckInComponent),
    canActivate: [panelRecepcion],
  },
  {
    path: 'recepcion/:id/cierre',
    loadComponent: () =>
      import('./cierre/cierre-form.component').then((m) => m.CierreFormComponent),
    canActivate: [panelRecepcion],
  },
  {
    path: 'recepcion/:id',
    loadComponent: () =>
      import('./estadias/estadia-detalle.component').then((m) => m.EstadiaDetalleComponent),
    canActivate: [panelRecepcion],
  },
  {
    path: 'limpieza',
    loadComponent: () =>
      import('./limpieza/limpieza.component').then((m) => m.LimpiezaComponent),
    canActivate: [panelRecepcion],
  },
];
