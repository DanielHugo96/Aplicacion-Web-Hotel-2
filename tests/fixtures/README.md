# Fixtures de la matriz de aceptación (06 §5)

Los fixtures F1–F9 se crean **por las rutas reales de la API**, nunca por `INSERT`, salvo F9
(venta `LEGACY`), que la especificación define como fila de importación/migración y por tanto
solo puede existir en base de datos.

| Fixture | Script | Cómo se crea | Evidencia |
|---|---|---|---|
| F1 | `python scripts/qa/matriz.py fixtures` | `POST /api/recepcion/registrar` en 101, cliente 100, adelanto 20.00 | `fixtures.json`, `paso-3-6-fixtures.json` |
| F2 | idem | `POST /api/venta` producto 1 ×2 (7.00), clave guardada para STK03 | idem |
| F3 | idem | `PUT /api/venta/{id}` PAGADO EFECTIVO | idem |
| F8 | idem | `POST /api/recepcion/registrar-salida` de F1 (200) y ciclo C1 | idem |
| F4 | `python scripts/qa/matriz.py fixtures-avanzados` | cierre en CONFIRMANDO reteniendo el advisory lock que usa Sales (`pg_advisory_lock(7101, idRecepcion)`), equivalente a «Sales detenido tras aceptar» | `fixtures-avanzados.json`, `F4.json` |
| F5 | idem | `POST /api/habitacion/102/mantenimiento` con reception parado a mitad de admisión; la admisión se persiste con la misma ruta interna que usa hotel | idem |
| F6 | idem | check-in en 201 (sede 2) y mantenimiento rechazado (409 durable) | idem |
| F7 | `python scripts/qa/matriz.py stk` (se reinduce por caso) | proxy TCP delante de inventory que acepta la conexión y no responde al débito | `STK04/05/07.json` |
| F9 | `python scripts/qa/matriz.py fixtures-avanzados` | fila de importación `origen=LEGACY` en `sales_db` (único fixture por SQL, como manda 06 §3.7) | `F9.json` |

## Estado transitorio

F4 y F5 son estados **inducidos**: F4 retiene el lock que serializa la confirmación de Sales y F5
detiene reception para que la admisión de la operación no complete. No se presentan como
naturales: cada archivo de evidencia dice qué se retuvo y cómo se soltó. F7 se reinduce en cada
caso que lo necesita (`STK04`, `STK05`, `STK07`) porque su estado (débito en vuelo) es transitorio
y el recuperador de 30 s lo resuelve.

`tests/evidencia/qa-matriz-aceptacion/_runtime/` (claves efímeras del caso SEC06 y logs) está
ignorado por Git.
