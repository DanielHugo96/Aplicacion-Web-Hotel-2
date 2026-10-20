import { Component, Input } from '@angular/core';
import { PeTimestampPipe } from '../../core/pipes/timestamp.pipe';

/**
 * Banner persistente de operación 202 (§4 y §8 de `DESIGN.md`). No es éxito:
 * permanece visible con spinner hasta que el polling confirma el estado
 * terminal y muestra “Actualizado …” en cada reintento. `aria-live` anuncia el
 * avance sin robar el foco.
 */
@Component({
  selector: 'app-pending-banner',
  standalone: true,
  imports: [PeTimestampPipe],
  templateUrl: './pending-banner.component.html',
})
export class PendingBannerComponent {
  @Input() visible = false;
  @Input() detalle = 'La operación se está aplicando en la habitación.';
  @Input() actualizadoEn: Date | null = null;
}
