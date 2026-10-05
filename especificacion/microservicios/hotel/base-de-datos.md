# Hotel — base de datos, procedimientos y seeds

BD `hotel_db`. Datos físicos y catálogo público. [Convenciones comunes](../../03-contrato-comun.md); campos ? admiten NULL.

## Tablas

| Tabla | Columnas |
|---|---|
| hotel | id:int PK identity; slug:varchar(80) UNIQUE; nombre:varchar(120); descripcion:text; direccion:varchar(200); ciudad:varchar(80); telefono:varchar(30); correo_contacto:varchar(254); estado:boolean; publicado:boolean; version:bigint; created_at:timestamptz |
| categoria | id:int PK identity; descripcion:varchar(80) UNIQUE; detalle:text; capacidad:int CHECK1..10; estado:boolean; version:bigint |
| piso | id:int PK identity; id_hotel:int FK hotel; descripcion:varchar(80); estado:boolean; version:bigint; UNIQUE(id_hotel,descripcion) |
| estado_habitacion | id:int PK fijo; codigo:varchar(30) UNIQUE; descripcion:varchar(80); estado:boolean; version:bigint |
| habitacion | id:int PK identity; id_hotel:int FK; id_piso:int FK; id_categoria:int FK; numero:varchar(10); detalle:text; precio:numeric(12,2)>0; id_estado_habitacion:int FK; estado:boolean; publicada:boolean; version:bigint; UNIQUE(id_hotel,numero) |
| imagen_habitacion | id:int PK identity; id_habitacion:int FK; url:varchar(500); alt:varchar(150); orden:int; UNIQUE(id_habitacion,orden) |
| limpieza | cleaning_cycle_id:uuid PK; id_habitacion:int FK; id_recepcion:int externo; room_version:bigint; estado:varchar(20) PENDIENTE/COMPLETADA; created_at/completed_at?:timestamptz; version:bigint |
| ocupacion_projection | id_habitacion:int PK FK; id_recepcion:int?; estado_estadia:varchar(16); room_version:bigint; updated_at:timestamptz. Solo visual |
| operacion_habitacion | block_id:uuid PK; id_habitacion:int FK; tipo:varchar(20) MANTENIMIENTO/BAJA; estado:varchar(20) PREPARANDO/APLICADA/LIBERADA/RECHAZADA; request:jsonb; attempts:int; next_attempt_at:timestamptz |
| api_idempotency,outbox,inbox,audit_log | Comunes |

Validar piso pertenece a misma sede mediante FK compuesta (piso.id,id_hotel) o constraint trigger; no solo validación frontend. Índices habitaciones por sede/categoría/activo y limpieza por habitación/estado; UNIQUE parcial una limpieza PENDIENTE por habitación. Un mismo número101 puede existir en sedes diferentes.

Códigos físicos fijos:1 LISTA,2 EN_LIMPIEZA,3 MANTENIMIENTO. NO reutilizar numéricamente los estados legacy: antes2 significaba OCUPADO. Migración transforma por semántica, no copia IDs a ciegas. Ocupación no es fila del catálogo físico.

## Procedimientos

| Firma | Comportamiento |
|---|---|
| fn_habitacion_guardar(p_id int?,p_datos jsonb,p_version bigint?) RETURNS habitacion | Catálogo/precio/imágenes; comprueba sede/piso/categoría; no permite cambiar físico ni sede/número de habitación usada |
| sp_limpieza_solicitar(p_ciclo uuid,p_habitacion int,p_recepcion int,p_room_version bigint) | Inbox+upsert ciclo; EN_LIMPIEZA; duplicados/viejos no reabren ciclos completos |
| sp_limpieza_completar(p_ciclo uuid,p_version bigint) | Lock tarea y habitación; PENDIENTE→COMPLETADA, físico LISTA y aumento version |
| sp_habitacion_aplicar_bloqueo(p_block uuid,p_tipo varchar) | Solo después de Feign exitoso; lock operación; MANTENIMIENTO o baja lógica |
| sp_habitacion_habilitar(p_block uuid) | Operación aplicada; LISTA, estado=true; emite habilitación correlacionada |
| sp_hotel_desactivar(p_id int,p_version bigint) | Solo si no quedan habitaciones activas |
| fn_catalogo_publico(p_slug varchar?,p_categoria int?) RETURNS TABLE(id_hotel int,slug varchar,nombre varchar,id_categoria int,descripcion varchar,capacidad int,precio_desde numeric,imagenes jsonb) | Solo sedes publicadas/activas y categorías con habitaciones publicadas/activas; precio mínimo y galería curada, no ocupación |

JPA para CRUD de sede/categoría/piso y lectura HotelPublico; fn_catalogo_publico entrega categorías/tarifa/galería agrupadas y el service añade detalle de categoria para CategoriaPublica. Filtros nulos devuelven todos los publicados; nunca datos operativos de cada habitación. No invoca reception dentro de SQL.

Al persistir PREPARANDO, otras mutaciones físicas/catálogo de esa habitación devuelven409 hasta aplicar o rechazar la operación. Índice único parcial: una operación PREPARANDO/APLICADA no liberada por habitación. La baja conserva blockId hasta reactivación. Reception gate se obtiene por Feign antes de mantenimiento/baja, fuera de transacción SQL. Jobs recuperan operacion_habitacion PREPARANDO; nunca se “arregla” desocupando una habitación por SQL.

## Seeds deterministas

| idHotel / slug | nombre | ciudad |
|---|---|---|
| 1 / lima-centro | Hotel Demo Lima Centro | Lima |
| 2 / miraflores | Hotel Demo Miraflores | Lima |
| 3 / arequipa | Hotel Demo Arequipa | Arequipa |

Dirección “Dirección de demostración”; correo contacto@hotel.test; teléfono ficticio no clicable en demo; activar enlaces reales solo con datos del equipo. Sedes activas/publicadas, version1.

Categorías1 Individual/capacidad1,2 Doble/capacidad2. Pisos11 sede1,21 sede2,31 sede3, descripción “Primer piso”.

| idHabitacion | sede | piso | numero | categoría | precio |
|---:|---:|---:|---|---:|---:|
| 101 | 1 | 11 | 101 | 1 | 70.00 |
| 102 | 1 | 11 | 102 | 2 | 100.00 |
| 201 | 2 | 21 | 101 | 1 | 80.00 |
| 202 | 2 | 21 | 102 | 2 | 120.00 |
| 301 | 3 | 31 | 101 | 1 | 60.00 |
| 302 | 3 | 31 | 102 | 2 | 90.00 |

Todas LISTA, activas/publicadas, version1. Imagen local propia placeholder bajo /assets/demo/, alt explícito “Habitación de demostración”; no hotlinks/acortadores del seed viejo. Limpieza/operaciones vacías. Bootstrap emite SedeSnapshot y HabitacionCreada para cada fila, con eventId estable de bootstrap; recepción/reporting deben consumir antes de demo.

No sembrar “ocupado” sin estadía. No restablecer rooms de una demo ejecutada al reejecutar seeds.
