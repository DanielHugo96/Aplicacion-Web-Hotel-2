import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { mapHttpError, validationError } from './api-error';
import {
  ApiList,
  ApiSingle,
  CategoriaPublica,
  HotelPublico,
} from './catalog.models';
import { GATEWAY_BASE_URL } from './config';

const DEFAULT_PAGE = 0;
const DEFAULT_SIZE = 20;
const MAX_SIZE = 100;

@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);
  private readonly base = inject(GATEWAY_BASE_URL);

  /** GET /api/public/hoteles?page=&size= */
  listarHoteles(
    page = DEFAULT_PAGE,
    size = DEFAULT_SIZE,
  ): Observable<ApiList<HotelPublico>> {
    return this.http
      .get<ApiList<HotelPublico>>(`${this.base}/api/public/hoteles`, {
        params: this.pageParams(page, size),
      })
      .pipe(catchError((error) => throwError(() => mapHttpError(error))));
  }

  /** GET /api/public/hoteles/{slug} */
  obtenerHotel(slug: string): Observable<ApiSingle<HotelPublico>> {
    return this.http
      .get<ApiSingle<HotelPublico>>(
        `${this.base}/api/public/hoteles/${encodeURIComponent(slug)}`,
      )
      .pipe(catchError((error) => throwError(() => mapHttpError(error))));
  }

  /** GET /api/public/hoteles/{slug}/categorias?page=&size=&capacidad= */
  listarCategorias(
    slug: string,
    page = DEFAULT_PAGE,
    size = DEFAULT_SIZE,
    capacidad?: number,
  ): Observable<ApiList<CategoriaPublica>> {
    if (capacidad !== undefined) {
      if (!Number.isInteger(capacidad) || capacidad < 1 || capacidad > 10) {
        return throwError(() =>
          validationError('capacidad', 'La capacidad debe estar entre 1 y 10.'),
        );
      }
    }

    let params = this.pageParams(page, size);
    if (capacidad !== undefined) {
      params = params.set('capacidad', capacidad);
    }

    return this.http
      .get<ApiList<CategoriaPublica>>(
        `${this.base}/api/public/hoteles/${encodeURIComponent(slug)}/categorias`,
        { params },
      )
      .pipe(catchError((error) => throwError(() => mapHttpError(error))));
  }

  /** GET /api/public/hoteles/{slug}/categorias/{idCategoria} */
  obtenerCategoria(
    slug: string,
    idCategoria: number,
  ): Observable<ApiSingle<CategoriaPublica>> {
    if (!Number.isInteger(idCategoria) || idCategoria < 1) {
      return throwError(() =>
        validationError('idCategoria', 'El tipo de habitación no es válido.'),
      );
    }

    return this.http
      .get<ApiSingle<CategoriaPublica>>(
        `${this.base}/api/public/hoteles/${encodeURIComponent(slug)}/categorias/${idCategoria}`,
      )
      .pipe(catchError((error) => throwError(() => mapHttpError(error))));
  }

  private pageParams(page: number, size: number): HttpParams {
    if (!Number.isInteger(page) || page < 0) {
      throw validationError('page', 'La página debe ser 0 o mayor.');
    }
    if (!Number.isInteger(size) || size < 1 || size > MAX_SIZE) {
      throw validationError('size', `El tamaño debe estar entre 1 y ${MAX_SIZE}.`);
    }
    return new HttpParams().set('page', page).set('size', size);
  }
}
