import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { SessionService } from '../../core/services/session.service';
import { SedeContextService } from '../../core/services/sede-context.service';
import { ToastComponent } from '../../ui/toast/toast.component';

interface MenuItem {
  label: string;
  path: string;
  roles: ReadonlyArray<'ADMIN' | 'EMPLEADO' | 'CLIENTE'>;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet, ToastComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
})
export class ShellComponent {
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  readonly sedesContext = inject(SedeContextService);

  readonly rol = this.session.rol;
  readonly persona = this.session.persona;
  readonly collapsed = signal(false);

  readonly menu: MenuItem[] = [
    { label: 'Inicio', path: '/app/inicio', roles: ['ADMIN', 'EMPLEADO'] },
    { label: 'Personas', path: '/app/personas', roles: ['ADMIN', 'EMPLEADO'] },
    { label: 'Roles', path: '/app/tipos-persona', roles: ['ADMIN', 'EMPLEADO'] },
    { label: 'Sedes', path: '/app/sedes', roles: ['ADMIN', 'EMPLEADO'] },
    { label: 'Pisos', path: '/app/pisos', roles: ['ADMIN', 'EMPLEADO'] },
    { label: 'Categorías', path: '/app/categorias', roles: ['ADMIN', 'EMPLEADO'] },
    { label: 'Habitaciones', path: '/app/habitaciones', roles: ['ADMIN', 'EMPLEADO'] },
    { label: 'Limpieza', path: '/app/limpieza', roles: ['ADMIN', 'EMPLEADO'] },
    { label: 'Recepción', path: '/app/recepcion', roles: ['ADMIN', 'EMPLEADO'] },
    { label: 'Productos', path: '/app/productos', roles: ['ADMIN', 'EMPLEADO'] },
    { label: 'Ventas', path: '/app/ventas', roles: ['ADMIN', 'EMPLEADO'] },
    { label: 'Reportes', path: '/app/reportes', roles: ['ADMIN', 'EMPLEADO'] },
    { label: 'Mi perfil', path: '/app/perfil', roles: ['ADMIN', 'EMPLEADO', 'CLIENTE'] },
  ];

  constructor() {
    if (typeof window !== 'undefined' && window.matchMedia('(max-width: 1024px)').matches) {
      this.collapsed.set(true);
    }
    if (this.rol() !== 'CLIENTE') {
      void this.sedesContext.cargar();
    }
  }

  toggle(): void {
    this.collapsed.update((c) => !c);
  }

  cerrarMenuSiMovil(): void {
    if (typeof window !== 'undefined' && window.matchMedia('(max-width: 1024px)').matches) {
      this.collapsed.set(true);
    }
  }

  esActivo(path: string): boolean {
    const url = this.router.url;
    return url === path || url.startsWith(path + '/');
  }

  estaVisible(item: MenuItem): boolean {
    const r = this.rol();
    return r ? item.roles.includes(r) : false;
  }

  cambiarSede(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.sedesContext.seleccionar(value ? Number(value) : null);
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
