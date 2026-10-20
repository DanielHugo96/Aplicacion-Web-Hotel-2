import { describe, expect, it } from 'vitest';
import type {
  ActualizarSalidaInput,
  CheckInInput,
  Limpieza,
  OperacionHabitacion,
  Recepcion,
} from './recepcion.models';
import type { Habitacion } from './catalogo.models';
import {
  habitacion,
  limpieza,
  operacionHabitacion,
  recepcion,
} from '../../features/recepcion/testing/recepcion.fixtures';

/**
 * Los schemas de `admin-web.yaml` son la fuente de estos nombres. Si un campo
 * cambia de nombre, la lista deja de coincidir; si cambia de tipo, el fixture
 * deja de compilar contra el modelo.
 */

const RECEPCION_REQUIRED = [
  'idRecepcion',
  'idHotel',
  'idCliente',
  'idHabitacion',
  'numero',
  'categoriaNombre',
  'pisoNombre',
  'detalleHabitacion',
  'precioHabitacion',
  'nombre',
  'apellido',
  'tipoDocumento',
  'documento',
  'correo',
  'fechaEntrada',
  'fechaSalida',
  'fechaSalidaConfirmacion',
  'precioInicial',
  'adelanto',
  'medioPagoAdelanto',
  'precioRestante',
  'totalPagado',
  'costoPenalidad',
  'motivoPenalidad',
  'observacion',
  'estado',
  'estadoEstadia',
  'archivada',
  'vencida',
  'version',
  'closureId',
  'cleaningCycleId',
  'cierre',
] as const;

const HABITACION_REQUIRED = [
  'idHabitacion',
  'idHotel',
  'numero',
  'idPiso',
  'idCategoria',
  'detalle',
  'precio',
  'publicada',
  'estado',
  'idEstadoHabitacion',
  'estadoFisico',
  'ocupacion',
  'blockId',
  'endpoint',
  'version',
] as const;

const LIMPIEZA_REQUIRED = [
  'cleaningCycleId',
  'idHabitacion',
  'idRecepcion',
  'estado',
  'roomVersion',
  'version',
] as const;

const OPERACION_REQUIRED = ['blockId', 'idHabitacion', 'tipo', 'estado'] as const;

function faltantes(objeto: Record<string, unknown>, requeridos: readonly string[]): string[] {
  return requeridos.filter((clave) => !(clave in objeto));
}

describe('modelos del módulo de recepción', () => {
  it('Recepcion conserva los 33 campos requeridos del contrato', () => {
    const r: Recepcion = recepcion();
    expect(faltantes(r as unknown as Record<string, unknown>, RECEPCION_REQUIRED)).toEqual([]);
    expect(r.tipoDocumento).toBe('DNI');
    expect(r.medioPagoAdelanto).toBe('EFECTIVO');
    expect(r.estadoEstadia).toBe('ACTIVA');
    expect(typeof r.version).toBe('number');
    expect(r.cierre).toBeNull();
  });

  it('Habitacion conserva sus 15 campos requeridos y la proyección de ocupación', () => {
    const h: Habitacion = habitacion();
    expect(faltantes(h as unknown as Record<string, unknown>, HABITACION_REQUIRED)).toEqual([]);
    expect(h.estadoFisico.codigo).toBe('LISTA');
    expect(h.ocupacion?.estadoEstadia).toBe('LIBRE');
    expect(h.blockId).toBeNull();
    expect(h.endpoint).toBeNull();
  });

  it('Limpieza y OperacionHabitacion usan exactamente los campos del contrato', () => {
    const l: Limpieza = limpieza();
    const o: OperacionHabitacion = operacionHabitacion();
    expect(faltantes(l as unknown as Record<string, unknown>, LIMPIEZA_REQUIRED)).toEqual([]);
    expect(faltantes(o as unknown as Record<string, unknown>, OPERACION_REQUIRED)).toEqual([]);
    expect(l.estado).toBe('PENDIENTE');
    expect(o.estado).toBe('PREPARANDO');
  });

  it('CheckIn y edición de salida no llevan campos calculados', () => {
    const checkIn: CheckInInput = {
      idHotel: 1,
      idCliente: 100,
      idHabitacion: 101,
      fechaSalida: '2026-10-21',
      adelanto: 20,
      medioPago: 'EFECTIVO',
      observacion: 'Ingreso presencial',
    };
    const salida: ActualizarSalidaInput = {
      fechaSalida: '2026-10-22',
      observacion: 'Extiende una noche',
    };
    expect(Object.keys(checkIn).sort()).toEqual([
      'adelanto',
      'fechaSalida',
      'idCliente',
      'idHabitacion',
      'idHotel',
      'medioPago',
      'observacion',
    ]);
    expect(Object.keys(salida).sort()).toEqual(['fechaSalida', 'observacion']);
    expect('precioInicial' in checkIn).toBe(false);
    expect('fechaEntrada' in checkIn).toBe(false);
  });
});
