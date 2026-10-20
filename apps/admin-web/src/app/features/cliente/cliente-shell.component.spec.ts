import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { ClienteContextService } from '../../core/services/cliente-context.service';
import { ClienteService } from '../../core/services/cliente.service';
import { SessionService } from '../../core/services/session.service';
import { PERSONA_CLIENTE, tokenConSub } from './testing/cliente.fixtures';
import { ClienteShellComponent } from './cliente-shell.component';

describe('ClienteShellComponent', () => {
  let fixture: ComponentFixture<ClienteShellComponent>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [ClienteShellComponent],
      providers: [
        SessionService,
        ClienteService,
        ClienteContextService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    TestBed.inject(SessionService).setSession(
      {
        idPersona: 100,
        nombre: 'Cliente',
        apellido: 'Uno',
        correo: 'cliente.uno@hotel.test',
        tipoPersona: 'Cliente',
        token: tokenConSub(100),
        expiresIn: 900,
        sedes: [],
      },
      PERSONA_CLIENTE
    );
    fixture = TestBed.createComponent(ClienteShellComponent);
    fixture.detectChanges();
  });

  it('muestra el rol Cliente y las cinco secciones propias', () => {
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Área de cliente');
    expect(texto).toContain('Cliente');
    for (const seccion of ['Mi estadía', 'Catálogo', 'Carrito', 'Mis ventas', 'Mi perfil']) {
      expect(texto).toContain(seccion);
    }
    const enlaces = (fixture.nativeElement as HTMLElement).querySelectorAll('.cliente-nav a');
    expect(enlaces.length).toBe(5);
    expect((fixture.nativeElement as HTMLElement).querySelector('aside')).toBeNull();
  });

  it('no monta textos ni acciones administrativas', () => {
    const texto = fixture.nativeElement.textContent as string;
    for (const prohibido of [
      'Personas',
      'Roles',
      'Sedes',
      'Pisos',
      'Habitaciones',
      'Limpieza',
      'Recepción',
      'Productos',
      'Reportes',
      'Registrar check-in',
      'Registrar salida',
      'Anular',
      'Cobrar',
    ]) {
      expect(texto).not.toContain(prohibido);
    }
  });
});
