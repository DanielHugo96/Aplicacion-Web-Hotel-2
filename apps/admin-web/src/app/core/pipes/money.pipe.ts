import { Pipe, PipeTransform } from '@angular/core';

/**
 * Moneda es-PE: `S/ 70.00` con separador de miles y 2 decimales
 * (`S/ 1,234.50`). La API envía PEN en ISO-8601/decimal; el pipe convierte.
 * Nunca se recalcan importes: se formatea lo que devuelve el backend.
 */
@Pipe({ name: 'money', standalone: true })
export class MoneyPipe implements PipeTransform {
  transform(value: number | null | undefined): string | null {
    if (value === null || value === undefined) return null;
    return new Intl.NumberFormat('es-PE', {
      style: 'currency',
      currency: 'PEN',
      currencyDisplay: 'symbol',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(value);
  }
}
