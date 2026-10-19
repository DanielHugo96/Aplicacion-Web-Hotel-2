import { Injectable } from '@angular/core';

/**
 * Caché en memoria de ETag por URL de recurso. Se llena de las respuestas
 * GET/PUT y se envía como If-Match en las mutaciones sobre ese recurso.
 */
@Injectable({ providedIn: 'root' })
export class ETagCacheService {
  private readonly cache = new Map<string, string>();

  get(url: string): string | null {
    return this.cache.get(url) ?? null;
  }

  set(url: string, etag: string): void {
    this.cache.set(url, etag);
  }

  has(url: string): boolean {
    return this.cache.has(url);
  }

  delete(url: string): void {
    this.cache.delete(url);
  }
}
