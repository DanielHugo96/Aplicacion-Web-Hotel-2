import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL, API_PATHS } from '../api.config';
import { ETagCacheService } from './etag-cache.service';
import { Persona } from './session.service';

export interface LoginRequest {
  correo: string;
  clave: string;
}

export interface RegistroRequest {
  tipoDocumento: string;
  documento: string;
  nombre: string;
  apellido: string;
  correo: string;
  clave: string;
}

export interface PerfilRequest {
  nombre: string;
  apellido: string;
  fotoUrl: string | null;
}

export interface PasswordChange {
  claveActual: string;
  claveNueva: string;
}

type Envelope<T> = {
  success: boolean;
  message: string;
  data: T;
  meta: { correlationId: string };
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly etags = inject(ETagCacheService);

  async login(req: LoginRequest): Promise<{ idPersona: number; nombre: string; apellido: string; correo: string; tipoPersona: string; token: string; expiresIn: number; sedes: number[] }> {
    const res = await firstValueFrom(
      this.http.post<Envelope<any>>(
        `${API_BASE_URL}${API_PATHS.authLogin}`,
        req,
        { headers: { Accept: 'application/json', 'Content-Type': 'application/json' } }
      )
    );
    return res.data;
  }

  async registro(req: RegistroRequest): Promise<Persona> {
    const res = await firstValueFrom(
      this.http.post<Envelope<Persona>>(
        `${API_BASE_URL}${API_PATHS.authRegistro}`,
        req,
        { headers: { Accept: 'application/json', 'Content-Type': 'application/json' } }
      )
    );
    return res.data;
  }

  async me(): Promise<{ persona: Persona; etag: string | null }> {
    const res = await firstValueFrom(
      this.http.get<Envelope<Persona>>(`${API_BASE_URL}${API_PATHS.authMe}`, {
        headers: { Accept: 'application/json' },
        observe: 'response',
      })
    );
    const etag = res.headers.get('ETag') ?? null;
    if (etag) this.etags.set(API_PATHS.authMe, etag);
    return { persona: res.body!.data, etag };
  }

  async updateMe(req: PerfilRequest): Promise<{ persona: Persona; etag: string | null }> {
    const etag = this.etags.get(API_PATHS.authMe);
    const headers: { [name: string]: string } = {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    };
    if (etag) headers['If-Match'] = etag;
    const res = await firstValueFrom(
      this.http.put<Envelope<Persona>>(
        `${API_BASE_URL}${API_PATHS.authMe}`,
        req,
        { headers, observe: 'response' }
      )
    );
    const newEtag = res.headers.get('ETag') ?? null;
    if (newEtag) this.etags.set(API_PATHS.authMe, newEtag);
    return { persona: res.body!.data, etag: newEtag };
  }

  async changePassword(req: PasswordChange): Promise<void> {
    await firstValueFrom(
      this.http.put<Envelope<null>>(
        `${API_BASE_URL}${API_PATHS.authPassword}`,
        req,
        { headers: { Accept: 'application/json', 'Content-Type': 'application/json' } }
      )
    );
  }
}
