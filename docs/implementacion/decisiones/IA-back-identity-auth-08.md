# IA-back-identity-auth-08 — Detalles menores que la spec deja abiertos en perfil y clave

- **Plan:** `back-identity-auth`
- **Paso:** 2, 11 y 17 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-18

## Qué se encontró

Cuatro huecos menores, todos en detalles que la spec no fija:

1. **Cambiar la clave y `version`.** `docs/especificacion/03-contrato-comun.md` § Persistencia
   transversal dice que «versiones inician en 1» y que `V` protege el recurso, pero no dice si un
   cambio de contraseña debe incrementar `version` de la persona.
2. **`fotoUrl`.** 03-contrato-comun.md § Límites de campos comunes fija «≤ 500, https allowlist o
   `/assets/`», y `configuracion.md` solo declara `IMAGE_URL_ALLOWLIST` para `hotel-service`:
   identity no tiene variable de allowlist.
3. **Perfil de las pruebas.** `services/identity-service/src/main/resources/application.yml` deja
   `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:dev}`, así que las pruebas de contexto heredan
   `dev` y con él los seeds de demo, que necesitan PostgreSQL.
4. **Mensaje de 400 por JSON cerrado.** El contrato declara `VALIDATION_ERROR` sin fijar el texto de
   `error.fields` para el caso «el cuerpo tiene campos que el DTO no declara».

## Qué se decidió

1. Cambiar la clave **no** incrementa `version`: el ETag describe nombre, apellido y foto, y un
   cambio de contraseña no invalida lo que el usuario tiene en pantalla. Si lo hiciera, un `PUT
   /api/auth/me` posterior a un cambio de clave fallaría con 412 sin motivo aparente.
2. `fotoUrl` se valida en código con la regla de la spec (`https://` o `/assets/`, hasta 500
   caracteres), sin inventar una variable de entorno que `configuracion.md` no declara.
3. Las pruebas de contexto usan `@ActiveProfiles("test")`. Los seeds quedan en `@Profile({"dev",
   "demo"})`, así que no corren ni en pruebas ni fuera de dev/demo.
4. El campo affected es `cuerpo` con el motivo «JSON invalido o con campos no permitidos», en lugar
   de inventar un `error.fields` por cada propiedad rechazada (que además revelaría nombres internos
   del DTO). Los errores de campo que sí se saben (documento, correo, clave, `Idempotency-Key`,
   `If-Match`) mantienen su nombre propio.

## Por qué

Todos son casos en los que la spec no dice nada y la opción elegida es la que menos superficie
cambia: ninguna altera un contrato publicado y todas se pueden revisar de una línea.

## Alternativas descartadas

- **Incrementar `version` al cambiar la clave**: provoca 412 falsos en la sesión del propio usuario.
- **Añadir `IMAGE_URL_ALLOWLIST` a identity**: sería una variable que `configuracion.md` no
  documenta y que este plan no puede registrar allí (`docs/especificacion/**` está bloqueado).
- **Poner `spring.profiles.active: test` en las pruebas por `properties`**: menos fiable que
  `@ActiveProfiles`, que la guía de Spring reserva para esto.
- **Devolver un campo por propiedad rechazada**: filtra la forma interna del DTO y produce
  mensajes más largos para el usuario sin ganar nada.

## A quién afecta

`services/identity-service/src/main/java/pe/hotel/identity/auth/AuthService.java`,
`platform/GlobalExceptionHandler.java`, `persona/DemoDataSeeder.java` y las pruebas de contexto.