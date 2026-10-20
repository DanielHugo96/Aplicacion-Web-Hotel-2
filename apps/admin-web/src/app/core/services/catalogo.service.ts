import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL, API_PATHS } from '../api.config';
import { ETagCacheService } from './etag-cache.service';
import {
  Categoria,
  CategoriaInput,
  ETagged,
  Envelope,
  ApiMetaPage,
  EstadoFisico,
  Habitacion,
  HabitacionInput,
  HabitacionUpdate,
  Hotel,
  HotelInput,
  Paged,
  Persona,
  PersonaAlta,
  PersonaUpdate,
  Piso,
  PisoInput,
  PisoUpdate,
  TipoPersona,
} from '../models/catalogo.models';

/**
 * El contrato publica `data` como array para las listas. Algunos servicios
 * devuelven un objeto con la colección dentro (`personas`, `tipos`, ...);
 * se aceptan ambas formas para no romper contra el Gateway real.
 */
function unwrapList<T>(data: unknown): T[] {
  if (Array.isArray(data)) return data as T[];
  if (data && typeof data === 'object') {
    const obj = data as Record<string, unknown>;
    const preferidas = [
      'personas',
      'tipos',
      'hoteles',
      'pisos',
      'categorias',
      'habitaciones',
      'estados',
    ];
    for (const clave of preferidas) {
      if (Array.isArray(obj[clave])) return obj[clave] as T[];
    }
    const primera = Object.values(obj).find((v) => Array.isArray(v));
    if (Array.isArray(primera)) return primera as T[];
  }
  return [];
}

/**
 * Cliente HTTP tipado del catálogo. Todo pasa por el Gateway
 * (`API_BASE_URL`): nunca por puertos de servicios.
 *
 * Lee y guarda el `ETag` de cada recurso en `ETagCacheService`; el interceptor
 * lo envía como `If-Match` en las mutaciones. La `Idempotency-Key` la pone el
 * interceptor una vez por intención. Tras un 412 el llamador recarga el
 * recurso (el servicio limpia el ETag viejo).
 */
@Injectable({ providedIn: 'root' })
export class CatalogoService {
  private readonly http = inject(HttpClient);
  private readonly etags = inject(ETagCacheService);

