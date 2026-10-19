import { describe, it, expect } from 'vitest';
import { MoneyPipe } from './money.pipe';

describe('MoneyPipe (es-PE)', () => {
  const pipe = new MoneyPipe();
  const opciones = {
    style: 'currency' as const,
    currency: 'PEN' as const,
    currencyDisplay: 'symbol' as const,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  };

  it('formatea 1234.5 como S/ 1,234.50', () => {
    const esperado = new Intl.NumberFormat('es-PE', opciones).format(1234.5);
    expect(pipe.transform(1234.5)).toBe(esperado);
    expect(pipe.transform(1234.5)).toContain('S/');
    expect(pipe.transform(1234.5)).toContain('1,234.50');
  });

  it('formatea 70 como S/ 70.00', () => {
    const esperado = new Intl.NumberFormat('es-PE', opciones).format(70);
    expect(pipe.transform(70)).toBe(esperado);
  });

  it('nulo o undefined devuelve null', () => {
    expect(pipe.transform(null)).toBeNull();
    expect(pipe.transform(undefined)).toBeNull();
  });
});
