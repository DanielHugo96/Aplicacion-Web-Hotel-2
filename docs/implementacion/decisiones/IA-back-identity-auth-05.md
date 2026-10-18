# IA-back-identity-auth-05 — Fallo de arranque cuando falta la clave RS256 o la clave HMAC

- **Plan:** `back-identity-auth`
- **Paso:** 3 y 8 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-18

## Qué se encontró

`docs/especificacion/03-contrato-comun.md` § Servicio a servicio dice «Secretos y clave privada fuera
de Git» y `configuracion.md` deja `IDENTITY_SIGNING_KEY_FILE` sin valor por defecto. La spec **no
dice NADA** de qué tiene que pasar si la variable no está definida.

Las pruebas de contexto de la CI tampoco levantan PostgreSQL ni tienen archivo de clave, así que
ninguna de las dos claves puede ser obligatoria sin dejar el contexto sin arrancar.

## Qué se decidió

- Si `IDENTITY_SIGNING_KEY_FILE` está vacía: en cualquier perfil que empiece por `prod` el arranque
  **falla**; en el resto se genera un par RSA-2048 efímero en memoria y se deja un aviso
  (`signing_key_efimera`) con el `kid`.
- Si `IDENTITY_IDEMPOTENCY_HMAC_KEY` y `SERVICE_CLIENT_SECRET` están ambas vacías: mismo criterio,
  con clave HMAC aleatoria de 32 bytes por arranque y su aviso.

## Por qué

Con dos instancias y clave efímera cada una firma con una clave distinta y los tokens de la otra no
validan: el fallo es silencioso y difícil de diagnosticar. Fallar al arrancar convierte un despliegue
mal configurado en un problema inmediato y visible; el aviso cubre el caso dev/demo, donde hay una
sola instancia y donde las pruebas necesitan una clave que no está en el repositorio.

## Alternativas descartadas

- **Exigir siempre las dos variables**: las pruebas de contexto (`IdentityServiceApplicationTests`,
  `SecurityConventionTests`) no podrían arrancar sin un archivo de clave en el repositorio, y eso es
  justamente una clave privada en Git.
- **Generar efímero siempre, sin mirar el perfil**: en producción escondería una configuración
  incompleta detrás de un servicio que parece sano.
- **Generar y escribir la clave en un archivo dentro del repo**: convertiría un directorio de
  trabajo en un almacén de secretos sin que nadie lo pidiera.

## A quién afecta

`services/identity-service/src/main/java/pe/hotel/identity/auth/SigningKeys.java` e
`IdentityIdempotencyCustomizer.java`. No toca contratos.