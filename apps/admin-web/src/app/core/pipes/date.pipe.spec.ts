import { describe, it, expect } from 'vitest';
import { PeDatePipe } from './date.pipe';
import { PeTimestampPipe } from './timestamp.pipe';

describe('Pipes de fecha es-PE', () => {
  it('PeDatePipe formatea dd/MM/yyyy', () => {
    const pipe = new PeDatePipe();
    const d = new Date(2026, 9, 4, 12, 0, 0); // 04/10/2026 hora local
    const esperado = new Intl.DateTimeFormat('es-PE', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(d);
    expect(pipe.transform(d)).toBe(esperado);
    // Una cadena ISO del mismo instante debe dar la misma fecha local
    expect(pipe.transform(d.toISOString())).toBe(esperado);
    expect(pipe.transform(null)).toBeNull();
  });

  it('PeTimestampPipe formatea dd/MM/yyyy HH:mm (sin coma)', () => {
    const pipe = new PeTimestampPipe();
    const d = new Date(2026, 9, 4, 15, 30, 0);
    const esperado = new Intl.DateTimeFormat('es-PE', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false,
    }).format(d).replace(/, /, ' ');
    expect(pipe.transform(d)).toBe(esperado);
    expect(pipe.transform(d.toISOString())).toBe(esperado);
    expect(pipe.transform(null)).toBeNull();
  });
});