  // ---------- Personas ----------
  async listarPersonas(
    page: number,
    size: number,
    filtros: { idTipoPersona?: number | null; documento?: string | null } = {}
  ): Promise<Paged<Persona>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filtros.idTipoPersona) params = params.set('idTipoPersona', filtros.idTipoPersona);
    const documento = filtros.documento?.trim();
    if (documento) params = params.set('documento', documento);
    return this.listar<Persona>(API_PATHS.personaListar, params);
  }

  buscarPersona(id: number): Promise<ETagged<Persona>> {
    return this.buscar<Persona>(API_PATHS.personaBuscar(id));
  }

  async registrarPersona(body: PersonaAlta): Promise<Persona> {
    return this.post<Persona>(API_PATHS.personaRegistrar, body);
  }

  async actualizarPersona(id: number, body: PersonaUpdate): Promise<ETagged<Persona>> {
    return this.put<Persona>(API_PATHS.personaActualizar(id), body);
  }

  async eliminarPersona(id: number): Promise<ETagged<Persona>> {
    return this.delete<Persona>(API_PATHS.personaEliminar(id));
  }

  // ---------- Roles ----------
  async listarTiposPersona(): Promise<TipoPersona[]> {
    const res = await firstValueFrom(
      this.http.get<Envelope<TipoPersona[]>>(`${API_BASE_URL}${API_PATHS.tipoPersonaListar}`, {
        headers: { Accept: 'application/json' },
      })
    );
    return unwrapList<TipoPersona>(res.data);
  }

  buscarTipoPersona(id: number): Promise<ETagged<TipoPersona>> {
    return this.buscar<TipoPersona>(API_PATHS.tipoPersonaBuscar(id));
  }

  async actualizarTipoPersona(id: number, descripcion: string): Promise<ETagged<TipoPersona>> {
    return this.put<TipoPersona>(API_PATHS.tipoPersonaActualizar(id), { descripcion });
  }

  // ---------- Sedes ----------
  async listarSedes(page = 0, size = 100): Promise<Paged<Hotel>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.listar<Hotel>(API_PATHS.hotelListar, params);
  }

  buscarSede(id: number): Promise<ETagged<Hotel>> {
    return this.buscar<Hotel>(API_PATHS.hotelBuscar(id));
  }

  async registrarSede(body: HotelInput): Promise<Hotel> {
    return this.post<Hotel>(API_PATHS.hotelRegistrar, body);
  }

  async actualizarSede(id: number, body: HotelInput): Promise<ETagged<Hotel>> {
    return this.put<Hotel>(API_PATHS.hotelActualizar(id), body);
  }

  async eliminarSede(id: number): Promise<ETagged<Hotel>> {
    return this.delete<Hotel>(API_PATHS.hotelEliminar(id));
  }

  // ---------- Pisos ----------
  async listarPisos(page: number, size: number, idHotel: number): Promise<Paged<Piso>> {
    const params = new HttpParams().set('page', page).set('size', size).set('idHotel', idHotel);
    return this.listar<Piso>(API_PATHS.pisoListar, params);
  }

  buscarPiso(id: number): Promise<ETagged<Piso>> {
    return this.buscar<Piso>(API_PATHS.pisoBuscar(id));
  }

  async registrarPiso(body: PisoInput): Promise<Piso> {
    return this.post<Piso>(API_PATHS.pisoRegistrar, body);
  }

  async actualizarPiso(id: number, body: PisoUpdate): Promise<ETagged<Piso>> {
    return this.put<Piso>(API_PATHS.pisoActualizar(id), body);
  }

  async eliminarPiso(id: number): Promise<ETagged<Piso>> {
    return this.delete<Piso>(API_PATHS.pisoEliminar(id));
  }

  // ---------- Categorías ----------
  async listarCategorias(page = 0, size = 100): Promise<Paged<Categoria>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.listar<Categoria>(API_PATHS.categoriaListar, params);
  }

  buscarCategoria(id: number): Promise<ETagged<Categoria>> {
    return this.buscar<Categoria>(API_PATHS.categoriaBuscar(id));
  }

  async registrarCategoria(body: CategoriaInput): Promise<Categoria> {
    return this.post<Categoria>(API_PATHS.categoriaRegistrar, body);
  }

  async actualizarCategoria(id: number, body: CategoriaInput): Promise<ETagged<Categoria>> {
    return this.put<Categoria>(API_PATHS.categoriaActualizar(id), body);
  }

  async eliminarCategoria(id: number): Promise<ETagged<Categoria>> {
    return this.delete<Categoria>(API_PATHS.categoriaEliminar(id));
  }

  // ---------- Habitaciones ----------
  async listarHabitaciones(
    page: number,
    size: number,
    idHotel: number,
    idCategoria?: number | null
  ): Promise<Paged<Habitacion>> {
    let params = new HttpParams().set('page', page).set('size', size).set('idHotel', idHotel);
    if (idCategoria) params = params.set('idCategoria', idCategoria);
    return this.listar<Habitacion>(API_PATHS.habitacionListar, params);
  }

  buscarHabitacion(id: number): Promise<ETagged<Habitacion>> {
    return this.buscar<Habitacion>(API_PATHS.habitacionBuscar(id));
  }

  async registrarHabitacion(body: HabitacionInput): Promise<Habitacion> {
    return this.post<Habitacion>(API_PATHS.habitacionRegistrar, body);
  }

  async actualizarHabitacion(id: number, body: HabitacionUpdate): Promise<ETagged<Habitacion>> {
    return this.put<Habitacion>(API_PATHS.habitacionActualizar(id), body);
  }

  // ---------- Estados físicos ----------
  async listarEstadosFisicos(): Promise<EstadoFisico[]> {
    const res = await firstValueFrom(
      this.http.get<Envelope<EstadoFisico[]>>(`${API_BASE_URL}${API_PATHS.estadoHabitacionListar}`, {
        headers: { Accept: 'application/json' },
      })
    );
    return unwrapList<EstadoFisico>(res.data);
  }

  buscarEstadoFisico(id: number): Promise<ETagged<EstadoFisico>> {
    return this.buscar<EstadoFisico>(API_PATHS.estadoHabitacionBuscar(id));
  }

  async actualizarEstadoFisico(id: number, descripcion: string): Promise<ETagged<EstadoFisico>> {
    return this.put<EstadoFisico>(API_PATHS.estadoHabitacionActualizar(id), { descripcion });
  }

  // ---------- Helpers ----------
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

  private async buscar<T>(path: string): Promise<ETagged<T>> {
    const res = await firstValueFrom(
      this.http.get<Envelope<T>>(`${API_BASE_URL}${path}`, {
        headers: { Accept: 'application/json' },
        observe: 'response',
      })
    );
    const etag = res.headers.get('ETag');
    if (etag) this.etags.set(`${API_BASE_URL}${path}`, etag);
    return { data: res.body!.data, etag, correlationId: res.body!.meta.correlationId };
  }

  private async post<T>(path: string, body: unknown): Promise<T> {
    const res = await firstValueFrom(
      this.http.post<Envelope<T>>(`${API_BASE_URL}${path}`, body, {
        headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
        observe: 'response',
      })
    );
    return res.body!.data;
  }

  private async put<T>(path: string, body: unknown): Promise<ETagged<T>> {
    const buscarPath = path.split('?')[0].replace('/actualizar/', '/buscar/');
    const etagVigente = this.etags.get(`${API_BASE_URL}${buscarPath}`);
    if (etagVigente) this.etags.set(`${API_BASE_URL}${path}`, etagVigente);
    try {
      const res = await firstValueFrom(
        this.http.put<Envelope<T>>(`${API_BASE_URL}${path}`, body, {
          headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
          observe: 'response',
        })
      );
      const etag = res.headers.get('ETag');
      if (etag) {
        this.etags.set(`${API_BASE_URL}${path}`, etag);
        this.etags.set(`${API_BASE_URL}${buscarPath}`, etag);
      }
      return { data: res.body!.data, etag, correlationId: res.body!.meta.correlationId };
    } catch (err) {
      const status = (err as any)?.status;
      if (status === 412 || status === 428) {
        this.etags.delete(`${API_BASE_URL}${path}`);
        this.etags.delete(`${API_BASE_URL}${buscarPath}`);
      }
      throw err;
    }
  }

  private async delete<T>(path: string): Promise<ETagged<T>> {
    const buscarPath = path.split('?')[0].replace('/eliminar/', '/buscar/');
    const etagVigente = this.etags.get(`${API_BASE_URL}${buscarPath}`);
    if (etagVigente) this.etags.set(`${API_BASE_URL}${path}`, etagVigente);
    try {
      const res = await firstValueFrom(
        this.http.delete<Envelope<T>>(`${API_BASE_URL}${path}`, {
          headers: { Accept: 'application/json' },
          observe: 'response',
        })
      );
      const etag = res.headers.get('ETag');
      if (etag) {
        this.etags.set(`${API_BASE_URL}${path}`, etag);
        this.etags.set(`${API_BASE_URL}${buscarPath}`, etag);
      }
      return { data: res.body!.data, etag, correlationId: res.body!.meta.correlationId };
    } catch (err) {
      const status = (err as any)?.status;
      if (status === 412 || status === 428) {
        this.etags.delete(`${API_BASE_URL}${path}`);
        this.etags.delete(`${API_BASE_URL}${buscarPath}`);
      }
      throw err;
    }
  }
}
