import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL, API_PATHS } from '../api.config';
import {
  ActualizarSalidaInput,
  AvanceOperacionHabitacion,
  CheckInInput,
  EstadoLimpieza,
  Limpieza,
  LimpiezaResultado,
  OperacionHabitacion,
  Recepcion,
  ResultadoOperacionHabitacion,
} from '../models/recepcion.models';
import {
  ApiMetaPage,
  Envelope,
  ETagged,
  Habitacion,
  Paged,
  Persona,
} from '../models/catalogo.models';
import { resolverLocation } from './cliente.service';

const RETRY_AFTER_POR_DEFECTO_MS = 2000;

function unwrapList<T>(data: unknown): T[] {
  if (Array.isArray(data)) return data as T[];
  if (data && typeof data === 'object') {
    const primera = Object.values(data as Record<string, unknown>).find((v) => Array.isArray(v));
    if (Array.isArray(primera)) return primera as T[];
  }
  return [];
}

/** El contrato exige `If-Match` con la versión V entre comillas (`"3"`). */
export function etagDeVersion(version: number): string {
  return `"${version}"`;
}

function parseRetryAfter(value: string | null): number | null {
  if (!value) return null;
  const segundos = Number(value);
  if (Number.isFinite(segundos)) return Math.max(0, segundos * 1000);
  const fecha = Date.parse(value);
  return Number.isFinite(fecha) ? Math.max(fecha - Date.now(), 0) : null;
}

function esperar(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, Math.max(0, ms)));
}

/**
 * Cliente HTTP del módulo de recepción. Todo sale por el Gateway
 * (`API_BASE_URL`); el envelope, `Idempotency-Key`, el `If-Match` cacheado y el
 * manejo de 401 los aporta el interceptor de `front-admin-web-base`.
 *
 * Las operaciones administrativas de habitación pueden responder 202; el
 * interceptor deja pasar ese 202 sin sondeos para que la pantalla muestre el
 * banner persistente y el avance de cada reintento. El ETag de las ediciones
 * se envía explícito porque el recurso se lee y se muta en URLs distintas.
 */
@Injectable({ providedIn: 'root' })
export class RecepcionService {
  private readonly http = inject(HttpClient);

  // --------------------------------------------------------------- estadías

  async listarEstadias(
    idHotel: number,
    page = 0,
    size = 20,
    estadoEstadia?: string | null
  ): Promise<Paged<Recepcion>> {
    let params = new HttpParams().set('page', page).set('size', size).set('idHotel', idHotel);
    if (estadoEstadia) params = params.set('estadoEstadia', estadoEstadia);
    return this.listar<Recepcion>(API_PATHS.recepcionListar, params);
  }

  async buscarEstadia(id: number): Promise<ETagged<Recepcion>> {
    const res = await firstValueFrom(
      this.http.get<Envelope<Recepcion>>(`${API_BASE_URL}${API_PATHS.recepcionBuscar(id)}`, {
        headers: { Accept: 'application/json' },
        observe: 'response',
      })
    );
    return {
      data: res.body!.data,
      etag: res.headers.get('ETag'),
      correlationId: res.body!.meta.correlationId,
    };
  }

  async actualizarSalida(
    id: number,
    body: ActualizarSalidaInput,
    ifMatch: string
  ): Promise<ETagged<Recepcion>> {
    const res = await firstValueFrom(
      this.http.put<Envelope<Recepcion>>(
        `${API_BASE_URL}${API_PATHS.recepcionActualizar(id)}`,
        body,
        {
          headers: { Accept: 'application/json', 'If-Match': ifMatch },
          observe: 'response',
        }
      )
    );
    return {
      data: res.body!.data,
      etag: res.headers.get('ETag'),
      correlationId: res.body!.meta.correlationId,
    };
  }

  // --------------------------------------------------------------- check-in

