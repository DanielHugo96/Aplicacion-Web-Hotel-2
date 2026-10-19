import { Pipe, PipeTransform } from '@angular/core';

/**
 * Fecha y hora del sistema es-PE: `dd/MM/yyyy HH:mm`
 * (p. `04/10/2026 15:30`).
 */
@Pipe({ name: 'peTimestamp', standalone: true })
export class PeTimestampPipe implements PipeTransform {
  transform(value: string | Date | null | undefined): string | null {
    if (!value) return null;
    const d = typeof value === 'string' ? new Date(value) : value;
    if (Number.isNaN(d.getTime())) return null;
    return new Intl.DateTimeFormat('es-PE', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(d).replace(/, /, ' ');
  }
}
