import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL, API_PATHS } from '../api.config';
import {
  AjusteResultado,
  AjusteStockInput,
  AvanceOperacion,
  CarritoItem,
  EstadoOperacionVenta,
  EstadoVenta,
  FiltroEstadoProducto,
  Movimiento,
  OperacionVenta,
  Producto,
  ProductoAlta,
  ProductoAsignacionSede,
  ProductoUpdate,
  ResultadoCreacionVenta,
  ResultadoOperacionVenta,
  Venta,
  VentaAlta,
} from '../models/ventas.models';
import { ApiMetaPage, Envelope, ETagged, Paged } from '../models/catalogo.models';
import { ClienteService, resolverLocation } from './cliente.service';

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

/** El contrato exige `If-Match` con la versión V entre comillas (`"3"`). */
export function etagDeVersion(version: number): string {
  return `"${version}"`;
}

/**
 * Cliente HTTP del módulo de productos y ventas del panel. Todo sale por el
 * Gateway (`API_BASE_URL`); el envelope, la `Idempotency-Key` por intención,
 * el `If-Match` cacheado, el 401 y los errores los aporta el interceptor de
 * `front-admin-web-base`.
 *
 * Carrito, catálogo por estadía, alta de venta y sondeo de operaciones se
 * delegan en `ClienteService`: la pieza de `202` es compartida con el resto del
 * panel y no se duplica. Este servicio añade las lecturas y mutaciones
 * administrativas de productos, ventas, pago y anulación.
 */
@Injectable({ providedIn: 'root' })
export class VentasService {
  private readonly http = inject(HttpClient);
  private readonly cliente = inject(ClienteService);

  // --------------------------------------------------------------- productos

  async listarProductos(
    idHotel: number,
    page = 0,
    size = 20,
    filtro: FiltroEstadoProducto = 'activos'
  ): Promise<Paged<Producto>> {
    let params = new HttpParams().set('page', page).set('size', size).set('idHotel', idHotel);
    if (filtro === 'activos') params = params.set('estado', 'true');
    if (filtro === 'inactivos') params = params.set('estado', 'false');
    return this.listar<Producto>(API_PATHS.productoListar, params);
  }

  async buscarProducto(id: number, idHotel: number): Promise<ETagged<Producto>> {
    const params = new HttpParams().set('idHotel', idHotel);
    return this.buscar<Producto>(API_PATHS.productoBuscar(id), params);
  }

  async registrarProducto(body: ProductoAlta): Promise<Producto> {
    return this.post<Producto>(API_PATHS.productoRegistrar, body);
  }

  async actualizarProducto(
    id: number,
    idHotel: number,
    body: ProductoUpdate,
    ifMatch: string
  ): Promise<ETagged<Producto>> {
    const params = new HttpParams().set('idHotel', idHotel);
    return this.put<Producto>(API_PATHS.productoActualizar(id), body, ifMatch, params);
  }

  async eliminarProducto(
    id: number,
    idHotel: number,
    ifMatch: string
  ): Promise<ETagged<Producto>> {
    const params = new HttpParams().set('idHotel', idHotel);
    return this.delete<Producto>(API_PATHS.productoEliminar(id), ifMatch, params);
  }

  async asignarProductoSede(
    id: number,
    body: ProductoAsignacionSede
  ): Promise<Producto> {
    return this.post<Producto>(API_PATHS.productoAsignarSede(id), body);
  }

  async ajustarStock(
    id: number,
    body: AjusteStockInput,
    ifMatch: string
  ): Promise<AjusteResultado> {
    return this.post<AjusteResultado>(API_PATHS.productoAjustes(id), body, ifMatch);
  }

  async listarMovimientos(
    id: number,
    idHotel: number,
    page = 0,
    size = 20,
    inicio?: string | null,
    fin?: string | null
  ): Promise<Paged<Movimiento>> {
    let params = new HttpParams().set('page', page).set('size', size).set('idHotel', idHotel);
    if (inicio) params = params.set('inicio', inicio);
    if (fin) params = params.set('fin', fin);
    return this.listar<Movimiento>(API_PATHS.productoMovimientos(id), params);
  }

  // ------------------------------------------------------------------ ventas

  async listarVentas(
    idHotel: number,
    page = 0,
    size = 20,
    estado?: EstadoVenta | null,
    estadoOperacion?: EstadoOperacionVenta | null
  ): Promise<Paged<Venta>> {
    let params = new HttpParams().set('page', page).set('size', size).set('idHotel', idHotel);
    if (estado) params = params.set('estado', estado);
    if (estadoOperacion) params = params.set('estadoOperacion', estadoOperacion);
    return this.listar<Venta>(API_PATHS.ventaListar, params);
  }

  async buscarVenta(id: number): Promise<ETagged<Venta>> {
    return this.buscar<Venta>(API_PATHS.ventaBuscar(id));
  }

  /**
   * `PUT /api/venta/{id}` con cuerpo cerrado `{estado:"PAGADO",medioPago}`.
   * Puede responder 200 (terminal) o 202 (operación durable en curso): en el
   * segundo caso la pantalla muestra el banner y sondea, sin éxito optimista.
   */
  async pagarVenta(
    id: number,
    medioPago: 'EFECTIVO' | 'TARJETA' | 'OTRO',
    ifMatch: string
  ): Promise<ResultadoOperacionVenta> {
    const res = await firstValueFrom(
      this.http.put<Envelope<Venta>>(
        `${API_BASE_URL}${API_PATHS.ventaPorId(id)}`,
        { estado: 'PAGADO', medioPago },
        {
          headers: { Accept: 'application/json', 'If-Match': ifMatch },
          observe: 'response',
        }
      )
    );
    return this.resultadoOperacion(res);
  }