  /**
   * Búsqueda exacta por documento. Solo devuelve personas activas con rol
   * Cliente: el buscador del check-in no admite personal ni cuentas inactivas.
   */
  async buscarClientePorDocumento(documento: string): Promise<Persona[]> {
    const params = new HttpParams()
      .set('page', 0)
      .set('size', 20)
      .set('documento', documento.trim());
    const res = await firstValueFrom(
      this.http.get<Envelope<Persona[], ApiMetaPage>>(
        `${API_BASE_URL}${API_PATHS.personaListar}`,
        { headers: { Accept: 'application/json' }, params }
      )
    );
    return unwrapList<Persona>(res.data).filter(
      (p) => p.tipoPersona === 'Cliente' && p.estado === true
    );
  }

  /** Habitaciones activas de la sede, con estado físico y ocupación. */
  async listarHabitacionesDeSede(idHotel: number): Promise<Habitacion[]> {
    const params = new HttpParams()
      .set('page', 0)
      .set('size', 100)
      .set('idHotel', idHotel);
    const res = await firstValueFrom(
      this.http.get<Envelope<Habitacion[], ApiMetaPage>>(
        `${API_BASE_URL}${API_PATHS.habitacionListar}`,
        { headers: { Accept: 'application/json' }, params }
      )
    );
    return unwrapList<Habitacion>(res.data).filter((h) => h.estado === true);
  }

  async registrarCheckIn(body: CheckInInput): Promise<Recepcion> {
    const res = await firstValueFrom(
      this.http.post<Envelope<Recepcion>>(
        `${API_BASE_URL}${API_PATHS.recepcionRegistrar}`,
        body,
        { headers: { Accept: 'application/json' } }
      )
    );
    return res.data;
  }

  // --------------------------------------------------------------- limpieza

  async listarLimpiezas(
    idHotel: number,
    estado: EstadoLimpieza | null,
    page = 0,
    size = 20
  ): Promise<Paged<Limpieza>> {
    let params = new HttpParams().set('page', page).set('size', size).set('idHotel', idHotel);
    if (estado) params = params.set('estado', estado);
    return this.listar<Limpieza>(API_PATHS.limpiezas, params);
  }

  /**
   * `PUT /api/habitacion/{id}/limpieza-completada`: el `id` es la habitación,
   * el `cleaningCycleId` viene fijo del listado y el `If-Match` es la versión
   * de la tarea (`Limpieza.version`), no la de la habitación.
   */
  async completarLimpieza(
    idHabitacion: number,
    cleaningCycleId: string,
    version: number
  ): Promise<LimpiezaResultado> {
    const res = await firstValueFrom(
      this.http.put<Envelope<LimpiezaResultado>>(
        `${API_BASE_URL}${API_PATHS.limpiezaCompletada(idHabitacion)}`,
        { cleaningCycleId },
        {
          headers: { Accept: 'application/json', 'If-Match': etagDeVersion(version) },
          observe: 'response',
        }
      )
    );
    return res.body!.data;
  }

  // ------------------------------------------------- operaciones habitación

  async iniciarMantenimiento(
    idHabitacion: number,
    motivo: string,
    version: number
  ): Promise<ResultadoOperacionHabitacion> {
    return this.operacionConPosible202<Habitacion>(
      'POST',
      API_PATHS.habitacionMantenimiento(idHabitacion),
      { motivo },
      etagDeVersion(version)
    );
  }

  async darDeBaja(
    idHabitacion: number,
    version: number
  ): Promise<ResultadoOperacionHabitacion> {
    return this.operacionConPosible202<Habitacion>(
      'DELETE',
      API_PATHS.habitacionEliminar(idHabitacion),
      null,
      etagDeVersion(version)
    );
  }

  async completarMantenimiento(
    idHabitacion: number,
    blockId: string,
    version: number
  ): Promise<ETagged<Habitacion>> {
    return this.putHabitacion(
      API_PATHS.habitacionMantenimientoCompletado(idHabitacion),
      { blockId },
      version
    );
  }

  async reactivar(idHabitacion: number, blockId: string, version: number): Promise<ETagged<Habitacion>> {
    return this.putHabitacion(API_PATHS.habitacionReactivar(idHabitacion), { blockId }, version);
  }

  async consultarOperacion(blockId: string): Promise<OperacionHabitacion> {
    const res = await firstValueFrom(
      this.http.get<Envelope<OperacionHabitacion>>(
        `${API_BASE_URL}${API_PATHS.habitacionOperacion(blockId)}`,
        { headers: { Accept: 'application/json' } }
      )
    );
    return res.data;
  }

