/**
 * Modelos del área de cliente: mismos nombres, campos y obligatoriedad que los
 * schemas de `docs/contracts/openapi/admin-web.yaml` (Recepcion, Cierre,
 * CalculoCierre, Producto, CarritoItem, Venta, VentaAlta, OperacionVenta,
 * Perfil y PasswordChange).
 *
 * Los cuerpos de alta no llevan precios, subtotales, totales ni IDs derivados:
 * el backend los calcula. El front solo muestra lo que llega.
 */

import type { Persona } from './catalogo.models';

export type { Persona };

export type TipoDocumento = 'DNI' | 'PASAPORTE' | 'CE';
export type MedioPago = 'EFECTIVO' | 'TARJETA' | 'OTRO';
export type EstadoEstadia = 'ACTIVA' | 'CERRANDO' | 'CERRADA';
export type EstadoVenta = 'PENDIENTE' | 'PAGADO';
export type EstadoOperacionVenta =
  | 'PENDIENTE'
  | 'CONFIRMADA'
  | 'RECHAZADA'
  | 'CANCELACION_PENDIENTE'
  | 'CANCELADA';
export type EstadoOperacion =
  | 'PREPARANDO'
  | 'ADMITIDA'
  | 'DEBITO_SOLICITADO'
  | 'TERMINAL';

export interface CalculoCierre {
  alojamientoTotal: number;
  saldoAlojamiento: number;
  consumosPagadosAntes: number;
  consumosPendientes: number;
  consumosTotal: number;
  cobroAhora: number;
  totalGeneral: number;
  expectedPendingTotal: number;
}

export interface Cierre {
  closureId: string;
  estado: 'VALIDANDO' | 'CONFIRMANDO' | 'COMPLETADO' | 'RECHAZADO';
  lastErrorCode: string | null;
  calculo: CalculoCierre | null;
  medioPago?: MedioPago | null;
}

export interface Recepcion {
  idRecepcion: number;
  idHotel: number;
  idCliente: number;
  idHabitacion: number;
  numero: string;
  categoriaNombre: string;
  pisoNombre: string;
  detalleHabitacion: string;
  precioHabitacion: number;
  nombre: string;
  apellido: string;
  tipoDocumento: TipoDocumento;
  documento: string;
  correo: string;
  fechaEntrada: string;
  fechaSalida: string;
  fechaSalidaConfirmacion: string | null;
  precioInicial: number;
  adelanto: number;
  medioPagoAdelanto: MedioPago | null;
  precioRestante: number;
  totalPagado: number;
  costoPenalidad: number;
  motivoPenalidad: string | null;
  observacion: string | null;
  estado: boolean;
  estadoEstadia: EstadoEstadia;
  archivada: boolean;
  vencida: boolean;
  version: number;
  closureId: string | null;
  cleaningCycleId: string | null;
  cierre: Cierre | null;
}

export interface Producto {
  idProducto: number;
  sku: string;
  nombre: string;
  detalle: string | null;
  imagenUrl: string | null;
  idHotel: number;
  precio: number;
  cantidad: number;
  umbralBajo: number;
  estado: boolean;
  fechaCreacion: string;
  version: number;
  productoVersion: number;
}

export interface CarritoItem {
  idCarrito: number;
  idRecepcion: number;
  idPersona: number;
  idHotel: number;
  idProducto: number;
  nombreProducto: string;
  precioUnitario: number;
  imagenUrl: string | null;
  cantidad: number;
  fechaRegistro: string;
  version: number;
}

export interface VentaDetalle {
  idDetalleVenta: number;
  idProducto: number;
  nombreProducto: string;
  /** Ver `ventas.models.ts`: opcional hasta que el contrato lo publique. */
  imagenUrl?: string | null;
  cantidad: number;
  precioUnitario: number;
  subTotal: number;
}

export interface Venta {
  idVenta: number;
  idRecepcion: number;
  idCliente: number;
  idHotel: number;
  total: number;
  estado: EstadoVenta;
  estadoOperacion: EstadoOperacionVenta;
  operationId: string;
  medioPago: MedioPago | null;
  version: number;
  fechaCreacion: string;
  paidAt: string | null;
  detalles: VentaDetalle[];
}

export interface VentaDetalleAlta {
  idProducto: number;
  cantidad: number;
}

export interface VentaAlta {
  idRecepcion: number;
  estado?: EstadoVenta;
  medioPago?: MedioPago | null;
  detalles: VentaDetalleAlta[];
}

export interface OperacionVenta {
  operationId: string;
  idVenta: number;
  tipo: 'VENTA' | 'PAGO' | 'ANULACION';
  estado: EstadoOperacion;
  estadoOperacion: EstadoOperacionVenta;
}

export interface Perfil {
  nombre?: string;
  apellido?: string;
  fotoUrl?: string | null;
}

export interface PasswordChange {
  claveActual: string;
  claveNueva: string;
}

/** Respuesta terminal de la creación de venta: 201 confirmada o 202 pendiente. */
export type ResultadoVenta =
  | { kind: 'confirmada'; venta: Venta }
  | { kind: 'pendiente'; venta: Venta; location: string; retryAfterMs: number };

/** Evento de cada sondeo de la operación en curso. */
export interface AvanceOperacion {
  venta: Venta | null;
  operacion: OperacionVenta | null;
  actualizadoEn: Date;
}
