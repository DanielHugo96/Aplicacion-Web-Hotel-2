import { DatePipe, registerLocaleData } from '@angular/common';
import localeEsPe from '@angular/common/locales/es-PE';
import { MonedaPePipe } from '../shared/moneda-pe.pipe';

registerLocaleData(localeEsPe);

describe('Formatos es-PE', () => {
  it('formatea moneda con S/, dos decimales y miles con coma', () => {
    const pipe = new MonedaPePipe();
    expect(pipe.transform(1234.5)).toBe('S/ 1,234.50');
    expect(pipe.transform(70)).toBe('S/ 70.00');
    expect(pipe.transform(0)).toBe('S/ 0.00');
  });

  it('formatea fecha y timestamp del sistema en es-PE', () => {
    const pipe = new DatePipe('es-PE');
    const fecha = new Date(2026, 9, 4, 15, 30);
    expect(pipe.transform(fecha, 'dd/MM/yyyy')).toBe('04/10/2026');
    expect(pipe.transform(fecha, 'dd/MM/yyyy HH:mm')).toBe('04/10/2026 15:30');
  });
});
