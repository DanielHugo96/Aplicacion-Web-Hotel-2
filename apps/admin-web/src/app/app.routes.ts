import { Routes } from '@angular/router';
import { LoginComponent } from './features/login/login.component';
import { RegistroComponent } from './features/registro/registro.component';
import { ShellComponent } from './features/shell/shell.component';
import { HomeRedirectComponent } from './features/shell/home-redirect.component';
import { InicioComponent } from './features/inicio/inicio.component';
import { PerfilComponent } from './features/perfil/perfil.component';
import { MiPerfilComponent } from './features/mi-perfil/mi-perfil.component';
import { authGuard } from './core/guards/auth.guard';
import { rolGuard } from './core/guards/rol.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'registro', component: RegistroComponent },
  {
    path: 'app',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', component: HomeRedirectComponent, canActivate: [authGuard] },
      { path: 'inicio', component: InicioComponent, canActivate: [rolGuard('ADMIN', 'EMPLEADO')] },
      { path: 'perfil', component: PerfilComponent, canActivate: [authGuard] },
    ],
  },
  { path: 'mi-perfil', component: MiPerfilComponent, canActivate: [rolGuard('CLIENTE')] },
  { path: '', redirectTo: '/login', pathMatch: 'full' },
];
