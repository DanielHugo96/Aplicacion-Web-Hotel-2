import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL, API_PATHS } from '../api.config';
import {
  AvanceOperacion,
  CarritoItem,
  OperacionVenta,
  Producto,
  Recepcion,
  ResultadoVenta,
  Venta,
  VentaAlta,
} from '../models/cliente.models';
import {
  ApiMetaPage,
  ApiMetaSingle,
  Envelope,
  ETagged,
  Paged,
} from '../models/catalogo.models';

const RETRY_AFTER_POR_DEFECTO_MS = 2000;

function unwrapList<T>(data: unknown): T[] {
  if (Array.isArray(data)) return data as T[];
  if (data && typeof data === 'object') {
    const primera = Object.values(data as Record<string, unknown>).find((v) => Array.isArray(v));
    if (Array.isArray(primera)) return primera as T[];
  }
  return [];
}

function parseRetryAfter(value: string | null): number | null {
  if (!value) return null;
  const segundos = Number(value);
  if (Number.isFinite(segundos)) return Math.max(0, segundos * 1000);
  const fecha = Date.parse(value);
  return Number.isFinite(fecha) ? Math.max(fecha - Date.now(), 0) : null;
}

/** `Location` puede venir relativo (`/api/...`) o absoluto; siempre sale por el Gateway. */
export function resolverLocation(location: string): string {
  if (/^https?:\/\//i.test(location)) return location;
  return `${API_BASE_URL}${location.startsWith('/') ? '' : '/'}${location}`;
}

function esperar(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, Math.max(0, ms)));
}

/**
 * Cliente HTTP del área de cliente. Todo pasa por el Gateway (`API_BASE_URL`);
 * el envelope, `Idempotency-Key`, `If-Match`, 401 y errores los aporta el
 * interceptor de `front-admin-web-base`.
 *
 * La creación de venta necesita ver el 202 para mostrar el banner y el polling
 * con "Actualizado …" en cada reintento; por eso el interceptor deja pasar el
 * 202 de `POST /api/venta` y este servicio sondea `Location` aquí.
 */
@Injectable({ providedIn: 'root' })
export class ClienteService {
  private readonly http = inject(HttpClient);

  // --------------------------------------------------------------- estadías

