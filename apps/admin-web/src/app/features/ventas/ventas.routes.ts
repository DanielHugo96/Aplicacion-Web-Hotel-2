import { Routes } from '@angular/router';
import { rolGuard } from '../../core/guards/rol.guard';

const panelVentas = rolGuard('ADMIN', 'EMPLEADO');

/**
 * Rutas del módulo de productos y ventas bajo el shell del panel. Todas son
 * lazy y reutilizan el `authGuard` del padre y el `rolGuard` de A/E: C nunca
 * entra. `/app/ventas/nueva` se declara antes de `/app/ventas/:id` para que el
 * path literal no se interprete como identificador; lo mismo con las rutas
 * literales de productos.
 */
export const ventasRoutes: Routes = [
  {
    path: 'productos',
    loadComponent: () =>
      import('./productos/productos-list.component').then((m) => m.ProductosListComponent),
    canActivate: [panelVentas],
  },
  {
    path: 'productos/nuevo',
    loadComponent: () =>
      import('./productos/producto-form.component').then((m) => m.ProductoFormComponent),
    canActivate: [panelVentas],
  },
  {
    path: 'productos/:id/editar',
    loadComponent: () =>
      import('./productos/producto-form.component').then((m) => m.ProductoFormComponent),
    canActivate: [panelVentas],
  },
  {
    path: 'productos/:id/movimientos',
    loadComponent: () =>
      import('./productos/movimientos.component').then((m) => m.MovimientosComponent),
    canActivate: [panelVentas],
  },
  {
    path: 'ventas',
    loadComponent: () =>
      import('./ventas/ventas-list.component').then((m) => m.VentasListComponent),
    canActivate: [panelVentas],
  },
  {
    path: 'ventas/nueva',
    loadComponent: () =>
      import('./ventas/venta-nueva.component').then((m) => m.VentaNuevaComponent),
    canActivate: [panelVentas],
  },
  {
    path: 'ventas/:id',
    loadComponent: () =>
      import('./ventas/venta-detalle.component').then((m) => m.VentaDetalleComponent),
    canActivate: [panelVentas],
  },
];
