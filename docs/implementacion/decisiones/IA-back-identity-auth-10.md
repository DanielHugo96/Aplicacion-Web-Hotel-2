# IA-back-identity-auth-10 — `DemoDataSeeder` siembra sedes dentro de la guarda de `DEMO_PASSWORD`

- **Plan:** `back-identity-auth`
- **Paso:** 2 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-18

## Qué se encontró

Defecto real, encontrado por el agente revisor al auditar el PR y reproducido antes de tocar código.

`DemoDataSeeder.run()` invocaba `sembrarSedes()` fuera del `if` que protege a `sembrarPersonas()`:

```java
sembrarRoles();
if (demo.getPassword() == null || demo.getPassword().isBlank()) {
  log.info("seed_demo personas_omitidas motivo=demo_password_ausente");
} else {
  sembrarPersonas();
}
sembrarSedes();                       // <- fuera de la guarda
sembrarClientesDeServicio();
```

`sembrarSedes()` inserta `(2,1)`, `(3,2)` y `(4,3)` en `persona_sede`, cuya columna `id_persona` es
`int NOT NULL REFERENCES persona(id)` (`V4__identity_personas.sql`). Sin `DEMO_PASSWORD` no se
crean las personas pero sí las sedes, la FK se viola, la transacción `@Transactional` de `run()`
se deshace y el `ApplicationRunner` lanza: **identity-service no arranca**.

Reproducción previa al arreglo:

```
Application run failed
  org.springframework.dao.DataIntegrityViolationException: PreparedStatementCallback; SQL
  [INSERT INTO persona_sede (id_persona, id_hotel) VALUES (?, ?) ON CONFLICT DO NOTHING
   ]; ERROR: insert or update on table "persona_sede" violates foreign key constraint
   "persona_sede_id_persona_fkey"
```

El puerto 8081 no quedaba escuchando. Contradecía el paso 2 del plan («sin ella no se siembran
usuarios») y `application.yml:106-107`, y `.env.example:63` publica `DEMO_PASSWORD=` vacío, así que
un entorno recién copiado sin credenciales no levantaba el servicio.

## Qué se decidió

`sembrarSedes()` pasa a ejecutarse dentro de la misma rama `else` que `sembrarPersonas()`. Los roles
(`sembrarRoles()`) y los clientes de servicio (`sembrarClientesDeServicio()`) se quedan fuera de la
guarda: no tienen FK a `persona` y sí dependen de secretos propios del entorno.

Se añaden cuatro pruebas en `DemoDataSeederTests` con el `JdbcTemplate` simulado, que fijan el
comportamiento por filas escritas: sin `DEMO_PASSWORD` no hay ningún `INSERT INTO persona (` ni
`INSERT INTO persona_sede` y `run()` no lanza; con `DEMO_PASSWORD`, 6 personas y 3 sedes.

## Por qué

Es lo que dice el paso 2 del plan y lo que exige la FK del DDL de referencia. La decisión es local
(`services/identity-service/**`): no hace falta tocar contrato, plan cerrado ni zona ajena, así que
es nivel 2 y no nivel 3.

## Alternativas descartadas

- **`INSERT` condicional por sede, con `INSERT ... SELECT ... WHERE EXISTS`**: evitaría el error pero
  dejaría filas de `persona_sede` a medias según el estado previo de la tabla, que es justo el
  comportamiento raro que un seed no debe tener.
- **Migrate la FK a `DEFERRABLE`**: cambiar el esquema para acomodar un bug del seeder.
- **Silenciar el error y seguir**: dejaría el servicio arrancando con datos incoherentes y el fallo
  escondido.

## A quién afecta

`services/identity-service/src/main/java/pe/hotel/identity/persona/DemoDataSeeder.java` y
`services/identity-service/src/test/java/pe/hotel/identity/persona/DemoDataSeederTests.java`. Sin
efecto en contratos ni en otros planes.