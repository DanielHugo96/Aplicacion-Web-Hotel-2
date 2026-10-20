import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ClienteContextService } from '../../core/services/cliente-context.service';
import { SessionService } from '../../core/services/session.service';
import { ToastComponent } from '../../ui/toast/toast.component';

interface ClienteMenuItem {
  label: string;
  path: string;
  exact: boolean;
}

/**
 * Shell propio del área de cliente (rol C): sin sidebar oscura, sin selector
 * de sede y sin ningún enlace o acción administrativa. El guard del backend
 * sigue siendo la autoridad; esto es solo UX.
 */
@Component({
  selector: 'app-cliente-shell',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, ToastComponent],
  templateUrl: './cliente-shell.component.html',
  styleUrl: './cliente-shell.component.css',
})
export class ClienteShellComponent {
  private readonly session = inject(SessionService);
  private readonly contexto = inject(ClienteContextService);

  readonly persona = this.session.persona;
  readonly rol = this.session.rol;

  readonly menu: ClienteMenuItem[] = [
    { label: 'Mi estadía', path: '/mi-estadia', exact: true },
    { label: 'Catálogo', path: '/mi-estadia/catalogo', exact: false },
    { label: 'Carrito', path: '/mi-estadia/carrito', exact: false },
    { label: 'Mis ventas', path: '/mis-ventas', exact: false },
    { label: 'Mi perfil', path: '/mi-perfil', exact: false },
  ];

  logout(): void {
    this.contexto.limpiar();
    this.session.logout();
  }
}