  /** `DELETE /api/venta/{id}?motivo=`; 202 pendiente, 200 ya cancelada o 409. */
  async anularVenta(
    id: number,
    motivo: string,
    ifMatch: string
  ): Promise<ResultadoOperacionVenta> {
    const params = new HttpParams().set('motivo', motivo);
    const res = await firstValueFrom(
      this.http.delete<Envelope<Venta>>(`${API_BASE_URL}${API_PATHS.ventaPorId(id)}`, {
        headers: { Accept: 'application/json', 'If-Match': ifMatch },
        params,
        observe: 'response',
      })
    );
    return this.resultadoOperacion(res);
  }

  // ------------------------------------------------- carrito y venta nueva

  listarCatalogo(idRecepcion: number, page = 0, size = 20): Promise<Paged<Producto>> {
    return this.cliente.listarCatalogo(idRecepcion, page, size) as Promise<Paged<Producto>>;
  }

  listarCarrito(idRecepcion: number): Promise<CarritoItem[]> {
    return this.cliente.listarCarrito(idRecepcion) as Promise<CarritoItem[]>;
  }

  agregarAlCarrito(
    idRecepcion: number,
    idProducto: number,
    cantidad: number
  ): Promise<CarritoItem> {
    return this.cliente.agregarAlCarrito(idRecepcion, idProducto, cantidad) as Promise<CarritoItem>;
  }

  quitarDelCarrito(idRecepcion: number, idProducto: number): Promise<void> {
    return this.cliente.quitarDelCarrito(idRecepcion, idProducto);
  }

  crearVenta(body: VentaAlta): Promise<ResultadoCreacionVenta> {
    return this.cliente.crearVenta(body) as Promise<ResultadoCreacionVenta>;
  }

  async consultarOperacion(operationId: string): Promise<OperacionVenta> {
    return (await this.cliente.consultarOperacion(operationId)) as OperacionVenta;
  }

  seguirOperacion(
    location: string,
    retryAfterMs: number,
    onAvance: (avance: AvanceOperacion) => void,
    signal?: AbortSignal
  ): Promise<AvanceOperacion | null> {
    return this.cliente.seguirOperacion(location, retryAfterMs, onAvance, signal) as Promise<
      AvanceOperacion | null
    >;
  }

  // ----------------------------------------------------------------- helpers

  private resultadoOperacion(res: {
    status: number;
    body: Envelope<Venta> | null;
    headers: { get(name: string): string | null };
  }): ResultadoOperacionVenta {
    const venta = res.body!.data;
    if (res.status === 202) {
      const location = res.headers.get('Location');
      if (!location) throw new Error('La operación pendiente no trae Location');
      return {
        kind: 'pendiente',
        venta,
        location: resolverLocation(location),
        retryAfterMs: parseRetryAfter(res.headers.get('Retry-After')) ?? RETRY_AFTER_POR_DEFECTO_MS,
      };
    }
    return { kind: 'confirmada', venta };
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
      totalElements: typeof meta.totalElements === 'number' ? meta.totalElements : data.length,
      correlationId: meta.correlationId ?? '',
    };
  }

  private async buscar<T>(path: string, params?: HttpParams): Promise<ETagged<T>> {
    const res = await firstValueFrom(
      this.http.get<Envelope<T>>(`${API_BASE_URL}${path}`, {
        headers: { Accept: 'application/json' },
        ...(params ? { params } : {}),
        observe: 'response',
      })
    );
    return {
      data: res.body!.data,
      etag: res.headers.get('ETag'),
      correlationId: res.body!.meta.correlationId,
    };
  }

  private async post<T>(path: string, body: unknown, ifMatch?: string): Promise<T> {
    const res = await firstValueFrom(
      this.http.post<Envelope<T>>(`${API_BASE_URL}${path}`, body, {
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
          ...(ifMatch ? { 'If-Match': ifMatch } : {}),
        },
        observe: 'response',
      })
    );
    return res.body!.data;
  }

  private async put<T>(
    path: string,
    body: unknown,
    ifMatch: string,
    params?: HttpParams
  ): Promise<ETagged<T>> {
    const res = await firstValueFrom(
      this.http.put<Envelope<T>>(`${API_BASE_URL}${path}`, body, {
        headers: { Accept: 'application/json', 'Content-Type': 'application/json', 'If-Match': ifMatch },
        ...(params ? { params } : {}),
        observe: 'response',
      })
    );
    return {
      data: res.body!.data,
      etag: res.headers.get('ETag'),
      correlationId: res.body!.meta.correlationId,
    };
  }

  private async delete<T>(
    path: string,
    ifMatch: string,
    params?: HttpParams
  ): Promise<ETagged<T>> {
    const res = await firstValueFrom(
      this.http.delete<Envelope<T>>(`${API_BASE_URL}${path}`, {
        headers: { Accept: 'application/json', 'If-Match': ifMatch },
        ...(params ? { params } : {}),
        observe: 'response',
      })
    );
    return {
      data: res.body!.data,
      etag: res.headers.get('ETag'),
      correlationId: res.body!.meta.correlationId,
    };
  }
}
