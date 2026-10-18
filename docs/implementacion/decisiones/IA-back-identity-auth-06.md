# IA-back-identity-auth-06 — Login responde 401 sin `Idempotency-Key` y sin cuerpo completo

- **Plan:** `back-identity-auth`
- **Paso:** 5 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-18

## Qué se encontró

`docs/contracts/openapi/identity.yaml` declara para `POST /api/auth/login` exactamente tres
respuestas: `200`, `401` (`INVALID_CREDENTIALS`) y `429` (`RateLimited`). No declara `400`.
`docs/especificacion/microservicios/identity/api.md` § DTO de entrada dice «Login correo y clave
requeridos», pero no dice con qué código responde una petición sin uno de los dos.

Añadir un `400 VALIDATION_ERROR` a esa ruta sería cambiar el conjunto de respuestas de un contrato ya
publicado, y la norma de nivel 3 lo prohíbe.

## Qué se decidió

Un login sin `correo`, sin `clave` o con cualquiera de los dos vacío responde el mismo
`401 INVALID_CREDENTIALS` que una credencial incorrecta, con el mismo mensaje. No hay ninguna
diferencia observable para quien llama entre «el correo no existe», «la clave no es esa» y «no
mandaste el campo», que es justo lo que quiere el 401 genérico de la spec.

## Por qué

Jerarquía de [`../README.md`](../README.md) §1 aplicada a la zona de contratos: el OpenAPI publicado
es la fuente y fija el conjunto de respuestas; `docs/especificacion/**` está bloqueada para este
plan. Un 400 distinto del que declara el contrato habría sido una ampliación que ni siquiera es
aditiva.

## Alternativas descartadas

- **Responder 400 con `VALIDATION_ERROR`**: no está en el contrato de la ruta; obligaría a tocar
  `identity.yaml` (permitido) pero a cambiar el significado de una respuesta ya publicada.
- **Dejar que la deserialización devuelva `null` y fallar con 500**: peor de todo; 500 nunca es una
  respuesta de contrato para esta ruta.
- **Diferenciar el mensaje**: crearía un oráculo de si el correo existe, que la spec prohíbe.

## A quién afecta

`services/identity-service/src/main/java/pe/hotel/identity/auth/AuthService.java#login` y su prueba
`loginConClaveIncorrectaYConCorreoInexistenteDanElMismo401`. No se toca `identity.yaml`.