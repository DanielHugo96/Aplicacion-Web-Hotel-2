import { describe, expect, it } from 'vitest';
import { CATALOGO_ERRORS, ERROR_CATALOG, parseApiError } from './api-error';

describe('catálogo de errores', () => {
  it('cubre los dieciséis códigos del alcance del plan', () => {
    expect(CATALOGO_ERRORS).toHaveLength(16);
    expect(new Set(CATALOGO_ERRORS).size).toBe(16);
  });

  it('usa el mensaje para UI y la acción esperada de errors.md', () => {
    for (const code of CATALOGO_ERRORS) {
      const error = parseApiError({
        error: { error: { code, fields: { campo: 'motivo' } }, meta: { correlationId: 'cid-1' } },
      });
      expect(error.code).toBe(code);
      expect(error.message).toBe(ERROR_CATALOG[code].message);
      expect(error.action).toBe(ERROR_CATALOG[code].action);
      expect(error.fields['campo']).toBe('motivo');
      expect(error.correlationId).toBe('cid-1');
    }
  });

  it('un código desconocido conserva message y correlationId sin traducir', () => {
    const error = parseApiError({
      error: {
        message: 'Fallo no catalogado',
        error: { code: 'CODIGO_RARO', fields: {} },
        meta: { correlationId: 'cid-2' },
      },
    });
    expect(error.code).toBe('CODIGO_RARO');
    expect(error.message).toBe('Fallo no catalogado');
    expect(error.correlationId).toBe('cid-2');
  });

  it('sin cuerpo usa INTERNAL_ERROR del catálogo', () => {
    const error = parseApiError({ status: 500 });
    expect(error.code).toBe('INTERNAL_ERROR');
    expect(error.message).toBe('Ocurrió un error inesperado.');
  });
});
