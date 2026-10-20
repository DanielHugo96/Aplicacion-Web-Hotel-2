import type { Habitacion, Persona } from '../../../core/models/catalogo.models';
import type {
  Limpieza,
  OperacionHabitacion,
  Recepcion,
} from '../../../core/models/recepcion.models';

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

function tokenFalso(sub: number): string {
  const payload = btoa(JSON.stringify({ sub, tipoPersona: 'Demo' }));
  return `header.${payload}.firma`;
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

export function persona(overrides: Partial<Persona> = {}): Persona {
  return { ...PERSONA_CLIENTE, ...overrides };
}

export const personaAdmin = PERSONA_ADMIN;
export const personaCliente = PERSONA_CLIENTE;

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
    nombre: 'Carla',
    apellido: 'Cliente',
    tipoDocumento: 'DNI',
    documento: '70000001',
    correo: 'carla.cliente@hotel.test',
    fechaEntrada: '2026-10-19',
    fechaSalida: '2026-10-21',
    fechaSalidaConfirmacion: null,
    precioInicial: 140,
    adelanto: 20,
    medioPagoAdelanto: 'EFECTIVO',
    precioRestante: 120,
    totalPagado: 20,
    costoPenalidad: 0,
    motivoPenalidad: null,
    observacion: 'Ingreso presencial',
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

export function habitacion(overrides: Partial<Habitacion> = {}): Habitacion {
  return {
    idHabitacion: 101,
    idHotel: 1,
    numero: '101',
    idPiso: 11,
    pisoNombre: 'Primer piso',
    idCategoria: 1,
    categoriaNombre: 'Individual',
    detalle: 'Habitación individual de demostración',
    precio: 70,
    publicada: true,
    urlsImagenes: [],
    estado: true,
    idEstadoHabitacion: 1,
    estadoFisico: {
      idEstadoHabitacion: 1,
      codigo: 'LISTA',
      descripcion: 'Lista',
      estado: true,
      version: 1,
    },
    ocupacion: {
      estadoEstadia: 'LIBRE',
      idRecepcion: null,
      updatedAt: '2026-10-20T12:00:00Z',
    },
    blockId: null,
    endpoint: null,
    version: 1,
    ...overrides,
  };
}

export function limpieza(overrides: Partial<Limpieza> = {}): Limpieza {
  return {
    cleaningCycleId: '11111111-1111-4111-8111-111111111111',
    idHabitacion: 101,
    idRecepcion: 1001,
    estado: 'PENDIENTE',
    roomVersion: 4,
    version: 1,
    ...overrides,
  };
}

export function operacionHabitacion(
  overrides: Partial<OperacionHabitacion> = {}
): OperacionHabitacion {
  return {
    blockId: '33333333-3333-4333-8333-333333333333',
    idHabitacion: 101,
    tipo: 'MANTENIMIENTO',
    estado: 'PREPARANDO',
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

export function metaPage(page: number, size: number, totalElements: number) {
  return { page, size, totalElements, correlationId: 'cid-page' };
}
