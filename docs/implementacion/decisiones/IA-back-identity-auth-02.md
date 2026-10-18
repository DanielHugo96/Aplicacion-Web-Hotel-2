# IA-back-identity-auth-02 — Las semillas se siembran en Java y sin snapshot de bootstrap

- **Plan:** `back-identity-auth`
- **Paso:** 2 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-18

## Qué se encontró

Tres documentos describen los seeds de identity y no encajan entre sí:

1. `docs/especificacion/microservicios/identity/base-de-datos.md` § Procedimientos: «clave_hash ya
   BCrypt desde Java».
2. `docs/especificacion/infraestructura/configuracion.md` § Flyway y seeds: «locations
   `classpath:db/migration` + `classpath:db/seed/dev`», es decir, SQL.
3. `docs/especificacion/microservicios/identity/base-de-datos.md` § Seeds: «Outbox de bootstrap
   publica snapshot por persona», mientras que el plan `back-identity-auth` § 5 dice «Sin topics
   Kafka: `identity.persona.v1` es de `back-identity-personas`» y § 2 saca ese topic del alcance.

## Qué se decidió

- Los seeds corren en Java (`DemoDataSeeder`, un `ApplicationRunner` activo solo en los perfiles
  `dev`/`demo`), porque el hash BCrypt no se puede calcular en SQL y porque la siembra depende de
  `DEMO_PASSWORD` del entorno. `spring.flyway.locations` se queda en `classpath:db/migration`.
- No se escribe ninguna fila de outbox en el seed. Lo primero que emite identity es la bienvenida
  del registro público, que sí es de este plan.

## Por qué

El punto 1 y el 2 son incompatibles y el 1 manda: es la regla de credenciales, y el propio DDL de
referencia define `clave_hash varchar(255)`. Un `INSERT` de SQL tendría que recibir el hash ya
calculado, lo que en la práctica significa hacerlo en Java igualmente. La decisión que más pesa es
la tercera: el plan (fuente 2 de la jerarquía) deja `identity.persona.v1` y sus snapshots a
`back-identity-personas`, y ese plan añadirá sobre las mismas tablas. Emitir snapshots aquí
duplicaría mensajes en el mismo topic.

## Alternativas descartadas

- **SQL en `db/seed/dev` con el hash inyectado desde una variable**: imposible en SQL plano sin
  `pgcrypto`, que la spec no trae y cuya salida (`crypt`) no es el mismo formato de BCrypt de
  Spring.
- **Emitir `PersonaSnapshot` en el bootstrap**: está fuera del alcance del plan por § 2 y § 5, y
  duplicaría el efecto del plan que es su dueño.
- **Dejar la constancia sin escribir nada**: una decisión invisible es justo lo que la norma
  prohíbe.

## A quién afecta

`services/identity-service/src/main/java/pe/hotel/identity/persona/DemoDataSeeder.java` y la
ausencia de filas Kafka en el seed. `back-identity-personas` hereda las mismas tablas y es quien
emitirá `identity.persona.v1`.