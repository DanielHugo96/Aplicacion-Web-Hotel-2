# IA-back-identity-auth-01 — La secuencia de `persona` se ajusta en la migración, no en el seeder

- **Plan:** `back-identity-auth`
- **Paso:** 2 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-18

## Qué se encontró

`docs/especificacion/microservicios/identity/base-de-datos.md` § Seeds dice «Secuencias se ajustan tras
IDs explícitos», y el seed de dev inserta personas con `id` fijo (1–4, 100 y 101). El primer
intento lo hizo el seeder, en Java, con `setval`.

Falló al arrancar: `ERROR: permission denied for sequence persona_id_seq`. El usuario runtime
(`identity_app`) solo tiene `USAGE, SELECT` sobre las secuencias por los privilegios por defecto que
declara `infra/postgres/init/01-bases-y-roles.sh`; `setval` exige `UPDATE`, que es del migrador
(`identity_migrator`), que es quien lleva Flyway.

## Qué se decidió

El `setval` va al final de la migración `V4__identity_personas.sql`, que corre con el migrador, en
lugar de en el seeder de Java.

## Por qué

La jerarquía de [`../README.md`](../README.md) §1 (norma > plan > catálogo > registro) no resuelve
el conflicto: tanto la base de datos como el init de infraestructura son fuente. Se eligió lo menos
invasivo: el requisito de la spec se cumple exactamente igual, sin tocar `infra/postgres/init`
—zona de otro plan— ni ensanchar privilegios del usuario runtime, que la propia spec describe como
«runtime sin DDL».

## Alternativas descartadas

- **Pedir `UPDATE` sobre secuencias para `identity_app`** en `infra/postgres/init/01-bases-y-roles.sh`:
  toca la zona de `infra-compose` (cerrado) y ensancha al runtime sin necesidad.
- **Dejar la secuencia sin ajustar**: el primer alta posterior al seed recibiría `id = 1`, que ya
  pertenece a la persona sembrada, y fallaría por el PK.
- **Ajustar la secuencia solo en dev desde la consola**: no es reproducible ni verificable por
  script, que es justo lo que la norma exige.

## A quién afecta

`services/identity-service/src/main/resources/db/migration/V4__identity_personas.sql` y
`services/identity-service/src/main/java/pe/hotel/identity/persona/DemoDataSeeder.java`. No toca
ningún contrato ni plan cerrado.