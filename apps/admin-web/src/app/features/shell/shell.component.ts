import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SessionService } from '../../core/services/session.service';

interface MenuItem {
  label: string;
  path: string;
  roles: ReadonlyArray<'ADMIN' | 'EMPLEADO' | 'CLIENTE'>;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
})
export class ShellComponent {
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  readonly rol = this.session.rol;
  readonly persona = this.session.persona;
  readonly collapsed = signal(false);

  readonly menu: MenuItem[] = [
    { label: 'Inicio', path: '/app/inicio', roles: ['ADMIN', 'EMPLEADO'] },
    { label: 'Mi perfil', path: '/app/perfil', roles: ['ADMIN', 'EMPLEADO', 'CLIENTE'] },
  ];

  toggle(): void {
    this.collapsed.update((c) => !c);
  }

  esActivo(path: string): boolean {
    const url = this.router.url;
    return url === path || url.startsWith(path + '/');
  }

  estaVisible(item: MenuItem): boolean {
    const r = this.rol();
    return r ? item.roles.includes(r) : false;
  }

  logout(): void {
    this.session.logout();
  }

  homeUrl(): string {
    return this.session.homeUrl();
  }

  irPerfil(): void {
    this.router.navigateByUrl(this.rol() === 'CLIENTE' ? '/mi-perfil' : '/app/perfil', { replaceUrl: true });
  }
}
