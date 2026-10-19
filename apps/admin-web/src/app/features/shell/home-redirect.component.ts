import { Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { SessionService } from '../../core/services/session.service';

/**
 * Redirección al entrar según rol: ADMIN y EMPLEADO van a /app/inicio;
 * CLIENTE va a su área propia (/mi-perfil). Sin datos de por medio: solo UX.
 */
@Component({
  selector: 'app-home-redirect',
  standalone: true,
  template: '',
})
export class HomeRedirectComponent implements OnInit {
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    this.router.navigateByUrl(this.session.homeUrl(), { replaceUrl: true });
  }
}
