# Implementación, migración y doble validación

## 1. Base inspeccionada y cambios intencionales

Referencia: commit `c10af777cc166319a7dff26eb9efd8cd09f60096`. No se utilizó el plan antiguo.

| Hallazgo en monolito | Decisión objetivo |
|---|---|
| Boot3.5.0/Java21, Angular21, PostgreSQL | Migrar familias a Boot4.1.x/Java25, Angular22, PostgreSQL18; al implementar fijar patches compatibles y lockfiles |
| Seguridad permite /api/persona/** y producto sin roles suficientes | Default deny, DTO separados, roles/sede/propietario por servicio |
| Registrar persona acepta idTipoPersona del request | Registro público específico solo CLIENTE; creación de personal solo ADMIN |
| DTO Persona tiene clave, mapper de salida inspeccionado no la asigna | No afirmar fuga de hash observada; retirar campo del DTO de salida y probarlo |
| sp_RegistrarRecepcion desactiva estadía anterior de misma habitación | Reemplazar por409 + índice único; nunca cierre implícito |
| Recepción puede crear persona implícitamente | Crear cliente antes; recepción requiere idCliente validado |
| Fecha de entrada real se fija CURRENT_DATE aunque DTO tenga fechaEntrada | MVP de check-in inmediato, no sistema de reservas futuras |
| sp_RegistrarVenta descuenta PRODUCTO | Descuento pasa solo a inventory; sales no conserva escritura a PRODUCTO |
| fn_agregaralcarrito se invoca desde repositorio pero no aparece en scripts | Implementar función de carrito local; no atribuirle un comportamiento no verificado |
| sp_RegistrarSalida modifica recepción, ventas y habitación | Separar cierre idempotente, gate local, comando de limpieza |
| Venta.estado es PENDIENTE/PAGADO y reportes filtran PAGADO | Mantener estado de pago, añadir estadoOperacion |
| DELETE venta puede borrar filas/historial | Anulación impagada con restitución, sin borrado físico |
| Estado habitación2 significa OCUPADO | Sacar ocupación del catálogo físico; migración semántica explícita |
| Carrito devuelve texto/array en vez de ApiResponse | Homogeneizar; adaptar cliente Angular en mismo PR |
| No DetalleVentaController real | No inventar microservicio ni CRUD separado; líneas dentro de venta |

## 2. Orden simple elegido

Corte coordinado con ventana de mantenimiento para este proyecto académico, no migración en vivo con dos escritores.

1. Base: Maven/contratos comunes, seguridad, Gateway/Eureka, Compose infra y CI.
2. Identity y Hotel: BD/seeds, permisos, catálogo público y pantallas base.
3. Reception: gates, snapshots, check-in, admisión de operaciones.
4. Inventory + Sales juntos como módulo de integración: débito, compensación, carrito, pagos; nunca activar inventory separado mientras el monolito todavía escribe PRODUCTO.
5. Cierre reception↔sales y limpieza hotel↔reception; operar ambos juntos. No mitad del flujo viejo con mitad nuevo.
6. Notification y Reporting consumiendo productores nuevos; bootstrap.
7. Adaptar admin Angular a contrato nuevo; public-web solo catálogo. Ejecutar flujos completos.
8. Corte final de rutas por grupos, con frontend compatible desplegado junto al Gateway.

Reporting no va “primero” sin datos. No introducir Debezium para evitar mapear eventos: outbox explícito en nuevos servicios y bootstrap controlado bastan.

## 3. Importación de datos existentes

Para demo desde cero: Flyway + seeds limpios. Para conservar datos reales del monolito: procedimiento distinto, NO ejecutar seeds encima.

1. Backup verificado restaurable de BD original. Fijar commit de apps y congelar escrituras (incluidas SP, procesos y usuarios SQL).
2. Auditar duplicados de correo/documento, múltiples estadías activas, stock negativo, ventas sin recepción/detalles y pagos inconsistentes. Generar reporte; no fusionar clientes ni corregir importes silenciosamente.
3. Crear nuevas DB vacías. Migrar IDs y snapshots; todos los datos originales son sede1 salvo mapeo explícito aprobado. Sedes2/3 son demo, no se inventa reparto de historia.
4. Importar identity; validar hashes BCrypt, requerir reset seguro para credenciales incompatibles. No transformar claves desconocidas en cuentas con password común.
5. Importar hotel y catálogo. Habitaciones OCUPADO necesitan recepción activa consistente; gate OCUPADA se importa en reception. Físico LISTA no equivale a libre. Estado legacy MANTENIMIENTO se conserva bloqueado hasta revisión.
6. Importar inventory, sales y reception preservando referencias por ID sin FKs entre DB. Ventas previas válidas se marcan CONFIRMADA, estado de pago original; no generar descuentos retrospectivos (stock ya los refleja). Ventas PAGADAS exigen paid_at verificable: si el origen no tiene fecha de pago, no sustituirla por fecha de creación. Esa historia queda en el legado de solo lectura hasta conciliación aprobada; no se importa a reportes de ingreso por día con fechas inventadas.
7. Para venta heredada no existe movimiento inventario demostrable: no permitir anulación automática; marcar origen=LEGACY y bloquear restitución hasta conciliación. El schema de sales ya define origen=NUEVA/LEGACY; importar con LEGACY. No improvisar movimientos.
8. Copiar estados de limpieza/bloqueos de manera conservadora y correlacionada. Crear ciclos/tareas iniciales para limpieza pendiente, no desbloquear por importar LISTA.
9. Bootstrap snapshots y watermarks con tráfico congelado. Cargar reporting, publicar estado inicial y establecer checkpoints/inbox; procesar proyecciones receptoras.
10. Verificar counts, checksums de claves, saldos, ingresos por rangos y permisos. Secuencias ajustadas después del mayor ID.
11. Cambiar rutas/front y abrir tráfico nuevo; revocar credenciales de escritura del monolito.

Rollback ANTES de nuevas escrituras: restaurar rutas al monolito preservado. DESPUÉS de escrituras nuevas: congelar, exportar y reconciliar; no devolver tráfico a una BD vieja perdiendo ventas. No se promete rollback instantáneo.

## 4. Cambios frontend obligatorios

- URL base del Gateway; nunca puertos individuales ni broker.
- Selección de sede para personal; guardar idHotel por flujo, no como autorización.
- JWT en memoria, guards y manejo401/403/404. Vista privada cliente separada de menú administrativo.
- Nuevo catálogo de cliente vía sales; LP solo /api/public.
- No enviar campos calculados del DTO viejo en POST/PUT. Conservar adaptadores de nombres donde sea seguro.
- EstadoOperacion separado del pago en tablas;202 muestra “pendiente”, no éxito definitivo.
- Idempotency-Key creada una vez por intención; conservar en reintento. Después de409 validación corregida, nueva intención/nueva clave.
- ETag→If-Match en ediciones;412 pide recargar, no sobrescribir.
- Cierre muestra desglose y “no volver a cobrar” si está en recuperación.
- Quitar social login, reserva futura y CRUD de códigos fijos del MVP.
- DELETE de maestros baja lógica y ventas anulación; texto de UI preciso.

## 5. Matriz de aceptación funcional

| ID | Prueba | Resultado exigido |
|---|---|---|
| SEC01 | Público consulta /api/persona/listar | 401 |
| SEC02 | Registro público intenta rol ADMIN | 400, ninguna elevación |
| SEC03 | E sede1 intenta room201 o reporte sede2 | 404 sin filtración |
| SEC04 | C cambia idRecepcion ajena en carrito/ventas | 404 |
| SEC05 | Token humano llama /internal/stock/descontar | 403/no ruta Gateway |
| SEC06 | JWT algoritmo/issuer/aud incorrectos | 401 en cada servicio |
| SEC07 | Dos ADMIN se degradan/inactivan simultáneamente por PUT/DELETE | Queda al menos un ADMIN activo con credenciales; uno recibe409 |
| SEC08 | Angular hace preflight con If-Match/Idempotency-Key | Orígenes permitidos aceptados; ETag/Location/Retry-After legibles; origen ajeno no permitido |
| LP01 | Visitar4 endpoints públicos sin token | 200, solo DTO públicos |
| LP02 | Buscar disponibilidad futura o datos de huésped en LP | No existe funcionalidad/ningún dato privado |
| CRUD01 | Crear/editar/listar/dar de baja maestros | DB persistida y UI refleja todos los verbos |
| OCC01 | Dos check-ins simultáneos misma room | Uno201,otro409 |
| OCC02 | Check-in y bloqueo mantenimiento simultáneos | Solo una admisión válida |
| CLN01 | Pausar Rabbit, cerrar y reintentar check-in | 409 hasta ciclo de limpieza correcto |
| CLN02 | Repetir comando limpieza / evento viejo | No reabrir tarea ni liberar ciclo actual |
| CLN03 | HabitacionLista llega antes de SalidaRegistrada, o después de otro check-in | Reporte reconcilia ciclo sin liberar ocupación posterior; comparar versiones del mismo origen |
| OCC03 | Reintentar blockId rechazado después de quedar habitación libre | Sigue409; nueva intención usa nuevo blockId |
| OCC04 | Dar de baja padre y crear/reactivar habitación concurrentemente | No queda habitación activa bajo padre inactivo |
| STK01 | Dos ventas15 sobre stock20 | Una confirmada; stock5 |
| STK02 | Multítem, una línea insuficiente | Ningún descuento parcial |
| STK03 | Repetir venta misma clave | Mismo id y un débito |
| STK04 | Restituir antes de débito tardío | Lápida rechaza débito, stock intacto |
| STK05 | Timeout después de débito + reconciliador | Estado terminal consistente, sin confirmar tras compensar |
| STK06 | Timeout de admisión, finalizar llega primero y luego respuesta de admitir | Lápida con tipo completo; CAS impide iniciar débito tardío |
| STK07 | Worker cae tras DEBITO_SOLICITADO, antes o después de HTTP | Misma compensación segura en ambos casos, no liberar antes de RESTITUIDO |
| PAY01 | C solicita estado PAGADO o precio reducido | 403/400, no cobro/precio adulterado |
| PAY02 | DELETE venta ya PAGADA | 409, historial intacto |
| OUT01 | Venta admitida compite con cierre | Cierre409, no consumo después de cierre |
| OUT02 | Sales confirma cierre y reception cae | Reintento mismo closureId, un receipt y una salida |
| OUT03 | Penalidad/total incorrecto | 409, no pago confirmado; cierre RECHAZADO conservado |
| OUT04 | Cerrar estadía sin ventas | Receipt con totales0 de consumos y sede correcta; cierre normal |
| OUT05 | Crash en VALIDANDO / CONFIRMANDO / después de receipt | Solo VALIDANDO recalcula; CONFIRMANDO reenvía payload congelado; un cobro/salida |
| OUT06 | Acortar estadía con precio nuevo menor que adelanto | 409 sin alterar precio ni adelanto |
| MSG01 | Broker caído luego de commit | Outbox recupera, efecto se procesa una vez |
| MSG02 | Duplicados y orden invertido | Inbox/version/ciclo mantienen invariantes |
| REP01 | Pago directo y posterior cierre | Ingreso no duplicado |
| REP02 | Rebuild con snapshots+eventos | Mismos totales y claves |
| REP03 | Bootstrap de demo sin ventas/estadías y topics vacíos | LISTO al cumplir manifiesto/watermarks, sin esperar mensajes inexistentes |
| NOT01 | SMTP caído o comando repetido | Negocio no bloqueado; un job, retries acotados |
| NOT02 | Expira lease y responde tarde el worker anterior | Su token no modifica el nuevo trabajo; duplicado SMTP sigue siendo limitación explícita |
| NOT03 | Retención elimina destinatario/variables de ENVIADA | CHECK permite purga y conserva hash/estado; no purgar jobs pendientes |

## 6. Doble validación antes de implementar y antes de entregar

Validación documental: dueños únicos, routes sin colisiones, bodies/headers/types, sedes, productores/consumidores emparejados, seeds compatibles, todos los estados con salida/recuperación.

Validación ejecutable futura: unitarias de reglas y JWT; tests PostgreSQL reales (no sustituir locks por H2); integración con brokers reales/Testcontainers; colección Postman por flujo; Angular build + pruebas de roles/pantallas. CI ejecuta mvn verify y npm ci/build/test de cada app. Comandos concretos se fijarán al crear módulos.

No marcar tests de esta tabla como pasados solo por existir el documento. El código actual sigue siendo monolito y no implementa todavía estas garantías.

## 7. Entregables de implementación

OpenAPI por servicio y JSON Schemas de mensajes; migraciones/seed scripts separados; Dockerfiles/Compose con healthchecks; colecciones Postman; evidencia de matriz; README de arranque; informe/presentación exigidos por la guía académica. Sin secretos, dumps personales, target/node_modules ni metadatos de IDE.
