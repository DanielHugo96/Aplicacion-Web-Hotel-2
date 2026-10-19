import { Component, inject, signal } from '@angular/core';
import { SessionService } from '../../core/services/session.service';
import { PerfilFormComponent } from '../perfil/perfil-form.component';

@Component({
  selector: 'app-mi-perfil',
  standalone: true,
  imports: [PerfilFormComponent],
  templateUrl: './mi-perfil.component.html',
  styleUrl: './mi-perfil.component.css',
})
export class MiPerfilComponent {
  private readonly session = inject(SessionService);

  readonly persona = this.session.persona;
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly titulo = 'Mi perfil';

  constructor() {
    this.session.me().then(
      () => (this.loading.set(false)),
      (err: any) => {
        this.loading.set(false);
        if (err?.status === 401) {
          this.session.logout();
        } else {
          this.error.set('No se pudo cargar el perfil. Reintenta.');
        }
      }
    );
  }

  onPasswordSaved(): void {
    this.error.set(null);
  }
}
