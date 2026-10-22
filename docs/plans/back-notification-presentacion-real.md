# back-notification-presentacion-real — plantillas de correo de Casa Sillar (F5)

| Campo | Valor |
|---|---|
| Plan | `back-notification-presentacion-real` |
| Ámbito | `back` |
| Componente | `notification-service` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `en curso` |
| Depende de | `back-notification-worker` |
| Spec | [`notification/base-de-datos.md` §Seeds](../especificacion/microservicios/notification/base-de-datos.md#seeds); [`02` §Condición de presentación real](../especificacion/02-landing-y-panel.md#condición-de-presentación-real); [`07` §Actualización 2026-10-21](../especificacion/07-revision-y-arranque.md#actualización-2026-10-21--presentación-real-e-imágenes-visibles) |
| Casos | `—` |
| Change | `—` |

## 1. Objetivo

Los tres correos de la cadena dejan de presentarse como demo: BIENVENIDA se titula «Bienvenido a Casa Sillar» y las tres plantillas v1 llevan el texto «Notificación automática de la cadena; no es un comprobante fiscal», sin «Hotel Demo» ni «demostración». Se comprueba leyendo las plantillas sembradas y un envío real en Mailpit.

## 2. Alcance

**Entra:**

- `services/notification-service/src/main/resources/db/seed/dev/V901__notification_plantillas_presentacion.sql`: asunto y cuerpo de las plantillas `BIENVENIDA`, `CHECKIN` y `CHECKOUT`, versión 1.
- Pruebas del worker y este plan con su fila de catálogo y su bitácora de decisión.

**Fuera de alcance:**

- `V900__notification_plantillas.sql`: no se edita; ya está aplicada en el perfil dev y Flyway rechazaría el cambio de checksum.
- Variables, `templateVersion`, tipos de plantilla y el contrato de `notification_job`: no cambian.
- Los textos que `reception` envía en `notificacion.checkin` y `notificacion.checkout`: el nombre de la sede viaja en `HabitacionSnapshot` y lo actualiza `back-hotel-presentacion-real`.
- `back-notification-worker`: no cambian leases, reintentos, purga ni SMTP.
- `docs/contracts/**` y `docs/especificacion/**`: no se modifican.

## 3. Contexto

`0c5b0a2` cambia [`notification/base-de-datos.md` §Seeds](../especificacion/microservicios/notification/base-de-datos.md#seeds): la bienvenida pasa a «Bienvenido a Casa Sillar» y el pie de las tres plantillas a «Notificación automática de la cadena; no es un comprobante fiscal». [`07`](../especificacion/07-revision-y-arranque.md#actualización-2026-10-21--presentación-real-e-imágenes-visibles) incluye los correos en la marca única; [`notification/api.md` §RabbitMQ](../especificacion/microservicios/notification/api.md#rabbitmq) ya muestra el nombre «Casa Sillar Lima Centro» en los ejemplos, sin cambio de estructura.

Las plantillas viven en el seed de dev `V900`, que ya está aplicada. Este plan decide, y registra en [`IA-back-notification-presentacion-real-01.md`](../implementacion/decisiones/IA-back-notification-presentacion-real-01.md), corregirlas con una migración de seed nueva `V901` en vez de editar `V900`.

La fase es `F5` porque `F0`–`F4` están cerradas en `main`: este cambio llega después del incremento y no reabre una fase anterior.

## 4. Pasos

- [x] 1. Crear `V901__notification_plantillas_presentacion.sql` en `db/seed/dev/` con un `UPDATE` por `(tipo, version)` para las tres plantillas. Verificación: Flyway aplica `V901` sobre una `notification_db` sembrada sin error de checksum. Evidencia: reinicio del servicio en `dev` → `Successfully validated 7 migrations` y `Successfully applied 1 migration to schema "public", now at version v901` (`tests/evidence/qa-runbooks/_runtime/notification-service.log`, línea 5787); `select version, description, success from flyway_schema_history` devuelve la fila `901 | notification plantillas presentacion | t`.
- [x] 2. Escribir los cuerpos nuevos conservando las variables de cada plantilla (`{{nombre}}` en las tres; `{{hotel}}`, `{{idRecepcion}}`, `{{fechaSalida}}` en CHECKIN; `{{hotel}}`, `{{idRecepcion}}`, `{{totalAlojamiento}}`, `{{totalConsumos}}` en CHECKOUT) y el pie obligatorio. Verificación: `SELECT tipo, subject FROM template WHERE version = 1` devuelve «Bienvenido a Casa Sillar», «Estadía registrada» y «Salida registrada»; ningún cuerpo contiene «demo» ni «demostración». Evidencia: consulta en §6; `python scripts/validar-planes.py` y `PlantillasPresentacionTest` (5 casos, 0 fallos) comprueban los marcadores exactos, el pie y la ausencia de la marca de demostración.
- [x] 3. Comprobar el envío real. Verificación: con Compose arriba y el worker en `dev`, completar un check-in de prueba y abrir `http://localhost:8025` (Mailpit): el asunto y el pie del correo son los nuevos y los valores de las variables aparecen escapados y completos. Evidencia: tres envíos reales (registro público, check-in y cierre) con sus jobs en `ENVIADA` y el texto recibido en Mailpit, transcritos en §6.
- [ ] 4. Ejecutar la verificación canónica. Verificación: `bash scripts/mvn.sh -f services/pom.xml verify` termina en `BUILD SUCCESS`, `python scripts/validar-contratos.py --base origin/main` no detecta retiradas y `python scripts/validar-planes.py` da 0 errores.

## 5. Contratos afectados

- **OpenAPI:** ninguno. `notification` no expone endpoints de plantillas.
- **Eventos y comandos:** ninguno nuevo. `notificacion.bienvenida`, `notificacion.checkin` y `notificacion.checkout` conservan su forma; solo cambia el texto que el worker renderiza.
- **Persistencia:** `services/notification-service/src/main/resources/db/seed/dev/V901__notification_plantillas_presentacion.sql` sobre la tabla `template`. Sin tablas ni columnas nuevas.
- **Errores:** ninguno.

## 6. Pruebas

- Sin caso de la matriz: `NOT01`–`NOT03` prueban la entrega, no el texto. Evidencia en el PR con la consulta a `template` y el mensaje de Mailpit de un envío de bienvenida y uno de check-out.
- Comandos: `bash scripts/mvn.sh -f services/pom.xml verify`, `python scripts/validar-contratos.py --base origin/main` y `python scripts/validar-planes.py`.

### Evidencia

**1. Plantillas sembradas.** Comando, sobre Compose arriba y `notification_db` migrada por el worker en `dev`:

```bash
docker exec hotel-postgres-1 psql -U postgres -d notification_db \
  -c "select tipo, version, subject from template where version = 1 order by tipo;" \
  -c "select count(*) from template where version=1 and (lower(body_text) like '%demo%' \
      or lower(body_text) like '%demostración%' or lower(subject) like '%demo%');"
```

Salida:

```
    tipo    | version |         subject
------------+---------+--------------------------
 BIENVENIDA |       1 | Bienvenido a Casa Sillar
 CHECKIN    |       1 | Estadía registrada
 CHECKOUT   |       1 | Salida registrada
(3 rows)

 count
-------
     0
(1 row)
```

Cuerpos (`select tipo || ' | ' || replace(body_text, chr(10), ' / ') from template where version=1`),
con las variables de §Esquemas JSONB intactas y el pie obligatorio:

```
BIENVENIDA | Bienvenido a Casa Sillar /  / Hola {{nombre}}, /  / Gracias por registrarte. Este correo confirma tu alta en Casa Sillar. /  / Notificación automática de la cadena; no es un comprobante fiscal. /
CHECKIN | Estadía registrada /  / Hola {{nombre}}, /  / Tu check-in en {{hotel}} quedó registrado (estadía {{idRecepcion}}). / Fecha de salida prevista: {{fechaSalida}}. /  / Notificación automática de la cadena; no es un comprobante fiscal. /
CHECKOUT | Salida registrada /  / Hola {{nombre}}, /  / Tu salida de {{hotel}} quedó registrada (estadía {{idRecepcion}}). / Total de alojamiento: {{totalAlojamiento}}. / Total de consumos: {{totalConsumos}}. /  / Notificación automática de la cadena; no es un comprobante fiscal. /
```

La migración quedó aplicada sin error de checksum: `flyway_schema_history` tiene
`901 | notification plantillas presentacion | t` y el arranque registró
`Successfully validated 7 migrations` + `Successfully applied 1 migration to schema "public", now at
version v901` en `tests/evidence/qa-runbooks/_runtime/notification-service.log`.

**2. Envío real.** Los tres correos salieron por las rutas de la spec y quedaron `ENVIADA` con un
solo intento (`select tipo, estado, attempts, sent_at from notification_job`: BIENVENIDA, CHECKIN y
CHECKOUT en `ENVIADA`, `attempts = 1`). Texto recibido en Mailpit (`GET
http://localhost:8025/api/v1/message/<id>`):

- Bienvenida — asunto `Bienvenido a Casa Sillar`:

  ```
  Bienvenido a Casa Sillar

  Hola Cliente,

  Gracias por registrarte. Este correo confirma tu alta en Casa Sillar.

  Notificación automática de la cadena; no es un comprobante fiscal.
  ```

- Check-in — asunto `Estadía registrada` (habitación 101, sede Lima Centro):

  ```
  Estadía registrada

  Hola Cliente Uno,

  Tu check-in en Casa Sillar Lima Centro quedó registrado (estadía 1).
  Fecha de salida prevista: 2026-10-23.

  Notificación automática de la cadena; no es un comprobante fiscal.
  ```

- Check-out — asunto `Salida registrada`:

  ```
  Salida registrada

  Hola Cliente Uno,

  Tu salida de Casa Sillar Lima Centro quedó registrada (estadía 1).
  Total de alojamiento: 70.00.
  Total de consumos: 0.00.

  Notificación automática de la cadena; no es un comprobante fiscal.
  ```

Las variables llegan completas y sin marcadores; `TemplateRenderer` las escapa (CR/LF y llaves), lo
que cubre `TemplateRendererTest.escapaElValorParaQueNoInyecteMarcadoresNiCabeceras` y la prueba
nueva `PlantillasPresentacionTest.elCuerpoNuevoSeRenderizaSinMarcadoresNiMarcaDeDemostracion`.

**3. Prueba automática del seed.** `PlantillasPresentacionTest` (5 casos en verde) lee
`db/seed/dev/V901__notification_plantillas_presentacion.sql` del classpath y comprueba asuntos, pie,
marcadores y ausencia de «demo»/«demostración». Comando:

```bash
bash scripts/mvn.sh -f services/pom.xml -pl notification-service test \
  -Dtest=PlantillasPresentacionTest -Dsurefire.failIfNoSpecifiedTests=false
```

Salida: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0` → `BUILD SUCCESS`.

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] Las tres plantillas v1 con la marca y el pie nuevos.
- [ ] Sin preguntas abiertas.