  async listarEstadiasPropias(
    idCliente: number,
    page = 0,
    size = 100
  ): Promise<Paged<Recepcion>> {
    return this.listar<Recepcion>(
      API_PATHS.recepcionCliente(idCliente),
      new HttpParams().set('page', page).set('size', size)
    );
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

  /**
   * El `sub` del JWT identifica al cliente. `ClienteContextService` lo lee de
   * la sesión en memoria y lo pasa aquí; el servicio nunca acepta un id de
   * cliente elegido por el usuario.
   */

  // --------------------------------------------------------------- catálogo

  async listarCatalogo(idRecepcion: number, page = 0, size = 20): Promise<Paged<Producto>> {
    return this.listar<Producto>(
      API_PATHS.ventaCatalogo,
      new HttpParams().set('idRecepcion', idRecepcion).set('page', page).set('size', size)
    );
  }

  // --------------------------------------------------------------- carrito

  async listarCarrito(idRecepcion: number): Promise<CarritoItem[]> {
    const res = await firstValueFrom(
      this.http.get<Envelope<CarritoItem[], ApiMetaSingle>>(
        `${API_BASE_URL}${API_PATHS.carritoListar(idRecepcion)}`,
        { headers: { Accept: 'application/json' } }
      )
    );
    return unwrapList<CarritoItem>(res.data);
  }

  async agregarAlCarrito(
    idRecepcion: number,
    idProducto: number,
    cantidad: number
  ): Promise<CarritoItem> {
    const params = new HttpParams()
      .set('idRecepcion', idRecepcion)
      .set('idProducto', idProducto)
      .set('cantidad', cantidad);
    const res = await firstValueFrom(
      this.http.post<Envelope<CarritoItem>>(
        `${API_BASE_URL}${API_PATHS.carritoAgregar}`,
        null,
        { headers: { Accept: 'application/json' }, params }
      )
    );
    return res.data;
  }

  async quitarDelCarrito(idRecepcion: number, idProducto: number): Promise<void> {
    const params = new HttpParams()
      .set('idRecepcion', idRecepcion)
      .set('idProducto', idProducto);
    await firstValueFrom(
      this.http.delete<void>(`${API_BASE_URL}${API_PATHS.carritoEliminar}`, {
        headers: { Accept: 'application/json' },
        params,
      })
    );
  }

  // --------------------------------------------------------------- ventas

  /**
   * Crea la venta PENDIENTE. El cuerpo es JSON cerrado: solo `idRecepcion`,
   * `estado` y `detalles[{idProducto, cantidad}]`. Nunca precios ni IDs
   * derivados (los calcula el backend).
   */
  async crearVenta(body: VentaAlta): Promise<ResultadoVenta> {
    const res = await firstValueFrom(
      this.http.post<Envelope<Venta>>(`${API_BASE_URL}${API_PATHS.ventaCrear}`, body, {
        headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
        observe: 'response',
      })
    );
    if (res.status === 202) {
      const location = res.headers.get('Location');
      if (!location) {
        throw new Error('La operación pendiente no trae Location');
      }
      return {
        kind: 'pendiente',
        venta: res.body!.data,
        location: resolverLocation(location),
        retryAfterMs: parseRetryAfter(res.headers.get('Retry-After')) ?? RETRY_AFTER_POR_DEFECTO_MS,
      };
    }
    return { kind: 'confirmada', venta: res.body!.data };
  }

  async listarVentasRecepcion(
    idRecepcion: number,
    page = 0,
    size = 50
  ): Promise<Paged<Venta>> {
    return this.listar<Venta>(
      API_PATHS.ventaRecepcion(idRecepcion),
      new HttpParams().set('page', page).set('size', size)
    );
  }

  async buscarVenta(id: number): Promise<Venta> {
    const res = await firstValueFrom(
      this.http.get<Envelope<Venta>>(`${API_BASE_URL}${API_PATHS.ventaBuscar(id)}`, {
        headers: { Accept: 'application/json' },
      })
    );
    return res.data;
  }

  async consultarOperacion(operationId: string): Promise<OperacionVenta> {
    const res = await firstValueFrom(
      this.http.get<Envelope<OperacionVenta>>(
        `${API_BASE_URL}${API_PATHS.ventaOperacion(operationId)}`,
        { headers: { Accept: 'application/json' } }
      )
    );
    return res.data;
  }

  /**
   * Sondea `Location` respetando `Retry-After` hasta el estado terminal y
   * consulta la operación (mismo `operationId`) para conocer su fase. Invoca
   * `onAvance` en cada reintento para que la pantalla muestre "Actualizado …".
   * Devuelve `null` si se cancela.
   */
  async seguirOperacion(
    location: string,
    retryAfterMs: number,
    onAvance: (avance: AvanceOperacion) => void,
    signal?: AbortSignal
  ): Promise<AvanceOperacion | null> {
    let espera = retryAfterMs > 0 ? retryAfterMs : RETRY_AFTER_POR_DEFECTO_MS;
    let ultimo: AvanceOperacion | null = null;

    while (!signal?.aborted) {
      await esperar(espera);
      if (signal?.aborted) return null;

      const res = await firstValueFrom(
        this.http.get<Envelope<Venta>>(location, {
          headers: { Accept: 'application/json' },
          observe: 'response',
        })
      );
      const venta = res.body?.data ?? null;
      const retry = parseRetryAfter(res.headers.get('Retry-After'));
      if (retry !== null) espera = retry > 0 ? retry : RETRY_AFTER_POR_DEFECTO_MS;

      let operacion: OperacionVenta | null = null;
      if (venta?.operationId) {
        try {
          operacion = await this.consultarOperacion(venta.operationId);
        } catch {
          operacion = null;
        }
      }

      ultimo = { venta, operacion, actualizadoEn: new Date() };
      onAvance(ultimo);

      const ventaTerminal =
        venta !== null &&
        ['CONFIRMADA', 'RECHAZADA', 'CANCELADA'].includes(venta.estadoOperacion);
      if (ventaTerminal || operacion?.estado === 'TERMINAL') {
        return ultimo;
      }
    }
    return ultimo;
  }

  // --------------------------------------------------------------- helpers

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
      totalElements: typeof meta.totalElements === 'number' ? meta.totalElements : data.length,
      correlationId: meta.correlationId ?? '',
    };
  }
}
