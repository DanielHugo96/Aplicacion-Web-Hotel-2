import { inject, computed, signal, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { API_BASE_URL, API_PATHS } from '../api.config';

export type Rol = 'ADMIN' | 'EMPLEADO' | 'CLIENTE';

export interface Persona {
  idPersona: number;
  tipoDocumento: string;
  documento: string;
  nombre: string;
  apellido: string;
  correo: string;
  fotoUrl: string | null;
  idTipoPersona: number;
  tipoPersona: string;
  sedes: number[];
  estado: boolean;
  fechaCreacion: string;
  version: number;
}

export interface AuthData {
  idPersona: number;
  nombre: string;
  apellido: string;
  correo: string;
  tipoPersona: string;
  token: string;
  expiresIn: number;
  sedes: number[];
}

export interface Session {
  auth: AuthData;
  persona: Persona;
}

function tipoPersonaToRol(tipoPersona: string): Rol {
  switch (tipoPersona) {
    case 'Administrador':
      return 'ADMIN';
    case 'Empleado':
      return 'EMPLEADO';
    case 'Cliente':
    default:
      return 'CLIENTE';
  }
}

function homeByRole(rol: Rol): string {
  return rol === 'CLIENTE' ? '/mi-perfil' : '/app/inicio';
}

/**
 * Almacén de sesión: JWT solo en memoria (sin localStorage ni sessionStorage).
 * Logout borra el token. Rol y sede se leen del JWT solo como UX: el backend
 * revalida en cada petición.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(null);

  readonly token = computed(() => this.session()?.auth.token ?? null);
  readonly persona = computed(() => this.session()?.persona ?? null);
  readonly rol = computed(() => {
    const s = this.session();
    return s ? tipoPersonaToRol(s.auth.tipoPersona) : null;
  });
  readonly tipoPersona = computed(() => this.session()?.auth.tipoPersona ?? null);
  readonly sedes = computed(() => this.session()?.auth.sedes ?? []);
  readonly isAuthenticated = computed(() => this.session() !== null);

  setSession(auth: AuthData, persona: Persona): void {
    this.session.set({ auth, persona });
  }

  getToken(): string | null {
    return this.token();
  }

  getRol(): Rol | null {
    return this.rol();
  }

  hasRole(rol: Rol): boolean {
    return this.rol() === rol;
  }

  homeUrl(): string {
    const r = this.rol();
    return r ? homeByRole(r) : '/login';
  }

  async me(): Promise<Persona> {
    const persona = await this.http
      .get<{ success: boolean; message: string; data: Persona; meta: { correlationId: string } }>(
        `${API_BASE_URL}${API_PATHS.authMe}`,
        { headers: { Accept: 'application/json' } }
      )
      .toPromise();
    if (!persona?.data) {
      throw new Error('Sin datos de sesión');
    }
    const s = this.session();
    if (s) {
      this.session.set({ ...s, persona: persona.data });
    }
    return persona.data;
  }

  logout(): void {
    this.session.set(null);
    this.router.navigateByUrl('/login');
  }
}
