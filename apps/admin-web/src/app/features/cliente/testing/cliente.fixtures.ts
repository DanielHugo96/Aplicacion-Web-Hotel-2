import {
  CarritoItem,
  OperacionVenta,
  Producto,
  Recepcion,
  ResultadoVenta,
  Venta,
} from '../../../core/models/cliente.models';
import { Persona } from '../../../core/models/catalogo.models';

export const PERSONA_CLIENTE: Persona = {
  idPersona: 100,
  tipoDocumento: 'DNI',
  documento: '90000100',
  nombre: 'Cliente',
  apellido: 'Uno',
  correo: 'cliente.uno@hotel.test',
  fotoUrl: null,
  idTipoPersona: 3,
  tipoPersona: 'Cliente',
  sedes: [],
  estado: true,
  fechaCreacion: '2026-09-01T10:00:00Z',
  version: 1,
};

export function tokenConSub(sub: number): string {
  const payload = btoa(JSON.stringify({ sub: String(sub), tipoPersona: 'Cliente' }));
  return `cabecera.${payload}.firma`;
}

export function recepcion(overrides: Partial<Recepcion> = {}): Recepcion {
  return {
    idRecepcion: 1001,
    idHotel: 1,
    idCliente: 100,
    idHabitacion: 101,
    numero: '101',
    categoriaNombre: 'Individual',
    pisoNombre: 'Primer piso',
    detalleHabitacion: 'Habitación individual de demostración',
    precioHabitacion: 70,
    nombre: 'Cliente',
    apellido: 'Uno',
    tipoDocumento: 'DNI',
    documento: '90000100',
    correo: 'cliente.uno@hotel.test',
    fechaEntrada: '2026-10-04',
    fechaSalida: '2026-10-06',
    fechaSalidaConfirmacion: null,
    precioInicial: 140,
    adelanto: 20,
    medioPagoAdelanto: 'EFECTIVO',
    precioRestante: 120,
    totalPagado: 20,
    costoPenalidad: 0,
    motivoPenalidad: null,
    observacion: null,
    estado: true,
    estadoEstadia: 'ACTIVA',
    archivada: false,
    vencida: false,
    version: 1,
    closureId: null,
    cleaningCycleId: null,
    cierre: null,
    ...overrides,
  };
}

export function producto(overrides: Partial<Producto> = {}): Producto {
  return {
    idProducto: 1,
    sku: 'AGUA-500',
    nombre: 'Agua 500ml',
    detalle: 'Botella de agua de demostración',
    imagenUrl: null,
    idHotel: 1,
    precio: 3.5,
    cantidad: 20,
    umbralBajo: 5,
    estado: true,
    fechaCreacion: '2026-09-01T10:00:00Z',
    version: 1,
    productoVersion: 1,
    ...overrides,
  };
}

export function carritoItem(overrides: Partial<CarritoItem> = {}): CarritoItem {
  return {
    idCarrito: 9001,
    idRecepcion: 1001,
    idPersona: 100,
    idHotel: 1,
    idProducto: 1,
    nombreProducto: 'Agua 500ml',
    precioUnitario: 3.5,
    imagenUrl: null,
    cantidad: 2,
    fechaRegistro: '2026-10-20T10:00:00Z',
    version: 1,
    ...overrides,
  };
}

export function venta(overrides: Partial<Venta> = {}): Venta {
  return {
    idVenta: 5001,
    idRecepcion: 1001,
    idCliente: 100,
    idHotel: 1,
    total: 7,
    estado: 'PENDIENTE',
    estadoOperacion: 'CONFIRMADA',
    operationId: '20000000-0000-4000-8000-000000000001',
    medioPago: null,
    version: 1,
    fechaCreacion: '2026-10-20T15:30:00Z',
    paidAt: null,
    detalles: [
      {
        idDetalleVenta: 1,
        idProducto: 1,
        nombreProducto: 'Agua 500ml',
        cantidad: 2,
        precioUnitario: 3.5,
        subTotal: 7,
      },
    ],
    ...overrides,
  };
}

export function operacion(overrides: Partial<OperacionVenta> = {}): OperacionVenta {
  return {
    operationId: '20000000-0000-4000-8000-000000000001',
    idVenta: 5001,
    tipo: 'VENTA',
    estado: 'TERMINAL',
    estadoOperacion: 'CONFIRMADA',
    ...overrides,
  };
}

export function envelopeSingle<T>(data: T, correlationId = 'corr-1') {
  return { success: true, message: 'OK', data, meta: { correlationId } };
}

export function envelopePage<T>(
  data: T[],
  page = 0,
  size = 20,
  totalElements = data.length,
  correlationId = 'corr-1'
) {
  return {
    success: true,
    message: 'OK',
    data,
    meta: { page, size, totalElements, correlationId },
  };
}

export function error404(correlationId = 'corr-404') {
  return {
    success: false,
    message: 'No encontramos lo que buscas.',
    data: null,
    error: { code: 'RESOURCE_NOT_FOUND', fields: {} },
    meta: { correlationId },
  };
}

export function error409(code: string, fields: Record<string, string>, correlationId = 'corr-409') {
  return {
    success: false,
    message: 'Conflicto',
    data: null,
    error: { code, fields },
    meta: { correlationId },
  };
}

export function resultadoConfirmado(v: Venta = venta()): ResultadoVenta {
  return { kind: 'confirmada', venta: v };
}
