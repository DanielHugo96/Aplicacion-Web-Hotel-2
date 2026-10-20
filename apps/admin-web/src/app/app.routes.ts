import { Routes } from '@angular/router';
import { LoginComponent } from './features/login/login.component';
import { RegistroComponent } from './features/registro/registro.component';
import { ShellComponent } from './features/shell/shell.component';
import { HomeRedirectComponent } from './features/shell/home-redirect.component';
import { InicioComponent } from './features/inicio/inicio.component';
import { PerfilComponent } from './features/perfil/perfil.component';
import { clienteRoutes } from './features/cliente/cliente.routes';
import { PersonasListComponent } from './features/catalogo/personas/personas-list.component';
import { PersonaFormComponent } from './features/catalogo/personas/persona-form.component';
import { TiposPersonaComponent } from './features/catalogo/tipos-persona/tipos-persona.component';
import { SedesListComponent } from './features/catalogo/sedes/sedes-list.component';
import { SedeFormComponent } from './features/catalogo/sedes/sede-form.component';
import { PisosListComponent } from './features/catalogo/pisos/pisos-list.component';
import { PisoFormComponent } from './features/catalogo/pisos/piso-form.component';
import { CategoriasListComponent } from './features/catalogo/categorias/categorias-list.component';
import { CategoriaFormComponent } from './features/catalogo/categorias/categoria-form.component';
import { HabitacionesListComponent } from './features/catalogo/habitaciones/habitaciones-list.component';
import { HabitacionFormComponent } from './features/catalogo/habitaciones/habitacion-form.component';
import { authGuard } from './core/guards/auth.guard';
import { rolGuard } from './core/guards/rol.guard';

const panel = rolGuard('ADMIN', 'EMPLEADO');

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'registro', component: RegistroComponent },
  {
    path: 'app',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', component: HomeRedirectComponent, canActivate: [authGuard] },
      { path: 'inicio', component: InicioComponent, canActivate: [panel] },
      { path: 'perfil', component: PerfilComponent, canActivate: [authGuard] },

      { path: 'personas', component: PersonasListComponent, canActivate: [panel] },
      { path: 'personas/nuevo', component: PersonaFormComponent, canActivate: [panel] },
      { path: 'personas/:id/editar', component: PersonaFormComponent, canActivate: [panel] },

      { path: 'tipos-persona', component: TiposPersonaComponent, canActivate: [panel] },

      { path: 'sedes', component: SedesListComponent, canActivate: [panel] },
      { path: 'sedes/nuevo', component: SedeFormComponent, canActivate: [panel] },
      { path: 'sedes/:id/editar', component: SedeFormComponent, canActivate: [panel] },

      { path: 'pisos', component: PisosListComponent, canActivate: [panel] },
      { path: 'pisos/nuevo', component: PisoFormComponent, canActivate: [panel] },
      { path: 'pisos/:id/editar', component: PisoFormComponent, canActivate: [panel] },

      { path: 'categorias', component: CategoriasListComponent, canActivate: [panel] },
      { path: 'categorias/nuevo', component: CategoriaFormComponent, canActivate: [panel] },
      { path: 'categorias/:id/editar', component: CategoriaFormComponent, canActivate: [panel] },

      { path: 'habitaciones', component: HabitacionesListComponent, canActivate: [panel] },
      { path: 'habitaciones/nuevo', component: HabitacionFormComponent, canActivate: [panel] },
      { path: 'habitaciones/:id/editar', component: HabitacionFormComponent, canActivate: [panel] },
    ],
  },
  ...clienteRoutes,
  { path: '', redirectTo: '/login', pathMatch: 'full' },
];
