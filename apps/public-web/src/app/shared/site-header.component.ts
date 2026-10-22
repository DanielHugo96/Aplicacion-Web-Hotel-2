import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { PANEL_URL } from '../core/config';

@Component({
  selector: 'app-site-header',
  imports: [RouterLink, RouterLinkActive],
  template: `
    <header class="cabecera">
      <div class="contenedor cabecera__interior">
        <a class="marca" routerLink="/" aria-label="Casa Sillar, inicio">
          <span class="marca__nombre">Casa</span>
          <span class="marca__apellido">Sillar</span>
        </a>

        <nav class="navegacion" aria-label="Navegación principal">
          <a
            routerLink="/sedes"
            routerLinkActive="activo"
            [routerLinkActiveOptions]="{ exact: false }"
          >
            Sedes
          </a>
          <a routerLink="/contacto" routerLinkActive="activo">Contacto</a>
          <a routerLink="/privacidad" routerLinkActive="activo">Privacidad</a>
        </nav>

        <a class="acceso" [href]="panelUrl">Acceso al panel</a>
      </div>
    </header>
  `,
  styleUrl: './site-header.component.css',
})
export class SiteHeaderComponent {
  protected readonly panelUrl = inject(PANEL_URL);
}
