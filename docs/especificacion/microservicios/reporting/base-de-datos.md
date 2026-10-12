# Reporting — base de datos, procedimientos y seeds

BD `reporting_db`. Read model de Kafka; nunca consulta DB ajenas en un reporte. [Comunes](../../03-contrato-comun.md).

## Tablas de proyección

| Tabla | Columnas |
|---|---|
| persona_projection | id_persona:int PK; nombre:varchar(100); apellido:varchar(100); id_tipo_persona:int; estado:boolean; source_version:bigint |
| hotel_projection | id_hotel:int PK; nombre:varchar(120); ciudad:varchar(80); estado:boolean; source_version:bigint |
| habitacion_projection | id_habitacion:int PK; id_hotel:int; numero:varchar(10); categoria_nombre:varchar(80); precio:numeric(12,2); estado:boolean; estado_fisico:varchar(24); ocupada:boolean?; hotel_version:bigint; room_version:bigint |
| recepcion_projection | id_recepcion:int PK; id_hotel:int; id_habitacion:int; id_cliente:int; numero:varchar(10); nombre_cliente:varchar(200); fecha_entrada/fecha_salida:date; fecha_cierre:timestamptz?; estado_estadia:varchar(16); precio_inicial,adelanto,penalidad:numeric(12,2); total_alojamiento,total_consumos,total_general,cobro_ahora:numeric(12,2)? (obligatorios en CERRADA); closure_id:uuid?; archivada:boolean; source_version:bigint |
| stock_projection | id_hotel:int; id_producto:int; nombre:varchar(120); precio:numeric(12,2); cantidad:int; umbral:int; estado:boolean; source_version:bigint; PK(id_hotel,id_producto) |
| venta_projection | id_venta:int PK; id_hotel:int; id_recepcion:int; estado:varchar(12); estado_operacion:varchar(24); total:numeric(12,2); created_at:timestamptz; paid_at:timestamptz?; source_version:bigint |
| venta_detalle_projection | id_venta:int FK local; id_producto:int; nombre:varchar(120); cantidad:int; precio/subtotal:numeric(12,2); PK(id_venta,id_producto) |
| limpieza_completada_projection | cleaning_cycle_id:uuid PK; id_habitacion:int; cleaning_room_version:bigint; hotel_version:bigint; completed_at:timestamptz. Recuerda completado aunque SalidaRegistrada llegue después |
| projection_checkpoint | topic:varchar(120); partition:int; last_offset:bigint default -1; last_event_at:timestamptz?; updated_at:timestamptz; degraded:boolean default false; PK(topic,partition) |
| projection_bootstrap | id:int PK CHECK id=1; estado:varchar(16) CARGANDO/LISTO; manifest:jsonb; completed_at:timestamptz? |
| inbox | Deduplicación; común |

Sin FK entre proyecciones que llegan por topics diferentes (persona puede llegar después que recepción). Conservar snapshots necesarios en recepción/venta. Actualizar upsert solo si versión nueva: recepcion_projection usa payload.version de su idRecepcion; gate/ocupación usan roomVersion y ocupacionActual. Archivar una estadía histórica no libera la habitación ocupada de nuevo. Detalles de venta se reemplazan en la misma transacción del snapshot.

Índices venta_projection(id_hotel,paid_at), recepcion_projection(id_hotel,fecha_cierre), stock_projection(id_hotel,cantidad). No tablas outbox ni API idempotency: solo consultas; replay operacional offline.

## Funciones SQL

