/** Modelos públicos del contrato `public-web.yaml`. Solo estas claves. */
export interface HotelPublico {
  idHotel: number;
  slug: string;
  nombre: string;
  descripcion: string;
  direccion: string;
  ciudad: string;
  telefono: string;
  correoContacto: string;
}

export interface ImagenPublica {
  url: string;
  alt: string;
}

export interface CategoriaPublica {
  idCategoria: number;
  descripcion: string;
  detalle: string;
  capacidad: number;
  precioDesde: number;
  moneda: 'PEN';
  imagenes: ImagenPublica[];
}

export interface MetaPage {
  page: number;
  size: number;
  totalElements: number;
  correlationId?: string;
}

export interface MetaSingle {
  correlationId?: string;
}

export interface ApiEnvelope<T, M> {
  success: true;
  message: string;
  data: T;
  meta: M;
}

export type ApiList<T> = ApiEnvelope<T[], MetaPage>;
export type ApiSingle<T> = ApiEnvelope<T, MetaSingle>;

export interface ApiErrorBody {
  success: false;
  message: string;
  data: null;
  error: {
    code: string;
    fields: Record<string, string>;
  };
  meta?: MetaSingle;
}
