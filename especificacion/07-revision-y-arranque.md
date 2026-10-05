# Revisión de coherencia y arranque — 2026-10-05

## Dictamen y alcance

La división de responsabilidades, el monorepo, las dos aplicaciones Angular y Docker Compose se mantienen. La versión anterior tenía incoherencias en recuperación, concurrencia y algunos contratos; esta revisión las corrige en los documentos fuente, no solo en este resumen.

No se leyó ni modificó el plan antiguo. El código del monolito de referencia no se cambió; este repositorio contiene documentación, no los servicios implementados. Esta es una base de implementación, no certificación de servicios ya ejecutados.

## Correcciones aplicadas

| Problema anterior | Regla corregida | Contratos afectados |
|---|---|---|
| Sales podía pagar y Reception caer antes de guardar el recibo; el retry podía recalcular saldo0 | Cierre VALIDANDO→CONFIRMANDO→COMPLETADO; cálculo congelado y búsqueda de recibo antes de recalcular; RECHAZADO no se borra | Flujos, Reception, Sales |
| Finalizar antes de admitir no podía insertar tipo NOT NULL; callback tardío podía iniciar débito | Mensaje completo con tipo y tupla validada; estado DEBITO_SOLICITADO y CAS antes del HTTP; lápida terminal | Mensajería, Reception, Sales |
| blockId rechazado podía aceptarse más tarde y dejar un bloqueo sin dueño | Persistir rechazo por clave; aplicar mantenimiento solo PREPARANDO→APLICADA | Reception, Hotel |
| Se protegía último ADMIN en DELETE, no en degradación/inactivación por PUT | Mutex sobre rol ADMIN y comprobación en todas las mutaciones de rol/estado | Identity |
| Versiones físicas y de ocupación podían confundirse; evento de limpieza podía llegar antes de salida | Ciclo con cleaningRoomVersion, historial de completados y reconciliación sin liberar una ocupación posterior | Hotel, Reception, Reporting |
| Bootstrap dependía de mensajes que no existen en una demo sin ventas | Manifiesto/watermarks y checkpoints de particiones vacías; LISTO explícito | Reporting |
| Angular no tenía declarados headers necesarios; reintento podía fallar por la versión que él mismo cambió | CORS completo; autenticar, resolver idempotencia y solo para intención nueva evaluar If-Match | Común, Gateway |
| Lease SMTP y purga no coincidían con el esquema | Token de lease; destinatario/variables nullable solo tras envío/purga; auditoría y reintentos acotados | Notification |
| Baja de padre competía con alta/reactivación de habitación; producto tenía estado ambiguo | Locks locales de padres; sede/número inmutables; estado de producto editado por sede | Hotel, Inventory |
| Cierre sin ventas no podía determinar sede; importación podía inventar fecha de pago | idHotel explícito en resumen y receipt de0; historia sin paid_at verificable no se importa como ingreso fechado | Sales, migración |

Los rechazos durables se guardan mediante commit de un resultado de negocio; no se lanza una excepción que borre la lápida al hacer rollback.

## Decisiones cerradas para el equipo

- Siete servicios de aplicación: Identity, Hotel, Reception, Inventory, Sales, Reporting y Notification; este último es worker. Gateway/Eureka son infraestructura.
- No añadir ahora servicios separados de reserva, pago, cliente o limpieza.
- REST para ambos frontends; Feign para consultas/acciones síncronas internas; Kafka para hechos y proyecciones; RabbitMQ para trabajos dirigidos.
- Landing informativa sin reserva/pago ni disponibilidad garantizada; cliente autenticado opera en el área privada.
- Stock solo Inventory; ocupación solo Reception; físico/limpieza Hotel; pagos manuales de consumos Sales.
- Una DB por servicio, un PostgreSQL local para la demo. No transacciones, FK ni joins entre DB.
- Primera implementación desde seeds limpios; importar historia del monolito es otro procedimiento controlado.
- Límites aceptados: reportes eventualmente consistentes, cambios de permisos con demora máxima del TTL del JWT y posible correo duplicado ante caída SMTP. Ninguno autoriza duplicar un cobro ni descontar stock dos veces.

## Primer incremento implementable

1. Crear estructura objetivo, fijar versiones compatibles y contratos OpenAPI/JSON Schema desde estos Markdown.
2. Compose de infraestructura, migraciones separadas, seguridad máquina/humana, Gateway y Eureka; sin exponer rutas internas.
3. Identity y Hotel con seeds, permisos, catálogo público y LP. Probar login, aislamiento de sedes y protección del último ADMIN.
4. Reception: gate/check-in; Inventory y Sales: operación admitida, débito y compensación.
5. Completar cierre/limpieza, worker de correo y proyecciones de Reporting; adaptar panel.
6. Ejecutar la [matriz de aceptación](06-migracion-y-pruebas.md) con PostgreSQL y brokers reales antes de la demostración.

No hace falta decidir otra arquitectura para empezar. Los parches exactos de dependencias, migraciones SQL y schemas ejecutables se fijan en el primer incremento: son trabajo de implementación, no capacidades presentes en el monolito.

## Qué valida esta revisión

Se contrastan enlaces internos, ejemplos JSON, rutas documentadas y correspondencia de productores/consumidores, además de recorrer los fallos de cierre, admisión tardía, bloqueo, permisos, limpieza y bootstrap contra sus estados persistidos.

Comprobación del paquete de especificación publicado: 24 Markdown, 42 enlaces relativos válidos, 15 bloques JSON parseables, 98 declaraciones método/ruta sin duplicados y cobertura de las 59 rutas del monolito (incluidas las retiradas explícitamente). Se contrastaron productor/consumidor de los 7 topics Kafka y 6 comandos RabbitMQ. Durante la revisión local, el diff del monolito permaneció vacío; la publicación en este repositorio conserva la carpeta plans sin cambios.

La matriz se amplió con casos concretos para estos fallos. Son pruebas de aceptación pendientes de implementación; no se marcan como ejecutadas por estar escritas. La verificación documental no sustituye tests de concurrencia, seguridad e integración.
