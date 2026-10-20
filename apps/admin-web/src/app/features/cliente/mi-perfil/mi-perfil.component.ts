import { Component, OnInit, inject, signal } from '@angular/core';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { SessionService } from '../../../core/services/session.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PerfilFormComponent } from '../../perfil/perfil-form.component';

/**
 * `/mi-perfil` del área de cliente: `GET /api/auth/me` para los datos actuales
 * y el formulario compartido, que envía solo `nombre`, `apellido` y `fotoUrl`
 * (`PUT /api/auth/me` con `If-Match` e `Idempotency-Key` del interceptor).
 */
@Component({
  selector: 'app-mi-perfil-cliente',
  standalone: true,
  imports: [ErrorBannerComponent, PerfilFormComponent],
  templateUrl: './mi-perfil.component.html',
  styleUrl: './mi-perfil.component.css',
})
export class MiPerfilClienteComponent implements OnInit {
  private readonly session = inject(SessionService);

  readonly persona = this.session.persona;
  readonly cargando = signal(true);
  readonly error = signal<ApiUiError | null>(null);

  async ngOnInit(): Promise<void> {
    await this.cargar();
  }

  async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      await this.session.me();
    } catch (err) {
      this.error.set(parseApiError(err));
    } finally {
      this.cargando.set(false);
    }
  }
}
