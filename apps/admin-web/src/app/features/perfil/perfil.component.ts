import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { SessionService } from '../../core/services/session.service';
import { PerfilFormComponent } from './perfil-form.component';
import { PasswordChangeComponent } from '../password-change/password-change.component';

@Component({
  selector: 'app-perfil',
  standalone: true,
  imports: [PerfilFormComponent],
  templateUrl: './perfil.component.html',
  styleUrl: './perfil.component.css',
})
export class PerfilComponent {
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

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
