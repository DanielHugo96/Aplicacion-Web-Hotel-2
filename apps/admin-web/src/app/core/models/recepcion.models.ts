/**
 * Modelos del módulo de recepción con los nombres, campos y obligatoriedad de
 * los schemas de `docs/contracts/openapi/admin-web.yaml`: `Recepcion`,
 * `Habitacion`, `Limpieza`, `LimpiezaResultado`, `OperacionHabitacion`,
 * `OperationRef`, `CheckIn` y el cuerpo de `PUT /api/recepcion/actualizar/{id}`.
 *
 * `Recepcion`, `Cierre`, `CalculoCierre`, `MedioPago` y `EstadoEstadia` viven
 * en `cliente.models.ts` porque el área de cliente (plan cerrado) ya los tipa
 * contra el mismo contrato; aquí se reexportan para no duplicar el schema.
 */

import type { MedioPago } from './cliente.models';

export type {
  CalculoCierre,
  Cierre,
  EstadoEstadia,
  MedioPago,
  Recepcion,
} from './cliente.models';

export type EstadoLimpieza = 'PENDIENTE' | 'COMPLETADA';
export type EstadoOperacionHabitacion = 'PREPARANDO' | 'APLICADA' | 'LIBERADA' | 'RECHAZADA';
export type TipoOperacionHabitacion = 'MANTENIMIENTO' | 'BAJA';

/** Cuerpo de `POST /api/recepcion/registrar` (CheckIn). */
export interface CheckInInput {
  idHotel: number;
  idCliente: number;
  idHabitacion: number;
  fechaSalida: string;
  adelanto: number;
  medioPago?: MedioPago;
  observacion?: string;
}

/** Cuerpo de `PUT /api/recepcion/actualizar/{id}`. */
export interface ActualizarSalidaInput {
  fechaSalida: string;
  observacion?: string;
}

/** Tarea de limpieza (`Limpieza`). `version` es el `If-Match` de la tarea. */
export interface Limpieza {
  cleaningCycleId: string;
  idHabitacion: number;
  idRecepcion: number;
  estado: EstadoLimpieza;
  roomVersion: number;
  version: number;
}

export interface LimpiezaResultado {
  limpieza: Limpieza;
  habitacion: import('./catalogo.models').Habitacion;
}

/** Operación administrativa vigilada por `blockId`. */
export interface OperacionHabitacion {
  blockId: string;
  idHabitacion: number;
  tipo: TipoOperacionHabitacion;
  estado: EstadoOperacionHabitacion;
}

/** `OperationRef` de un 202: al menos uno de los identificadores está presente. */
export interface OperationRef {
  operationId?: string | null;
  closureId?: string | null;
  blockId?: string | null;
  estadoOperacion?: string | null;
}

/** Resultado de una operación de habitación: 200 terminal o 202 pendiente. */
export type ResultadoOperacionHabitacion =
  | { kind: 'confirmada'; habitacion: import('./catalogo.models').Habitacion }
  | {
      kind: 'pendiente';
      operacion: OperationRef;
      location: string;
      retryAfterMs: number;
    };

/** Evento de cada sondeo del `blockId` en curso. */
export interface AvanceOperacionHabitacion {
  operacion: OperacionHabitacion;
  actualizadoEn: Date;
}
