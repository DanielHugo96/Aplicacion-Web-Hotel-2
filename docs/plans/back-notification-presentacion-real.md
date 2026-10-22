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
- [ ] 2. Escribir los cuerpos nuevos conservando las variables de cada plantilla (`{{nombre}}` en las tres; `{{hotel}}`, `{{idRecepcion}}`, `{{fechaSalida}}` en CHECKIN; `{{hotel}}`, `{{idRecepcion}}`, `{{totalAlojamiento}}`, `{{totalConsumos}}` en CHECKOUT) y el pie obligatorio. Verificación: `SELECT tipo, subject FROM template WHERE version = 1` devuelve «Bienvenido a Casa Sillar», «Estadía registrada» y «Salida registrada»; ningún cuerpo contiene «demo» ni «demostración».
- [ ] 3. Comprobar el envío real. Verificación: con Compose arriba y el worker en `dev`, completar un check-in de prueba y abrir `http://localhost:8025` (Mailpit): el asunto y el pie del correo son los nuevos y los valores de las variables aparecen escapados y completos.
- [ ] 4. Ejecutar la verificación canónica. Verificación: `bash scripts/mvn.sh -f services/pom.xml verify` termina en `BUILD SUCCESS`, `python scripts/validar-contratos.py --base origin/main` no detecta retiradas y `python scripts/validar-planes.py` da 0 errores.

## 5. Contratos afectados

- **OpenAPI:** ninguno. `notification` no expone endpoints de plantillas.
- **Eventos y comandos:** ninguno nuevo. `notificacion.bienvenida`, `notificacion.checkin` y `notificacion.checkout` conservan su forma; solo cambia el texto que el worker renderiza.
- **Persistencia:** `services/notification-service/src/main/resources/db/seed/dev/V901__notification_plantillas_presentacion.sql` sobre la tabla `template`. Sin tablas ni columnas nuevas.
- **Errores:** ninguno.

## 6. Pruebas

- Sin caso de la matriz: `NOT01`–`NOT03` prueban la entrega, no el texto. Evidencia en el PR con la consulta a `template` y el mensaje de Mailpit de un envío de bienvenida y uno de check-out.
- Comandos: `bash scripts/mvn.sh -f services/pom.xml verify`, `python scripts/validar-contratos.py --base origin/main` y `python scripts/validar-planes.py`.

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] Las tres plantillas v1 con la marca y el pie nuevos.
- [ ] Sin preguntas abiertas.
