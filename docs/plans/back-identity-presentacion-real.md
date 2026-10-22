# back-identity-presentacion-real — personas sembradas con la marca Casa Sillar (F5)

| Campo | Valor |
|---|---|
| Plan | `back-identity-presentacion-real` |
| Ámbito | `back` |
| Componente | `identity-service` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `cerrado` |
| Depende de | `back-identity-personas` |
| Spec | [`identity/base-de-datos.md` §Seeds](../especificacion/microservicios/identity/base-de-datos.md#seeds); [`02` §Condición de presentación real](../especificacion/02-landing-y-panel.md#condición-de-presentación-real); [`07` §Actualización 2026-10-21](../especificacion/07-revision-y-arranque.md#actualización-2026-10-21--presentación-real-e-imágenes-visibles) |
| Casos | `—` |
| Change | `—` |

## 1. Objetivo

Las personas del perfil dev se presentan con la marca de la cadena: «Sillar Admin», «Sillar Recepción Lima/Miraflores/Arequipa» y «Cliente Uno/Dos», sin «Demo» en el nombre. Se comprueba iniciando sesión con el ADMIN sembrado y leyendo la persona por API.

## 2. Alcance

**Entra:**

- `services/identity-service/src/main/java/pe/hotel/identity/persona/DemoDataSeeder.java`: nombres y apellidos de `PERSONAS` según [§Seeds](../especificacion/microservicios/identity/base-de-datos.md#seeds), más la corrección idempotente de las filas ya sembradas.
- Pruebas del seeder y este plan con su fila de catálogo y su bitácora de decisión.

**Fuera de alcance:**

- Correos, documentos, roles y sedes asignadas: no cambian; los `@hotel.test` siguen siendo ficticios y de uso interno.
- `back-identity-auth`: no cambian login, tokens, `/api/auth/me`, registro ni clientes de servicio.
- `front-admin-web-*`: la marca visible del panel se cambia en `front-admin-web-imagenes-panel`.
- `docs/contracts/**` y `docs/especificacion/**`: se leen, no se modifican.

## 3. Contexto

`0c5b0a2` cambia en [`identity/base-de-datos.md` §Seeds](../especificacion/microservicios/identity/base-de-datos.md#seeds) los nombres semilla a «Sillar Admin», «Sillar Recepción Lima/Miraflores/Arequipa» y «Cliente Uno/Dos», y [`07`](../especificacion/07-revision-y-arranque.md#actualización-2026-10-21--presentación-real-e-imágenes-visibles) incluye los seeds de identity en la marca única. El seeder actual declara `nombre = "Demo"` y `apellido = "Admin" | "Empleado Lima" | …` en `PERSONAS`.

El seeder es Java y no SQL porque el hash BCrypt depende de DEMO_PASSWORD; su idempotencia es `INSERT ... ON CONFLICT DO NOTHING`, así que cambiar las constantes no corrige una base ya sembrada. Este plan decide, y registra en [`IA-back-identity-presentacion-real-01.md`](../implementacion/decisiones/IA-back-identity-presentacion-real-01.md), corregir solo las filas que todavía conservan el valor semilla anterior (`nombre = 'Demo'` y el apellido exacto), para no pisar una edición hecha desde el panel.

La fase es `F5` porque `F0`–`F4` están cerradas en `main`: este cambio llega después del incremento y no reabre una fase anterior.

## 4. Pasos

- [x] 1. Cambiar en `PERSONAS` los pares nombre/apellido a «Sillar»/«Admin», «Sillar»/«Recepción Lima», «Sillar»/«Recepción Miraflores» y «Sillar»/«Recepción Arequipa», conservando id, correo, documento, tipo y sedes. Verificación: el bloque `PERSONAS` no contiene «Demo» ni el apellido «Empleado …»; los ids, correos y sedes siguen siendo los de §Seeds.
- [x] 2. Corregir las filas ya sembradas con un `UPDATE` idempotente por id y valor semilla anterior, ejecutado en el mismo `run()` antes de los snapshots, y dejar un log con el número de filas corregidas. Verificación: con la base ya sembrada, reiniciar el servicio y ver `seed_demo personas_renombradas=4`; un segundo reinicio lo deja en `0`.
- [x] 3. Comprobar que un apellido editado a mano no se sobrescribe. Verificación: editar el apellido de la persona 2 por API, reiniciar y `GET /api/persona/buscar/{id}` de la persona 2 conserva el valor editado.
- [x] 4. Verificar los datos sembrados de punta a punta. Verificación: login del ADMIN sembrado y `GET /api/persona/buscar/{id}` de la persona 1 devuelve «Sillar Admin»; el empleado 2 devuelve «Sillar Recepción Lima» con sede 1; los clientes 100 y 101 siguen siendo «Cliente Uno» y «Cliente Dos».
- [x] 5. Ejecutar la verificación canónica. Verificación: `bash scripts/mvn.sh -f services/pom.xml verify` termina en `BUILD SUCCESS` y `python scripts/validar-planes.py` con 0 errores.

## 5. Contratos afectados

- **OpenAPI:** ninguno. `identity.yaml` no expone campos nuevos; los nombres son datos de seed.
- **Eventos y comandos:** el `payload` de `PersonaSnapshot` no cambia de forma; solo cambian los valores sembrados. Sin topics, comandos ni grupos nuevos.
- **Persistencia:** sin migración nueva. El cambio vive en el seeder de perfil dev y en la corrección de filas ya insertadas.
- **Errores:** ninguno.

## 6. Pruebas

Evidencia de ejecución (2026-10-21). `identity_db` estaba en `V1`–`V4` con los seis seeds de
demostración (`nombre = 'Demo'`), que es justo el escenario del plan. Servicio arrancado sobre esa
base desde este árbol con
`bash scripts/mvn.sh -f services/pom.xml -pl identity-service spring-boot:run`, usando el `.env`
local para el entorno (la clave de demo no se imprime). Los guiones y las salidas completas están en
`%TEMP%/opencode/id-ev/`: `arranque-1..3.log`, `paso-4.sh` → `pasos-4.txt`,
`paso-3-edita.sh` → `paso-3-edita.txt` y `paso-3-tras-reinicio.sh` → `paso-3-tras-reinicio.txt`.

- Sin caso de la matriz: el cambio no define comportamiento nuevo, solo el dato semilla de la presentación. Evidencia en el PR con la salida del log del seeder y la respuesta de `GET /api/persona/buscar/{id}` para las seis personas.
- Comandos: `bash scripts/mvn.sh -f services/pom.xml verify` y `python scripts/validar-planes.py`.

### Paso 1 — nombres de `PERSONAS`

El bloque `PERSONAS` de
`services/identity-service/src/main/java/pe/hotel/identity/persona/DemoDataSeeder.java` queda en
«Sillar»/«Admin», «Sillar»/«Recepción Lima», «Sillar»/«Recepción Miraflores» y
«Sillar»/«Recepción Arequipa», con «Cliente»/«Uno» y «Cliente»/«Dos» intactos. `grep -n "Empleado"
DemoDataSeeder.java` solo devuelve la etiqueta del rol `EMPLEADO` (`"Empleado"`) y los apellidos
semilla anteriores en el `WHERE` del renombrado; ninguna persona se siembra ya con ellos. Ids
(1, 2, 3, 4, 100, 101), correos `@hotel.test`, documentos `9000000x`/`9000010x` y sedes (vacía, 1, 2,
3, vacía, vacía) son los de
[§Seeds](../especificacion/microservicios/identity/base-de-datos.md#seeds). Lo fija la prueba
`DemoDataSeederTests.lasPersonasSembradasLlevanLaMarcaCasaSillar`, que captura los seis
`INSERT INTO persona (id,…)` y compara nombre, apellido, id y correo uno a uno.

### Paso 2 — corrección de las filas ya sembradas

`DemoDataSeeder.renombrarPersonasSemillaAnterior()` corre dentro del mismo `run()` y justo después de
`INSERT … ON CONFLICT DO NOTHING`, antes de `sembrarSedes()` y de los snapshots de bootstrap. El
`UPDATE` lleva `id`, `nombre = 'Demo'` y el apellido semilla anterior en el `WHERE`, y deja un log con
el total. Arranque sobre la base ya sembrada (`arranque-1.log`):

```
seed_demo personas declaradas=6 nuevas=0
seed_demo personas_renombradas=4
seed_demo sedes nuevas=0
```

Segundo arranque, ya corregida (`arranque-2.log` y `arranque-3.log`):

```
seed_demo personas declaradas=6 nuevas=0
seed_demo personas_renombradas=0
```

La tabla tras el primer arranque:

```
 id  | nombre  |       apellido       |             correo              | id_tipo_persona | estado | version
-----+---------+----------------------+---------------------------------+-----------------+--------+---------
   1 | Sillar  | Admin                | admin@hotel.test                |               1 | t      |       1
   2 | Sillar  | Recepción Lima       | recepcion.lima@hotel.test       |               2 | t      |       1
   3 | Sillar  | Recepción Miraflores | recepcion.miraflores@hotel.test |               2 | t      |       1
   4 | Sillar  | Recepción Arequipa   | recepcion.arequipa@hotel.test   |               2 | t      |       1
 100 | Cliente | Uno                  | cliente.uno@hotel.test          |               3 | t      |       1
 101 | Cliente | Dos                  | cliente.dos@hotel.test          |               3 | t      |       1
```

`persona_sede` no cambia (`2→1`, `3→2`, `4→3`). `version` se queda en 1 y `updated_at` no se toca:
es una corrección del valor semilla, no una mutación de negocio
([`IA-back-identity-presentacion-real-02.md`](../implementacion/decisiones/IA-back-identity-presentacion-real-02.md)).

### Paso 3 — una edición humana no se pisa

`paso-3-edita.sh` inicia sesión como la persona 2 y cambia su apellido por API:

```
GET /api/auth/me (recepcion.lima) -> ETag "1"
PUT /api/auth/me -> HTTP 200 | idPersona=2 nombre='Sillar' apellido='Recepción Lima (editada a mano)' correo=recepcion.lima@hotel.test sedes=[1] tipoPersona=Empleado version=2
```

Se reinicia el servicio (el arranque 3 de arriba, con `personas_renombradas=0`) y se relee
(`paso-3-tras-reinicio.sh`):

```
GET /api/persona/buscar/2 -> HTTP 200 | idPersona=2 nombre='Sillar' apellido='Recepción Lima (editada a mano)' correo=recepcion.lima@hotel.test sedes=[1] tipoPersona=Empleado version=2
```

El valor editado sobrevive porque el `WHERE` del `UPDATE` exige el apellido semilla anterior. La
prueba `DemoDataSeederTests.renombraSoloLasFilasQueConservanElValorSemillaAnterior` fija qué filas
entran en el `UPDATE` (solo 1–4, y con los apellidos semilla anteriores como condición). Después de
la prueba se restauró el valor de la spec por la misma API, para no dejar la base con datos de
prueba.

### Paso 4 — de punta a punta

`paso-4.sh`: login del ADMIN sembrado con la clave de demo del entorno y lectura de las seis personas
con el token resultante:

```
POST /api/auth/login (admin@hotel.test) -> HTTP 200, token emitido (no se imprime)

GET /api/persona/buscar/1 -> HTTP 200 | idPersona=1 nombre='Sillar' apellido='Admin' correo=admin@hotel.test sedes=[] tipoPersona=Administrador version=1
GET /api/persona/buscar/2 -> HTTP 200 | idPersona=2 nombre='Sillar' apellido='Recepción Lima' correo=recepcion.lima@hotel.test sedes=[1] tipoPersona=Empleado version=1
GET /api/persona/buscar/3 -> HTTP 200 | idPersona=3 nombre='Sillar' apellido='Recepción Miraflores' correo=recepcion.miraflores@hotel.test sedes=[2] tipoPersona=Empleado version=1
GET /api/persona/buscar/4 -> HTTP 200 | idPersona=4 nombre='Sillar' apellido='Recepción Arequipa' correo=recepcion.arequipa@hotel.test sedes=[3] tipoPersona=Empleado version=1
GET /api/persona/buscar/100 -> HTTP 200 | idPersona=100 nombre='Cliente' apellido='Uno' correo=cliente.uno@hotel.test sedes=[] tipoPersona=Cliente version=1
GET /api/persona/buscar/101 -> HTTP 200 | idPersona=101 nombre='Cliente' apellido='Dos' correo=cliente.dos@hotel.test sedes=[] tipoPersona=Cliente version=1

GET /api/auth/me -> 200
```

El empleado 2 conserva la sede 1, y los clientes 100 y 101 siguen siendo «Cliente Uno» y «Cliente
Dos» sin sedes. `/api/auth/me` del ADMIN responde 200 con el token del login.

### Paso 5 — verificación canónica

```
bash scripts/mvn.sh -f services/pom.xml verify
  identity-service ................................... SUCCESS [ 45.958 s]
  ... (los nueve módulos) ...
  BUILD SUCCESS
  identity-service: Tests run: 81, Failures: 0, Errors: 0, Skipped: 0
```

`identity-service` pasa a 81 pruebas (79 antes, más las dos que añade este plan:
`lasPersonasSembradasLlevanLaMarcaCasaSillar` y
`renombraSoloLasFilasQueConservanElValorSemillaAnterior`). Los otros ocho módulos siguen en verde con
las suyas.

```
python scripts/validar-planes.py
  39 plan(es) revisado(s); 39 previsto(s) en el catálogo; 0 error(es), 0 aviso(s).

python scripts/validar-contratos.py --base origin/main
  Contratos: 8 OpenAPI, 20 schemas de evento. Regla: aditivos frente a origin/main.
  Los contratos solo se han ampliado. Correcto.
```

El validador de contratos no lo pide el plan, pero es el que demuestra que este cambio no toca
ningún contrato publicado.

### Comandos

`bash scripts/mvn.sh -f services/pom.xml verify`, `python scripts/validar-planes.py` y, como
comprobación adicional del cierre, `python scripts/validar-contratos.py --base origin/main`.

## 7. Preguntas abiertas

Ninguna (resuelto por IA: el renombrado no incrementa `version` ni `updated_at`, para no invalidar el
`ETag` que el panel ya tiene en memoria; ver
[`IA-back-identity-presentacion-real-02.md`](../implementacion/decisiones/IA-back-identity-presentacion-real-02.md)).

## 8. Criterio de terminado

- [x] Todos los pasos marcados con su verificación.
- [x] Las seis personas sembradas muestran los nombres de la spec.
- [x] Sin preguntas abiertas.
