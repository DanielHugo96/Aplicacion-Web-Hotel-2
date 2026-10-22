/**
 * Modelos del catálogo del panel: mismos campos y obligatoriedad que los
 * schemas de `docs/contracts/openapi/admin-web.yaml` (Persona, TipoPersona,
 * Hotel, Piso, Categoria, Habitacion, EstadoFisico e ImagenHabitacion).
 */

export type TipoDocumento = 'DNI' | 'PASAPORTE' | 'CE';
export type RolCanonico = 'Administrador' | 'Empleado' | 'Cliente';
export type CodigoEstadoFisico = 'LISTA' | 'EN_LIMPIEZA' | 'MANTENIMIENTO';
export type EstadoOcupacion = 'LIBRE' | 'OCUPADA' | 'DESCONOCIDA';

export interface ApiMetaSingle {
  correlationId: string;
}

export interface ApiMetaPage extends ApiMetaSingle {
  page: number;
  size: number;
  totalElements: number;
}

export interface Envelope<T, M = ApiMetaSingle> {
  success: boolean;
  message: string;
  data: T;
  meta: M;
}

export interface Paged<T> {
  data: T[];
  page: number;
  size: number;
  totalElements: number;
  correlationId: string;
}

export interface ETagged<T> {
  data: T;
  etag: string | null;
  correlationId: string;
}

export interface Persona {
  idPersona: number;
  tipoDocumento: TipoDocumento;
  documento: string;
  nombre: string;
  apellido: string;
  correo: string;
  fotoUrl: string | null;
  idTipoPersona: number;
  tipoPersona: RolCanonico;
  sedes: number[];
  estado: boolean;
  fechaCreacion: string;
  version: number;
}

export interface PersonaAlta {
  tipoDocumento: TipoDocumento;
  documento: string;
  nombre: string;
  apellido: string;
  correo: string;
  clave?: string;
  idTipoPersona: number;
  sedes: number[];
}

export interface PersonaUpdate {
  nombre?: string;
  apellido?: string;
  correo?: string;
  fotoUrl?: string | null;
  idTipoPersona?: number;
  sedes?: number[];
  estado?: boolean;
}

export interface TipoPersona {
  idTipoPersona: number;
  descripcion: string;
  codigo: 'ADMIN' | 'EMPLEADO' | 'CLIENTE';
  estado: boolean;
  version: number;
}

export interface Hotel {
  idHotel: number;
  slug: string;
  nombre: string;
  descripcion: string;
  direccion: string;
  ciudad: string;
  telefono: string;
  correoContacto: string;
  /** Opcional en el contrato: `null` cuando la sede no tiene foto. */
  imagenUrl?: string | null;
  /** Obligatorio si hay `imagenUrl` (≤ 150). */
  imagenAlt?: string | null;
  estado: boolean;
  publicado: boolean;
  version: number;
}

export interface HotelInput {
  slug: string;
  nombre: string;
  descripcion: string;
  direccion: string;
  ciudad: string;
  telefono: string;
  correoContacto: string;
  imagenUrl?: string | null;
  imagenAlt?: string | null;
  publicado: boolean;
  estado: boolean;
}

export interface Piso {
  idPiso: number;
  idHotel: number;
  descripcion: string;
  estado: boolean;
  version: number;
}

export interface PisoInput {
  idHotel: number;
  descripcion: string;
}

export interface PisoUpdate {
  descripcion: string;
  estado: boolean;
}

export interface Categoria {
  idCategoria: number;
  descripcion: string;
  detalle: string;
  capacidad: number;
  estado: boolean;
  version: number;
}

export interface CategoriaInput {
  descripcion: string;
  detalle: string;
  capacidad: number;
  estado: boolean;
}

export interface ImagenHabitacion {
  url: string;
  alt: string;
  orden: number;
}

export interface HabitacionInput {
  idHotel: number;
  numero: string;
  idPiso: number;
  idCategoria: number;
  detalle: string;
  precio: number;
  publicada: boolean;
  urlsImagenes: ImagenHabitacion[];
}

export interface HabitacionUpdate {
  idPiso?: number;
  idCategoria?: number;
  detalle?: string;
  precio?: number;
  publicada?: boolean;
  urlsImagenes?: ImagenHabitacion[];
}

export interface Habitacion {
  idHabitacion: number;
  idHotel: number;
  numero: string;
  idPiso: number;
  pisoNombre?: string;
  idCategoria: number;
  categoriaNombre?: string;
  detalle: string;
  precio: number;
  publicada: boolean;
  urlsImagenes?: ImagenHabitacion[];
  estado: boolean;
  idEstadoHabitacion: number;
  estadoFisico: EstadoFisico;
  ocupacion: {
    estadoEstadia: EstadoOcupacion;
    idRecepcion: number | null;
    updatedAt: string;
  } | null;
  blockId: string | null;
  endpoint: string | null;
  version: number;
}

export interface EstadoFisico {
  idEstadoHabitacion: number;
  codigo: CodigoEstadoFisico;
  descripcion: string;
  estado: boolean;
  version: number;
}
