import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { API_BASE_URL, API_PATHS } from '../api.config';
import {
  DashboardReporte,
  MetaReporte,
  PaginaReporte,
  ReporteBajoStockRow,
  ReporteCobrosRow,
  ReporteOcupacionRow,
  ReporteVentasRow,
  RespuestaDashboard,
} from '../models/reportes.models';
import { Envelope } from '../models/catalogo.models';

/** `Retry-After` del 503 de reporting se convierte a milisegundos para la UI. */
export function parseRetryAfter(value: string | null): number | null {
  if (!value) return null;
  const segundos = Number(value);
  if (Number.isFinite(segundos)) return Math.max(0, segundos * 1000);
  const fecha = Date.parse(value);
  return Number.isFinite(fecha) ? Math.max(fecha - Date.now(), 0) : null;
}

function metaDe(meta: Partial<MetaReporte> | undefined, fallback: string): MetaReporte {
  return {
    updatedAt: meta?.updatedAt ?? null,
    consistency: 'EVENTUAL',
    degraded: meta?.degraded ?? false,
    bootstrapComplete: meta?.bootstrapComplete ?? false,
    correlationId: meta?.correlationId ?? fallback,
    page: meta?.page,
    size: meta?.size,
    totalElements: meta?.totalElements,
  };
}

/**
 * Cliente HTTP de los cinco GET de reportes por el Gateway
 * (`docs/especificacion/microservicios/reporting/api.md` § REST legacy).
 *
 * Solo GET: sin `Idempotency-Key` ni `If-Match`, que el interceptor reserva a
 * las mutaciones. La meta `EVENTUAL` (`updatedAt`, `degraded`,
 * `bootstrapComplete`) se conserva tal como llega; el front no recalcula
 * importes ni métricas. Los errores los traduce `parseApiError` con el
 * catálogo de `errors.md`.
 */
@Injectable({ providedIn: 'root' })
export class ReportesService {
  private readonly http = inject(HttpClient);

  async dashboard(idHotel: number | null): Promise<RespuestaDashboard> {
    let params = new HttpParams();
    if (idHotel !== null) params = params.set('idHotel', idHotel);
    const res = await firstValueFrom(
      this.http.get<Envelope<DashboardReporte | null, Partial<MetaReporte>>>(
        `${API_BASE_URL}${API_PATHS.reporteDashboard}`,
        { headers: { Accept: 'application/json' }, params, observe: 'response' }
      )
    );
    return {
      data: res.body!.data ?? null,
      meta: metaDe(res.body!.meta, res.headers.get('X-Correlation-Id') ?? ''),
    };
  }

  ventas(
    idHotel: number,
    inicio: string,
    fin: string,
    page = 0,
    size = 20
  ): Promise<PaginaReporte<ReporteVentasRow>> {
    return this.listar<ReporteVentasRow>(
      API_PATHS.reporteVentas,
      this.rango(idHotel, inicio, fin, page, size)
    );
  }

  ocupacion(
    idHotel: number,
    inicio: string,
    fin: string,
    page = 0,
    size = 20
  ): Promise<PaginaReporte<ReporteOcupacionRow>> {
    return this.listar<ReporteOcupacionRow>(
      API_PATHS.reporteOcupacion,
      this.rango(idHotel, inicio, fin, page, size)
    );
  }

  cobros(
    idHotel: number,
    inicio: string,
    fin: string,
    page = 0,
    size = 20
  ): Promise<PaginaReporte<ReporteCobrosRow>> {
    return this.listar<ReporteCobrosRow>(
      API_PATHS.reporteCobros,
      this.rango(idHotel, inicio, fin, page, size)
    );
  }

  bajoStock(
    idHotel: number,
    limite = 5,
    page = 0,
    size = 20
  ): Promise<PaginaReporte<ReporteBajoStockRow>> {
    const params = new HttpParams()
      .set('idHotel', idHotel)
      .set('limite', limite)
      .set('page', page)
      .set('size', size);
    return this.listar<ReporteBajoStockRow>(API_PATHS.reporteBajoStock, params);
  }

  private rango(
    idHotel: number,
    inicio: string,
    fin: string,
    page: number,
    size: number
  ): HttpParams {
    return new HttpParams()
      .set('idHotel', idHotel)
      .set('inicio', inicio)
      .set('fin', fin)
      .set('page', page)
      .set('size', size);
  }

  private async listar<T>(path: string, params: HttpParams): Promise<PaginaReporte<T>> {
    const res = await firstValueFrom(
      this.http.get<Envelope<T[], Partial<MetaReporte>>>(
        `${API_BASE_URL}${path}`,
        { headers: { Accept: 'application/json' }, params, observe: 'response' }
      )
    );
    const meta = metaDe(res.body!.meta, res.headers.get('X-Correlation-Id') ?? '');
    const data = Array.isArray(res.body!.data) ? res.body!.data : [];
    const sizePedido = Number(params.get('size'));
    return {
      data,
      meta: {
        ...meta,
        page: meta.page ?? Number(params.get('page')) ?? 0,
        size: meta.size ?? (Number.isFinite(sizePedido) ? sizePedido : data.length),
        totalElements: meta.totalElements ?? data.length,
      },
    };
  }
}
