import { CurrencyPipe, registerLocaleData } from '@angular/common';
import localeEsPe from '@angular/common/locales/es-PE';
import { Pipe, PipeTransform } from '@angular/core';

registerLocaleData(localeEsPe);

/**
 * Moneda PEN en es-PE con el formato exacto de la especificación:
 * `S/ 1,234.50` (dos decimales, miles con coma y espacio simple).
 * Reutiliza `CurrencyPipe`; solo normaliza el espacio duro que inserta ICU.
 */
@Pipe({ name: 'monedaPe' })
export class MonedaPePipe implements PipeTransform {
  private readonly currency = new CurrencyPipe('es-PE');

  transform(value: number | null | undefined): string {
    if (value === null || value === undefined) {
      return '';
    }
    return (this.currency.transform(value, 'PEN', 'symbol-narrow', '1.2-2') ?? '')
      .replace(/\u00a0/g, ' ');
  }
}
