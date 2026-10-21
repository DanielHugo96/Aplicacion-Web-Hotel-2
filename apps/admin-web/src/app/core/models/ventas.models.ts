/**
 * Modelos del módulo de productos y ventas del panel: mismos nombres, campos y
 * obligatoriedad que los schemas de `docs/contracts/openapi/admin-web.yaml`
 * (Producto, ProductoAlta, ProductoUpdate, AjusteResultado, Movimiento como
 * unión VENTA/AJUSTE, Venta, VentaAlta, VentaDetalle, OperacionVenta y
 * CarritoItem).
 *
 * Los cuerpos de alta y edición son JSON cerrado: no llevan precios
 * calculados, subtotales, totales ni IDs derivados; el backend los calcula. El
 * front solo muestra lo que llega.
 */

export type MedioPago = 'EFECTIVO' | 'TARJETA' | 'OTRO';
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
export type EstadoMovimientoVenta = 'DESCONTADO' | 'RECHAZADO' | 'RESTITUIDO';

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

export interface ProductoAlta {
  sku: string;
  nombre: string;
  detalle?: string;
  imagenUrl?: string;
  idHotel: number;
  precio: number;
  cantidadInicial: number;
  umbralBajo: number;
}

export interface ProductoUpdate {
  nombre?: string;
  detalle?: string;
  imagenUrl?: string;
  precio?: number;
  umbralBajo?: number;
  estado?: boolean;
  productoVersion: number;
}

export interface ProductoAsignacionSede {
  idHotel: number;
  precio: number;
  cantidadInicial: number;
  umbralBajo: number;
}

export interface AjusteStockInput {
  idHotel: number;
  delta: number;
  motivo: string;
}

export interface AjusteResultado {
  ajusteId: string;
  cantidad: number;
  version: number;
}

export interface MovimientoVenta {
  tipo: 'VENTA';
  idVenta: number;
  idHotel: number;
  estado: EstadoMovimientoVenta;
  cantidad: number;
  precioUnitario: number;
  createdAt: string;
  updatedAt: string;
}

export interface MovimientoAjuste {
  tipo: 'AJUSTE';
  ajusteId: string;
  idHotel: number;
  delta: number;
  motivo: string;
  createdAt: string;
}

/** Unión discriminada por `tipo`, tal como la publica `Movimiento`. */
export type Movimiento = MovimientoVenta | MovimientoAjuste;

export interface VentaDetalle {
  idDetalleVenta: number;
  idProducto: number;
  nombreProducto: string;
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

export interface OperacionVenta {
  operationId: string;
  idVenta: number;
  tipo: 'VENTA' | 'PAGO' | 'ANULACION';
  estado: EstadoOperacion;
  estadoOperacion: EstadoOperacionVenta;
}

/** Respuesta de `POST /api/venta`: 201 confirmada o 202 con operación en curso. */
export type ResultadoCreacionVenta =
  | { kind: 'confirmada'; venta: Venta }
  | { kind: 'pendiente'; venta: Venta; location: string; retryAfterMs: number };

/** Respuesta de pago y anulación: 200 terminal o 202 con operación en curso. */
export type ResultadoOperacionVenta =
  | { kind: 'confirmada'; venta: Venta }
  | { kind: 'pendiente'; venta: Venta; location: string; retryAfterMs: number };

/** Evento de cada sondeo de la operación en curso. */
export interface AvanceOperacion {
  venta: Venta | null;
  operacion: OperacionVenta | null;
  actualizadoEn: Date;
}

/** Cambio de cantidad en el carrito = eliminar la línea y agregarla. */
export interface LineaVentaNueva {
  idProducto: number;
  cantidad: number;
}

export type FiltroEstadoProducto = 'activos' | 'inactivos' | 'todos';
