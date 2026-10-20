import type { Persona } from '../../../core/models/catalogo.models';

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
