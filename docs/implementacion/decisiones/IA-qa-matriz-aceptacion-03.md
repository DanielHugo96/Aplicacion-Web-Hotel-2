# IA-qa-matriz-aceptacion-03 — Cómo se aísla la instancia de rebuild y cómo se apaga SMTP sin depender del contenedor

- **Plan:** `qa-matriz-aceptacion`
- **Paso:** 13 (REP02, REP03) y 14 (NOT01) de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-21

## Qué se encontró

Al ejecutar los tres casos que quedaron pendientes aparecieron dos problemas de **aislamiento
del entorno de prueba**, no del sistema:

1. **REP02.** La instancia de reporting que hace el rebuild en la base nueva se levantaba con
   `server.port=8096` y, como el Compose de servicios corre con `eureka.client.enabled=true` por
   defecto, **se registraba en Eureka con el mismo nombre de aplicación** que la instancia
   principal. El Gateway balancea entre las dos instancias registradas: mientras la de rebuild
   arrancaba (Flyway + bootstrap + consumo del stream desde el principio), parte de las consultas
   de `/api/reportes/**` caían en ella y devolvían `503 DEPENDENCY_UNAVAILABLE`. El caso
   comparaba entonces una respuesta buena contra una respuesta de error y fallaba por un motivo
   que no era el que mide.

2. **NOT01.** El caso exige «SMTP (Mailpit) caído» y reintentos hasta `ERROR`. Detener el
   contenedor `hotel-mailpit-1` no es determinista en este entorno: el contenedor tiene política
   `unless-stopped` y volvió a arrancar a mitad de la observación, de modo que el job pasó de
   `PENDIENTE`/`SMTP_ERROR` a `ENVIADA` y el criterio «ERROR tras agotar los reintentos» no se
   pudo medir.

## Qué se decidió

- **REP02:** la instancia de rebuild se levanta con `EUREKA_ENABLED=false` y se consulta
  directamente en `http://localhost:8096`. El Gateway sigue apuntando solo a la instancia
  principal, que es la referencia de comparación. Al terminar, la base `reporting_rebuild_db` se
  retira (salvo `QA_MATRIZ_CONSERVAR_REBUILD=1`).
- **NOT01:** el «SMTP caído» se induce levantando **el worker real de notification** con
  `SMTP_PORT=1026`, un puerto local cerrado, en lugar de parar Mailpit. El worker, el job, el
  lease, los reintentos y el procedimiento de resultado son los reales; lo único que cambia es a
  qué puerto intenta conectar el cliente SMTP configurado por entorno. Mailpit sigue arriba y se
  usa como referencia de que el puerto 1026 no escucha.

Además, el paso 13 exige «503 `REPORTING_NOT_READY` sin bootstrap». Se mide en una base nueva
migrada con `REPORTING_BOOTSTRAP_ENABLED=false`, en el puerto 8097 y sin Eureka, para no tocar la
instancia principal. El caso se consulta con el dashboard consolidado (sin `idHotel`), porque la
validación de sede ocurre antes de la guardia de bootstrap y con la proyección vacía devolvería
`404 RESOURCE_NOT_FOUND` —correcto, pero no el criterio del caso—.

## Por qué

Nivel 2 de [`../README.md`](../../plans/README.md) §6.11: se elige **la opción menos invasiva que no
rompe ningún plan cerrado**. Las tres decisiones tocan **solo variables de entorno del proceso de
prueba**. No se modifica ningún servicio, ni el Compose, ni el perfil `dev`, ni un contrato. La
alternativa de tocar `infra/compose.yaml` (quitar la política de reinicio) o el `application.yml`
de notification cambiaría artefactos fuera de la zona de `qa`.

## Alternativas descartadas

- **Quitar `unless-stopped` de Mailpit en el Compose** para poder pararlo: cambia infraestructura
  cerrada y afecta a `qa-runbooks`, que cuenta con que Mailpit esté arriba.
- **Escribir el job en `ERROR` por SQL**: fabricaría el resultado que el caso mide.
- **Aceptar `ENVIADA` como desenlace de NOT01**: la spec exige `ERROR` tras agotar los reintentos;
  aceptar otra cosa sería cerrar el caso sin cumplir su criterio.
- **Dejar que el rebuild se registre en Eureka y reintentar hasta que el Gateway caiga en la
  instancia buena**: convierte un fallo de aislamiento en un caso probabilístico.
- **Comparar el rebuild contra sí mismo**: no demuestra nada; la referencia tiene que ser la
  instancia principal.

## A quién afecta

`scripts/qa/matriz.py` (funciones `rep02`, `rep03_sin_bootstrap`, `not01`) y la evidencia
`REP02.json`, `REP03.json` y `NOT01.json`, que declaran el aislamiento en su campo `inducido` o
`smtpCaido`. No se toca ningún contrato, servicio ni app. No cambia el resultado exigido por
ningún caso: solo cómo se consigue el estado que el caso necesita observar.