| Firma | Salida/regla |
|---|---|
| fn_reporte_productos_bajo_stock(p_hotel int,p_limite int) RETURNS TABLE(id_producto int,nombre_producto text,cantidad int,precio numeric,estado boolean) | Cantidad<=límite, solo productos activos |
| fn_reporte_ventas(p_hotel int,p_inicio timestamptz,p_fin timestamptz) RETURNS TABLE(id_producto int,nombre_producto text,cantidad_total bigint,total_ingresado numeric) | Solo CONFIRMADA/PAGADO, por paid_at en [inicio,fin) |
| fn_reporte_ocupacion(p_hotel int,p_inicio timestamptz,p_fin timestamptz) RETURNS TABLE(id_habitacion int,numero_habitacion text,descripcion_categoria text,veces_alquilada bigint) | Cuenta check-ins por fechaEntrada en rango de fechas Lima; no “porcentaje” sin denominador |
| fn_reporte_cobros(p_hotel int,p_inicio timestamptz,p_fin timestamptz) RETURNS TABLE(id_recepcion int,numero_habitacion text,nombre_cliente text,total_alojamiento numeric,total_consumos numeric,total_general numeric,fecha_cierre timestamptz) | Recepciones CERRADAS por fecha_cierre; usa receipt snapshot, no vuelve a sumar ventas |
| fn_dashboard(p_hoteles int[],p_dia date) RETURNS jsonb | Habitaciones ocupadas/disponibles y productos bajo stock; ingresosHoy por pagos efectivos del día |

IngresosHoy = adelantos de check-ins de hoy + cobro final de ALOJAMIENTO en cierres de hoy + ventas PAGADAS según paid_at de hoy. No sumar totalGeneral de cierre además de esas ventas. Para representar cobro final de alojamiento, deriva total_alojamiento-adelanto. Estadía activa aún no tiene total_alojamiento final; adelanto ya existe en su snapshot.

HabitacionesDisponibles: activas, física LISTA, sin ocupación y sin limpieza pendiente; proyección puede atrasarse, nunca se usa para check-in. Para detectar gate de limpieza pendiente, SalidaRegistrada marca disponibilidad false hasta HabitacionLista del ciclo correspondiente: persistir gate_projection detallado abajo.

`gate_projection(id_habitacion int PK,estado varchar(24),cleaning_cycle_id uuid?,block_id uuid?,hotel_version bigint,room_version bigint)`. Se crea aunque el catálogo de habitación aún no haya llegado; no tiene FK a habitacion_projection. Hotel inicializa solo la parte física y no reemplaza un gate ya proyectado desde reception. Se alimenta de eventos físicos y recepción; prioriza bloqueo/ciclo frente a snapshot genérico LISTA. HabitacionLista registra también limpieza_completada_projection. Si SalidaRegistrada llega después, consulta ese ciclo ya completado y no deja un bloqueo eterno. HabitacionLista trae cleaningRoomVersion de reception, que se conserva junto con el ciclo (no se compara hotel.version contra roomVersion). Solo libera una proyección LIMPIEZA_PENDIENTE con el mismo ciclo; no cambia una OCUPADA posterior. Si todavía no llegó la salida, únicamente guarda el completado y espera su snapshot. Cada snapshot de reception se aplica por roomVersion y ocupacionActual; luego se reconcilia contra los ciclos completados. Habilitaciones usan blockId coincidente, no un LISTA genérico. Esta regla funciona en ambos órdenes entre topics. Métricas eventualmente consistentes se muestran como tales.

## Seeds y bootstrap

No inventar ventas, cobros ni estadísticas. Base vacía; cargar snapshots de los cinco dominios en bootstrap, luego stream. Los seeds iniciales deberían proyectar3 sedes,6 habitaciones,3 productos×3sedes,6 personas,0 ventas/estadías.

projection_bootstrap inicia CARGANDO; el manifiesto contiene conteos esperados por dominio y watermarks topic/partición de una exportación o seed terminado. El job crea checkpoints para todas las particiones suscritas, incluidas las vacías (last_offset=-1, last_event_at=null, updated_at=hora bootstrap). Marca LISTO en transacción solo tras cargar snapshots y alcanzar los watermarks registrados. No esperar el primer evento de ventas/estadías vacías. Hasta entonces API503 REPORTING_NOT_READY; bootstrapComplete=(estado=LISTO). Para demo0 ingresos es correcto solo después de bootstrap.

Para reconstrucción: snapshot exportado de DB propietarias con watermark/version durante ventana de mantenimiento, importar a DB reporting nueva y reproducir eventos posteriores. No depender de7d de Kafka para historia anterior.

## Validación

Mismo eventId dos veces no duplica ingresos; versión vieja no rebaja snapshot; pago ya recibido antes de cierre no se cuenta dos veces; reordenar recepción y venta mantiene reportes coherentes al ponerse al día; ARCHIVADA conserva historia de cobro.
