import { Routes } from '@angular/router';

/** Rutas Angular de la landing (no son endpoints backend). */
export const routes: Routes = [
  {
    path: '',
    title: 'Casa Sillar | Hoteles en Lima y Arequipa',
    loadComponent: () =>
      import('./pages/inicio.page').then((m) => m.InicioPage),
  },
  {
    path: 'sedes',
    title: 'Sedes | Casa Sillar',
    loadComponent: () => import('./pages/sedes.page').then((m) => m.SedesPage),
  },
  {
    path: 'sedes/:slug',
    title: 'Sede | Casa Sillar',
    loadComponent: () =>
      import('./pages/sede-detalle.page').then((m) => m.SedeDetallePage),
  },
  {
    path: 'sedes/:slug/habitaciones/:idCategoria',
    title: 'Tipo de habitación | Casa Sillar',
    loadComponent: () =>
      import('./pages/categoria-detalle.page').then((m) => m.CategoriaDetallePage),
  },
  {
    path: 'contacto',
    title: 'Contacto | Casa Sillar',
    loadComponent: () =>
      import('./pages/contacto.page').then((m) => m.ContactoPage),
  },
  {
    path: 'privacidad',
    title: 'Privacidad | Casa Sillar',
    loadComponent: () =>
      import('./pages/privacidad.page').then((m) => m.PrivacidadPage),
  },
  { path: '**', redirectTo: '' },
];
