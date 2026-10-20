import { Routes } from '@angular/router';

/** Rutas Angular de la landing (no son endpoints backend). */
export const routes: Routes = [
  {
    path: '',
    title: 'Hoteles de demostración en Lima y Arequipa',
    loadComponent: () =>
      import('./pages/inicio.page').then((m) => m.InicioPage),
  },
  {
    path: 'sedes',
    title: 'Sedes | Hoteles de demostración',
    loadComponent: () => import('./pages/sedes.page').then((m) => m.SedesPage),
  },
  {
    path: 'sedes/:slug',
    title: 'Sede | Hoteles de demostración',
    loadComponent: () =>
      import('./pages/sede-detalle.page').then((m) => m.SedeDetallePage),
  },
  {
    path: 'sedes/:slug/habitaciones/:idCategoria',
    title: 'Tipo de habitación | Hoteles de demostración',
    loadComponent: () =>
      import('./pages/categoria-detalle.page').then((m) => m.CategoriaDetallePage),
  },
  {
    path: 'contacto',
    title: 'Contacto | Hoteles de demostración',
    loadComponent: () =>
      import('./pages/contacto.page').then((m) => m.ContactoPage),
  },
  {
    path: 'privacidad',
    title: 'Privacidad | Hoteles de demostración',
    loadComponent: () =>
      import('./pages/privacidad.page').then((m) => m.PrivacidadPage),
  },
  { path: '**', redirectTo: '' },
];
