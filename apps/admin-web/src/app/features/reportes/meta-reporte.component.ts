import { Component, Input } from '@angular/core';
import { PeTimestampPipe } from '../../core/pipes/timestamp.pipe';
import { MetaReporte } from '../../core/models/reportes.models';

/**
 * Aviso de consistencia eventual de los reportes (`reporting/api.md` § REST
 * legacy): banner ámbar cuando la meta trae `degraded: true`, con `updatedAt` y
 * `bootstrapComplete` siempre visibles. No bloquea la vista y nunca promete
 * frescura absoluta: «Datos eventualmente consistentes» es el texto canónico
 * de `DESIGN-admin-web.md` (mensajes de vacío y avisos).
 */
@Component({
  selector: 'app-meta-reporte',
  standalone: true,
  imports: [PeTimestampPipe],
  template: `
    @if (meta) {
      <div class="meta-reporte">
        @if (meta.degraded) {
          <div class="banner banner-aviso" role="status" aria-live="polite">
            <div class="banner-body">
              <strong>Datos eventualmente consistentes</strong>
              <span class="text-small">
                Hay errores de proyección conocidos; las cifras pueden cambiar cuando las
                proyecciones se pongan al día.
              </span>
              <span class="text-micro">
                Actualizado: {{ meta.updatedAt | peTimestamp }}
              </span>
            </div>
          </div>
        }
        <p class="meta-linea text-micro muted">
          Consistencia {{ meta.consistency }} · Actualizado:
          <span class="monospace">{{ meta.updatedAt | peTimestamp }}</span>
          · Proyecciones listas:
          <span class="monospace">{{ meta.bootstrapComplete ? 'sí' : 'no' }}</span>
        </p>
      </div>
    }
  `,
  styles: [
    `
      .meta-reporte {
        margin-top: var(--space-4);
      }
      .meta-linea {
        margin: 0;
      }
    `,
  ],
})
export class MetaReporteComponent {
  @Input() meta: MetaReporte | null = null;
}
