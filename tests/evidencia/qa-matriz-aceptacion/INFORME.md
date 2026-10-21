# Informe de la matriz de aceptación (F5)

Generado por `python scripts/qa/matriz.py informe` a partir de los archivos de
`tests/evidencia/qa-matriz-aceptacion/`. Cada fila se ejecutó contra el Compose real y los
servicios reales; el archivo citado contiene el comando/endpoint, la respuesta y el criterio.

- Casos con evidencia ejecutada: **41/41**
- Casos que cumplen el criterio: **41/41**

| Caso | Grupo | Comando o endpoint | Invocación | Resultado | Evidencia |
|---|---|---|---|---|---|
| SEC01 | plataforma | `GET /api/persona/listar sin token` | `python scripts/qa/matriz.py sec` | ✅ | `tests/evidencia/qa-matriz-aceptacion/SEC01.json` |
| SEC02 | plataforma | `POST /api/auth/registro con idTipoPersona` | `python scripts/qa/matriz.py sec` | ✅ | `tests/evidencia/qa-matriz-aceptacion/SEC02.json` |
| SEC03 | plataforma | `GET /api/habitacion/buscar/{id} y GET /api/reportes/dashboard de sede ajena` | `python scripts/qa/matriz.py sec` | ✅ | `tests/evidencia/qa-matriz-aceptacion/SEC03.json` |
| SEC04 | plataforma | `GET /api/carrito/listar/{idRecepcion} y POST /api/venta con estadía ajena` | `python scripts/qa/matriz.py sec` | ✅ | `tests/evidencia/qa-matriz-aceptacion/SEC04.json` |
| SEC05 | plataforma | `POST /internal/stock/descontar directo al puerto interno` | `python scripts/qa/matriz.py sec` | ✅ | `tests/evidencia/qa-matriz-aceptacion/SEC05.json` |
| SEC06 | plataforma | `GET /api/auth/me y GET /api/hotel/listar con JWT inválido` | `python scripts/qa/matriz.py sec` | ✅ | `tests/evidencia/qa-matriz-aceptacion/SEC06.json` |
| SEC07 | plataforma | `PUT /api/persona/actualizar/{id} y DELETE /api/persona/eliminar/{id}` | `python scripts/qa/matriz.py sec` | ✅ | `tests/evidencia/qa-matriz-aceptacion/SEC07.json` |
| SEC08 | plataforma | `OPTIONS preflight CORS permitido y ajeno` | `python scripts/qa/matriz.py sec` | ✅ | `tests/evidencia/qa-matriz-aceptacion/SEC08.json` |
| LP01 | catálogo público | `GET /api/public/hoteles, /hoteles/{slug}, /categorias y /categorias/{idCategoria}` | `python scripts/qa/matriz.py lp-crud` | ✅ | `tests/evidencia/qa-matriz-aceptacion/LP01.json` |
| LP02 | catálogo público | `GET público con ?fecha= / ?ocupacion= + bundle de public-web` | `python scripts/qa/matriz.py lp-crud` | ✅ | `tests/evidencia/qa-matriz-aceptacion/LP02.json` |
| CRUD01 | catálogo público | `POST/PUT/GET/DELETE de hotel, piso, categoría, habitación y producto` | `python scripts/qa/matriz.py lp-crud` | ✅ | `tests/evidencia/qa-matriz-aceptacion/CRUD01.json` |
| OCC01 | concurrencia | `dos POST /api/recepcion/registrar simultáneos` | `python scripts/qa/matriz.py occ-cln` | ✅ | `tests/evidencia/qa-matriz-aceptacion/OCC01.json` |
| OCC02 | concurrencia | `POST /api/recepcion/registrar y POST /api/habitacion/{id}/mantenimiento` | `python scripts/qa/matriz.py occ-cln` | ✅ | `tests/evidencia/qa-matriz-aceptacion/OCC02.json` |
| OCC03 | concurrencia | `reintento del blockId R y nueva intención de mantenimiento` | `python scripts/qa/matriz.py occ-cln` | ✅ | `tests/evidencia/qa-matriz-aceptacion/OCC03.json` |
| OCC04 | concurrencia | `DELETE /api/piso/eliminar/{id} y /api/categoria/eliminar/{id} con habitación activa` | `python scripts/qa/matriz.py occ-cln` | ✅ | `tests/evidencia/qa-matriz-aceptacion/OCC04.json` |
| CLN01 | limpieza | `PUT /api/habitacion/{id}/limpieza-completada con Rabbit pausado` | `python scripts/qa/matriz.py occ-cln` | ✅ | `tests/evidencia/qa-matriz-aceptacion/CLN01.json` |
| CLN02 | limpieza | `comando limpieza.solicitada C1 y HabitacionLista viejo` | `python scripts/qa/matriz.py occ-cln` | ✅ | `tests/evidencia/qa-matriz-aceptacion/CLN02.json` |
| CLN03 | limpieza | `HabitacionLista posterior al cierre + proyecciones de reporting` | `python scripts/qa/matriz.py occ-cln` | ✅ | `tests/evidencia/qa-matriz-aceptacion/CLN03.json` |
| STK01 | stock y venta | `dos POST /api/venta ×15 sobre stock 20` | `python scripts/qa/matriz.py stk` | ✅ | `tests/evidencia/qa-matriz-aceptacion/STK01.json` |
| STK02 | stock y venta | `POST /api/venta multítem que excede un ítem` | `python scripts/qa/matriz.py stk` | ✅ | `tests/evidencia/qa-matriz-aceptacion/STK02.json` |
| STK03 | stock y venta | `POST /api/venta con la Idempotency-Key de F2` | `python scripts/qa/matriz.py stk` | ✅ | `tests/evidencia/qa-matriz-aceptacion/STK03.json` |
| STK04 | stock y venta | `DELETE /api/venta/{id} con débito en vuelo y débito tardío` | `python scripts/qa/matriz.py stk` | ✅ | `tests/evidencia/qa-matriz-aceptacion/STK04.json` |
| STK05 | stock y venta | `job de recuperación cada 30 s sobre F7` | `python scripts/qa/matriz.py stk` | ✅ | `tests/evidencia/qa-matriz-aceptacion/STK05.json` |
| STK06 | stock y venta | `comando recepcion.operacion-finalizar RECHAZADA antes de admitir` | `python scripts/qa/matriz.py stk` | ✅ | `tests/evidencia/qa-matriz-aceptacion/STK06.json` |
| STK07 | stock y venta | `worker caído tras DEBITO_SOLICITADO y reinicio` | `python scripts/qa/matriz.py stk` | ✅ | `tests/evidencia/qa-matriz-aceptacion/STK07.json` |
| PAY01 | pagos y cierre | `POST /api/venta como CLIENTE con estado PAGADO y con precio` | `python scripts/qa/matriz.py pay-out` | ✅ | `tests/evidencia/qa-matriz-aceptacion/PAY01.json` |
| PAY02 | pagos y cierre | `DELETE /api/venta/{id} sobre F3 pagada` | `python scripts/qa/matriz.py pay-out` | ✅ | `tests/evidencia/qa-matriz-aceptacion/PAY02.json` |
| OUT01 | pagos y cierre | `POST /api/recepcion/registrar-salida con venta en vuelo` | `python scripts/qa/matriz.py pay-out` | ✅ | `tests/evidencia/qa-matriz-aceptacion/OUT01.json` |
| OUT02 | pagos y cierre | `reintento del cierre de F4 con el mismo closureId` | `python scripts/qa/matriz.py pay-out` | ✅ | `tests/evidencia/qa-matriz-aceptacion/OUT02.json` |
| OUT03 | pagos y cierre | `POST /api/recepcion/registrar-salida con monto inexacto y penalidad` | `python scripts/qa/matriz.py pay-out` | ✅ | `tests/evidencia/qa-matriz-aceptacion/OUT03.json` |
| OUT04 | pagos y cierre | `POST /api/recepcion/registrar-salida sin ventas` | `python scripts/qa/matriz.py pay-out` | ✅ | `tests/evidencia/qa-matriz-aceptacion/OUT04.json` |
| OUT05 | pagos y cierre | `matar reception en VALIDANDO y en CONFIRMANDO` | `python scripts/qa/matriz.py pay-out` | ✅ | `tests/evidencia/qa-matriz-aceptacion/OUT05.json` |
| OUT06 | pagos y cierre | `PUT /api/recepcion/actualizar/{id} que deja precioInicial < adelanto` | `python scripts/qa/matriz.py pay-out` | ✅ | `tests/evidencia/qa-matriz-aceptacion/OUT06.json` |
| MSG01 | mensajería | `F2 commiteada con Kafka detenido y reanudación` | `python scripts/qa/matriz.py msg` | ✅ | `tests/evidencia/qa-matriz-aceptacion/MSG01.json` |
| MSG02 | mensajería | `duplicados, versión vieja y orden invertido` | `python scripts/qa/matriz.py msg` | ✅ | `tests/evidencia/qa-matriz-aceptacion/MSG02.json` |
| REP01 | reportes | `GET /api/reportes/dashboard y GET /api/reportes/ventas` | `python scripts/qa/matriz.py rep / rep03_sin_bootstrap` | ✅ | `tests/evidencia/qa-matriz-aceptacion/REP01.json` |
| REP02 | reportes | `rebuild de reporting en DB nueva` | `python scripts/qa/matriz.py rep / rep03_sin_bootstrap` | ✅ | `tests/evidencia/qa-matriz-aceptacion/REP02.json` |
| REP03 | reportes | `arranque en demo sin ventas` | `python scripts/qa/matriz.py rep / rep03_sin_bootstrap` | ✅ | `tests/evidencia/qa-matriz-aceptacion/REP03.json` |
| NOT01 | notificaciones | `cierre de F1 con Mailpit caído y repetición de notificacion.checkout` | `python scripts/qa/matriz.py not` | ✅ | `tests/evidencia/qa-matriz-aceptacion/NOT01.json` |
| NOT02 | notificaciones | `respuesta tardía con lease vencido` | `python scripts/qa/matriz.py not` | ✅ | `tests/evidencia/qa-matriz-aceptacion/NOT02.json` |
| NOT03 | notificaciones | `CLI notification-service --admin purge --before sobre ENVIADA antigua y PENDIENTE/ERROR` | `python scripts/qa/matriz.py not` | ✅ | `tests/evidencia/qa-matriz-aceptacion/NOT03.json` |

## Estados inducidos

Los casos que necesitan un estado difícil de reproducir lo dicen en su archivo de
evidencia, en el campo `inducido`: F4 (advisory lock de Sales), F5 (reception detenido),
F7/STK04/STK05/STK07 (proxy TCP que cuelga el débito), OUT01/OUT05 (lock + proceso
matado), NOT02 y NOT03 (lease vencido y job ENVIADA antiguo por SQL). Ninguno se presenta
como natural.

## Configuración

```bash
bash scripts/ejecutar-matriz.sh --help
python scripts/qa/matriz.py <grupo>
bash scripts/ejecutar-matriz-completa.sh   # corrida completa en orden
```
