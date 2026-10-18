package pe.hotel.identity.platform;

/**
 * Permite a un servicio ajustar el actor y el hash de la idempotencia de una ruta.
 *
 * <p>La convención por defecto usa el {@code sub} del JWT (o {@code anonymous} en rutas públicas) y un
 * SHA-256 del request canónico. Hay rutas donde eso no sirve: el registro público no tiene
 * identidad y su actor es el hash del correo normalizado, y las rutas con credenciales necesitan
 * HMAC para que el {@code request_hash} no sea atacable por fuerza bruta.
 *
 * <p>Devolver {@code null} o vacío deja el comportamiento por defecto.
 * Ver docs/especificacion/03-contrato-comun.md § Headers.
 */
public interface IdempotencyCustomizer {

  /** Actor de la clave única {@code (actor, método, plantillaRuta, key)}. */
  default String actorFor(CachedBodyHttpServletRequest request) {
    return null;
  }

  /** Hash del request; con credenciales debe ser un HMAC, nunca la clave en claro. */
  default String hashFor(CachedBodyHttpServletRequest request) {
    return null;
  }
}