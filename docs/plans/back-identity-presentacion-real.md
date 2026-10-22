# back-identity-presentacion-real — personas sembradas con la marca Casa Sillar (F5)

| Campo | Valor |
|---|---|
| Plan | `back-identity-presentacion-real` |
| Ámbito | `back` |
| Componente | `identity-service` |
| Fase | `F5` |
| Modo | `directo` |
| Estado | `en curso` |
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

- [ ] 1. Cambiar en `PERSONAS` los pares nombre/apellido a «Sillar»/«Admin», «Sillar»/«Recepción Lima», «Sillar»/«Recepción Miraflores» y «Sillar»/«Recepción Arequipa», conservando id, correo, documento, tipo y sedes. Verificación: el bloque `PERSONAS` no contiene «Demo» ni el apellido «Empleado …»; los ids, correos y sedes siguen siendo los de §Seeds.
- [ ] 2. Corregir las filas ya sembradas con un `UPDATE` idempotente por id y valor semilla anterior, ejecutado en el mismo `run()` antes de los snapshots, y dejar un log con el número de filas corregidas. Verificación: con la base ya sembrada, reiniciar el servicio y ver `seed_demo personas_renombradas=4`; un segundo reinicio lo deja en `0`.
- [ ] 3. Comprobar que un apellido editado a mano no se sobrescribe. Verificación: editar el apellido de la persona 2 por API, reiniciar y `GET /api/persona/buscar/{id}` de la persona 2 conserva el valor editado.
- [ ] 4. Verificar los datos sembrados de punta a punta. Verificación: login del ADMIN sembrado y `GET /api/persona/buscar/{id}` de la persona 1 devuelve «Sillar Admin»; el empleado 2 devuelve «Sillar Recepción Lima» con sede 1; los clientes 100 y 101 siguen siendo «Cliente Uno» y «Cliente Dos».
- [ ] 5. Ejecutar la verificación canónica. Verificación: `bash scripts/mvn.sh -f services/pom.xml verify` termina en `BUILD SUCCESS` y `python scripts/validar-planes.py` con 0 errores.

## 5. Contratos afectados

- **OpenAPI:** ninguno. `identity.yaml` no expone campos nuevos; los nombres son datos de seed.
- **Eventos y comandos:** el `payload` de `PersonaSnapshot` no cambia de forma; solo cambian los valores sembrados. Sin topics, comandos ni grupos nuevos.
- **Persistencia:** sin migración nueva. El cambio vive en el seeder de perfil dev y en la corrección de filas ya insertadas.
- **Errores:** ninguno.

## 6. Pruebas

- Sin caso de la matriz: el cambio no define comportamiento nuevo, solo el dato semilla de la presentación. Evidencia en el PR con la salida del log del seeder y la respuesta de `GET /api/persona/buscar/{id}` para las seis personas.
- Comandos: `bash scripts/mvn.sh -f services/pom.xml verify` y `python scripts/validar-planes.py`.

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] Las seis personas sembradas muestran los nombres de la spec.
- [ ] Sin preguntas abiertas.
