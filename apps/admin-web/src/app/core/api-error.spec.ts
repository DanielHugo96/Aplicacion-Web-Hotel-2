import { describe, expect, it } from 'vitest';
import { CATALOGO_ERRORS, ERROR_CATALOG, parseApiError } from './api-error';

describe('catálogo de errores', () => {
  it('cubre los veintiocho códigos del alcance del panel, recepción y área de cliente', () => {
    expect(CATALOGO_ERRORS).toHaveLength(28);
    expect(new Set(CATALOGO_ERRORS).size).toBe(28);
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

  it('los códigos del área de cliente copian el catálogo de errors.md', () => {
    expect(ERROR_CATALOG['STAY_NOT_ACTIVE'].message).toBe(
      'La estadía no está activa para esa operación.'
    );
    expect(ERROR_CATALOG['PRODUCT_INACTIVE'].message).toBe(
      'El producto ya no está disponible en esta sede.'
    );
    expect(ERROR_CATALOG['STOCK_INSUFFICIENT'].message).toBe(
      'No hay stock suficiente de uno o más productos.'
    );
    expect(ERROR_CATALOG['PASSWORD_POLICY'].message).toBe(
      'La contraseña debe tener entre 12 y 72 bytes (caracteres UTF-8).'
    );
    expect(ERROR_CATALOG['CURRENT_PASSWORD_MISMATCH'].message).toBe(
      'La contraseña actual no es correcta.'
    );
  });

  it('los códigos de recepción y operaciones copian el catálogo de errors.md', () => {
    expect(ERROR_CATALOG['ROOM_OCCUPIED'].message).toBe('La habitación ya está ocupada.');
    expect(ERROR_CATALOG['ROOM_NOT_READY'].message).toBe(
      'La habitación no admite ingreso todavía (limpieza o mantenimiento).'
    );
    expect(ERROR_CATALOG['ROOM_NOT_SYNCED'].message).toBe(
      'La habitación aún no está sincronizada con recepción.'
    );
    expect(ERROR_CATALOG['GATE_NOT_AVAILABLE'].message).toBe(
      'No se puede bloquear la habitación: está ocupada o en limpieza.'
    );
    expect(ERROR_CATALOG['ROOM_OPERATION_IN_PROGRESS'].message).toBe(
      'La habitación tiene una operación en curso.'
    );
    expect(ERROR_CATALOG['OPERATIONS_IN_PROGRESS'].message).toBe(
      'Hay consumos en proceso. Espera a que terminen.'
    );
    expect(ERROR_CATALOG['ADVANCE_EXCEEDS_NEW_TOTAL'].message).toBe(
      'La nueva fecha deja el alojamiento por debajo del adelanto registrado.'
    );
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