  /**
   * Sondea `Location` respetando `Retry-After` hasta APLICADA, LIBERADA o
   * RECHAZADA. Invoca `onAvance` en cada reintento para que la pantalla muestre
   * “Actualizado …”. Devuelve `null` si se cancela.
   */
  async seguirOperacion(
    location: string,
    retryAfterMs: number,
    onAvance: (avance: AvanceOperacionHabitacion) => void,
    signal?: AbortSignal
  ): Promise<AvanceOperacionHabitacion | null> {
    let espera = retryAfterMs > 0 ? retryAfterMs : RETRY_AFTER_POR_DEFECTO_MS;
    let ultimo: AvanceOperacionHabitacion | null = null;

    while (!signal?.aborted) {
      await esperar(espera);
      if (signal?.aborted) return null;

      const res = await firstValueFrom(
        this.http.get<Envelope<OperacionHabitacion>>(location, {
          headers: { Accept: 'application/json' },
          observe: 'response',
        })
      );
      const operacion = res.body?.data;
      if (!operacion) {
        throw new Error('La operación pendiente no devolvió datos');
      }
      const retry = parseRetryAfter(res.headers.get('Retry-After'));
      if (retry !== null) espera = retry > 0 ? retry : RETRY_AFTER_POR_DEFECTO_MS;

      ultimo = { operacion, actualizadoEn: new Date() };
      onAvance(ultimo);

      if (['APLICADA', 'LIBERADA', 'RECHAZADA'].includes(operacion.estado)) {
        return ultimo;
      }
    }
    return ultimo;
  }

  // --------------------------------------------------------------- helpers

  private async operacionConPosible202<T>(
    method: 'POST' | 'DELETE',
    path: string,
    body: unknown,
    ifMatch: string
  ): Promise<ResultadoOperacionHabitacion> {
    const url = `${API_BASE_URL}${path}`;
    const headers = { Accept: 'application/json', 'If-Match': ifMatch };
    const res = await firstValueFrom(
      this.http.request<Envelope<T> | Envelope<import('../models/recepcion.models').OperationRef>>(
        method,
        url,
        { body: body ?? undefined, headers, observe: 'response' }
      )
    );
    if (res.status === 202) {
      const location = res.headers.get('Location');
      if (!location) {
        throw new Error('La operación pendiente no trae Location');
      }
      return {
        kind: 'pendiente',
        operacion: (res.body as Envelope<import('../models/recepcion.models').OperationRef>).data,
        location: resolverLocation(location),
        retryAfterMs: parseRetryAfter(res.headers.get('Retry-After')) ?? RETRY_AFTER_POR_DEFECTO_MS,
      };
    }
    return {
      kind: 'confirmada',
      habitacion: (res.body as Envelope<T>).data as unknown as Habitacion,
    };
  }

  private async putHabitacion(
    path: string,
    body: unknown,
    version: number
  ): Promise<ETagged<Habitacion>> {
    const res = await firstValueFrom(
      this.http.put<Envelope<Habitacion>>(`${API_BASE_URL}${path}`, body, {
        headers: { Accept: 'application/json', 'If-Match': etagDeVersion(version) },
        observe: 'response',
      })
    );
    return {
      data: res.body!.data,
      etag: res.headers.get('ETag'),
      correlationId: res.body!.meta.correlationId,
    };
  }

  private async listar<T>(path: string, params: HttpParams): Promise<Paged<T>> {
    const res = await firstValueFrom(
      this.http.get<Envelope<T[], ApiMetaPage>>(`${API_BASE_URL}${path}`, {
        headers: { Accept: 'application/json' },
        params,
        observe: 'response',
      })
    );
    const meta = (res.body!.meta ?? {}) as Partial<ApiMetaPage>;
    const data = unwrapList<T>(res.body!.data);
    return {
      data,
      page: typeof meta.page === 'number' ? meta.page : 0,
      size: typeof meta.size === 'number' ? meta.size : data.length,
      totalElements:
        typeof meta.totalElements === 'number' ? meta.totalElements : data.length,
      correlationId: meta.correlationId ?? '',
    };
  }
}
