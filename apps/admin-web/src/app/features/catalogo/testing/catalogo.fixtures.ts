import type { Hotel } from '../../../core/models/catalogo.models';

/**
 * Sede de prueba con los campos del contrato `Hotel` (`admin-web.yaml`),
 * incluidos `imagenUrl`/`imagenAlt` opcionales. Datos de la cadena de la
 * presentación, nunca «Hotel Demo».
 */
export function hotel(overrides: Partial<Hotel> = {}): Hotel {
  return {
    idHotel: 1,
    slug: 'lima-centro',
    nombre: 'Casa Sillar Lima Centro',
    descripcion: 'Casona restaurada a dos cuadras de la Plaza Mayor, con patio de sillar.',
    direccion: 'Jr. de la Unión 540, Cercado de Lima',
    ciudad: 'Lima',
    telefono: '+51 1 428 6400',
    correoContacto: 'contacto@casasillar.pe',
    imagenUrl: '/assets/sedes/lima-centro-fachada.jpg',
    imagenAlt: 'Fachada de piedra de la sede de Lima Centro al atardecer',
    estado: true,
    publicado: true,
    version: 1,
    ...overrides,
  };
}
