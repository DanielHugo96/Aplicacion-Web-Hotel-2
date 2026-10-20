import { Pipe, PipeTransform } from '@angular/core';

/**
 * Fecha es-PE: `dd/MM/yyyy` (p. `04/10/2026`). La API usa ISO-8601; el pipe
 * convierte para mostrar. Las fechas sin hora (`YYYY-MM-DD`) se construyen en
 * hora local: `new Date('2026-10-04')` las interpreta como UTC y en America/Lima
 * mostraría el día anterior.
 */
@Pipe({ name: 'peDate', standalone: true })
export class PeDatePipe implements PipeTransform {
  transform(value: string | Date | null | undefined): string | null {
    if (!value) return null;
    const d = typeof value === 'string' ? this.desdeTexto(value) : value;
    if (!d || Number.isNaN(d.getTime())) return null;
    return new Intl.DateTimeFormat('es-PE', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(d);
  }

  private desdeTexto(value: string): Date | null {
    const soloFecha = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
    if (soloFecha) {
      return new Date(
        Number(soloFecha[1]),
        Number(soloFecha[2]) - 1,
        Number(soloFecha[3])
      );
    }
    return new Date(value);
  }
}
