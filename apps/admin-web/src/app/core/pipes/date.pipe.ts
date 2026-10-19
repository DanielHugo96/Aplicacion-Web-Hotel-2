import { Pipe, PipeTransform } from '@angular/core';

/**
 * Fecha es-PE: `dd/MM/yyyy` (p.. `04/10/2026`). La API usa ISO-8601;
 * el pipe convierte para mostrar.
 */
@Pipe({ name: 'peDate', standalone: true })
export class PeDatePipe implements PipeTransform {
  transform(value: string | Date | null | undefined): string | null {
    if (!value) return null;
    const d = typeof value === 'string' ? new Date(value) : value;
    if (Number.isNaN(d.getTime())) return null;
    return new Intl.DateTimeFormat('es-PE', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(d);
  }
}
