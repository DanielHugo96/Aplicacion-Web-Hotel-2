import { Routes } from '@angular/router';
import { authGuard } from '../../core/guards/auth.guard';
import { rolGuard } from '../../core/guards/rol.guard';

const cliente = rolGuard('CLIENTE');

/**
 * Rutas del área de cliente (rol C), las seis de `08-admin-web.md` §2, bajo un
 * shell propio y con `authGuard` + `rolGuard(['CLIENTE'])`. Las pantallas se
 * cargan de forma perezosa.
 */
export const clienteRoutes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./cliente-shell.component').then((m) => m.ClienteShellComponent),
    canActivate: [authGuard, cliente],
    children: [
      {
        path: 'mi-estadia',
        loadComponent: () =>
          import('./mi-estadia/mi-estadia.component').then((m) => m.MiEstadiaComponent),
      },
      {
        path: 'mi-estadia/catalogo',
        loadComponent: () =>
          import('./catalogo/catalogo-cliente.component').then(
            (m) => m.CatalogoClienteComponent
          ),
      },
      {
        path: 'mi-estadia/carrito',
        loadComponent: () =>
          import('./carrito/carrito.component').then((m) => m.CarritoComponent),
      },
      {
        path: 'mis-ventas',
        loadComponent: () =>
          import('./mis-ventas/mis-ventas.component').then((m) => m.MisVentasComponent),
      },
      {
        path: 'mis-ventas/:id',
        loadComponent: () =>
          import('./mis-ventas/venta-detalle.component').then(
            (m) => m.VentaDetalleComponent
          ),
      },
      {
        path: 'mi-perfil',
        loadComponent: () =>
          import('./mi-perfil/mi-perfil.component').then((m) => m.MiPerfilClienteComponent),
      },
    ],
  },
];
