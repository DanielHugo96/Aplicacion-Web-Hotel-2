import { DatePipe } from '@angular/common';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-site-footer',
  imports: [RouterLink, DatePipe],
  template: `
    <footer class="pie">
      <div class="contenedor pie__interior">
        <div class="pie__marca">
          <p class="pie__nombre">Casa Sillar</p>
          <p class="pie__aviso">
            Cadena hotelera de presentación. Las sedes, categorías e importes
            provienen del catálogo público y no constituyen una reserva; la
            disponibilidad se consulta con recepción.
          </p>
          <p class="pie__aviso">
            Los datos de esta presentación son ficticios y no corresponden a una
            empresa real en operación.
          </p>
          <p class="pie__fecha">Página generada el {{ hoy | date: 'dd/MM/yyyy HH:mm' }}</p>
        </div>
        <nav class="pie__enlaces" aria-label="Enlaces del pie">
          <a routerLink="/contacto">Contacto</a>
          <a routerLink="/privacidad">Privacidad</a>
          <a routerLink="/sedes">Sedes</a>
        </nav>
      </div>
    </footer>
  `,
  styleUrl: './site-footer.component.css',
})
export class SiteFooterComponent {
  protected readonly hoy = new Date();
}
