import type { Persona } from '../../../core/models/catalogo.models';
import type {
  CarritoItem,
  MovimientoAjuste,
  MovimientoVenta,
  OperacionVenta,
  Producto,
  ProductoAlta,
  Venta,
  VentaDetalle,
} from '../../../core/models/ventas.models';

export interface AuthDataFixture {
  idPersona: number;
  nombre: string;
  apellido: string;
  correo: string;
  tipoPersona: string;
  token: string;
  expiresIn: number;
  sedes: number[];
}

function tokenFalso(sub: number): string {
  const payload = btoa(JSON.stringify({ sub, tipoPersona: 'Demo' }));
  return `header.${payload}.firma`;
}

export function authData(tipoPersona: string, sedes: number[] = []): AuthDataFixture {
  const idPersona = tipoPersona === 'Cliente' ? 100 : tipoPersona === 'Empleado' ? 3 : 1;
  return {
    idPersona,
    nombre: tipoPersona,
    apellido: 'Demo',
    correo: `${tipoPersona.toLowerCase()}@hotel.test`,
    tipoPersona,
    token: tokenFalso(idPersona),
    expiresIn: 900,
    sedes,
  };
}

export const PERSONA_ADMIN: Persona = {
  idPersona: 1,
  tipoDocumento: 'DNI',
  documento: '44555666',
  nombre: 'Ana',
  apellido: 'Admin',
  correo: 'admin@hotel.test',
  fotoUrl: null,
  idTipoPersona: 1,
  tipoPersona: 'Administrador',
  sedes: [],
  estado: true,
  fechaCreacion: '2026-10-18T00:00:00Z',
  version: 1,
};

export const PERSONA_EMPLEADO: Persona = {
  ...PERSONA_ADMIN,
  idPersona: 3,
  documento: '44555667',
  nombre: 'Eva',
  apellido: 'Empleada',
  correo: 'empleada@hotel.test',
  idTipoPersona: 2,
  tipoPersona: 'Empleado',
  sedes: [1],
};

export const PERSONA_CLIENTE: Persona = {
  ...PERSONA_ADMIN,
  idPersona: 100,
  documento: '70000001',
  nombre: 'Carla',
  apellido: 'Cliente',
  correo: 'carla.cliente@hotel.test',
  idTipoPersona: 3,
  tipoPersona: 'Cliente',
  sedes: [],
};

export const personaAdmin = PERSONA_ADMIN;
export const personaCliente = PERSONA_CLIENTE;

export function producto(overrides: Partial<Producto> = {}): Producto {
  return {
    idProducto: 1,
    sku: 'AGUA-500',
    nombre: 'Agua 500ml',
    detalle: 'Botella de agua de 500 ml',
    imagenUrl: '/assets/productos/agua.png',
    idHotel: 1,
    precio: 3.5,
    cantidad: 20,
    umbralBajo: 5,
    estado: true,
    fechaCreacion: '2026-10-18T10:00:00Z',
    version: 3,
    productoVersion: 2,
    ...overrides,
  };
}

export function productoAlta(overrides: Partial<ProductoAlta> = {}): ProductoAlta {
  return {
    sku: 'AGUA-500',
    nombre: 'Agua 500ml',
    detalle: 'Botella de agua de 500 ml',
    imagenUrl: '/assets/productos/agua.png',
    idHotel: 1,
    precio: 3.5,
    cantidadInicial: 20,
    umbralBajo: 5,
    ...overrides,
  };
}

export function movimientoVenta(overrides: Partial<MovimientoVenta> = {}): MovimientoVenta {
  return {
    tipo: 'VENTA',
    idVenta: 5001,
    idHotel: 1,
    estado: 'DESCONTADO',
    cantidad: 2,
    precioUnitario: 3.5,
    createdAt: '2026-10-20T12:00:00Z',
    updatedAt: '2026-10-20T12:00:05Z',
    ...overrides,
  };
}

export function movimientoAjuste(overrides: Partial<MovimientoAjuste> = {}): MovimientoAjuste {
  return {
    tipo: 'AJUSTE',
    ajusteId: '40000000-0000-4000-8000-000000000004',
    idHotel: 1,
    delta: -3,
    motivo: 'Merma de inventario',
    createdAt: '2026-10-20T13:00:00Z',
    ...overrides,
  };
}

export function ventaDetalle(overrides: Partial<VentaDetalle> = {}): VentaDetalle {
  return {
    idDetalleVenta: 1,
    idProducto: 1,
    nombreProducto: 'Agua 500ml',
    cantidad: 2,
    precioUnitario: 3.5,
    subTotal: 7,
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
    operationId: '55555555-5555-4555-8555-555555555555',
    medioPago: null,
    version: 2,
    fechaCreacion: '2026-10-20T15:05:00Z',
    paidAt: null,
    detalles: [ventaDetalle()],
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
    fechaRegistro: '2026-10-20T12:30:00Z',
    version: 1,
    ...overrides,
  };
}

export function operacionVenta(overrides: Partial<OperacionVenta> = {}): OperacionVenta {
  return {
    operationId: '55555555-5555-4555-8555-555555555555',
    idVenta: 5001,
    tipo: 'VENTA',
    estado: 'PREPARANDO',
    estadoOperacion: 'PENDIENTE',
    ...overrides,
  };
}

export function envelopeSingle<T>(data: T, correlationId = 'cid-1') {
  return { success: true, message: 'OK', data, meta: { correlationId } };
}

export function envelopePage<T>(
  data: T[],
  page = 0,
  size = 20,
  totalElements = data.length,
  correlationId = 'cid-page'
) {
  return { success: true, message: 'OK', data, meta: { page, size, totalElements, correlationId } };
}

export function apiError(
  code: string,
  fields: Record<string, string> = {},
  correlationId = 'cid-error'
) {
  return {
    success: false,
    message: 'Error de negocio',
    data: null,
    error: { code, fields },
    meta: { correlationId },
  };
}
