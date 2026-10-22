# Arquitectura y decisiones cerradas

## 1. Monorepo: sí

Un repositorio y PRs por funcionalidad completa; cada servicio mantiene build, migraciones, imagen y despliegue independientes. Evita coordinar varios repositorios para un equipo académico.

Estructura objetivo para la implementación. Este repositorio contiene hoy la especificación y los planes; el monolito se conserva en el repositorio de referencia indicado en ESPECIFICACION.md:

```text
/
  ESPECIFICACION.md
  especificacion/
  plans/                   planes de trabajo existentes
  apps/
    admin-web/             Angular: panel y área privada de cliente
    public-web/            Angular: landing y catálogo público
  services/
    pom.xml                agregador Maven y versiones comunes
    identity-service/       src/main/resources/db/migration/ (Flyway propio)
    hotel-service/          src/main/resources/db/migration/ (Flyway propio)
    reception-service/      src/main/resources/db/migration/ (Flyway propio)
    inventory-service/      src/main/resources/db/migration/ (Flyway propio)
    sales-service/          src/main/resources/db/migration/ (Flyway propio)
    reporting-service/      src/main/resources/db/migration/ (Flyway propio)
    notification-service/   src/main/resources/db/migration/ (Flyway propio)
    api-gateway/
    discovery-server/
  contracts/
    openapi/               un YAML por servicio, derivados de estos MD
    events/                JSON Schema por evento/comando v1
  infra/
    compose.yaml
    postgres/init/         crea DB y usuarios, no tablas de negocio
    rabbitmq/
    kafka/
    nginx/
  tests/
    postman/
    integration/
  scripts/
  .env.example             nombres y valores no sensibles
```

No librería compartida de entidades JPA, repositorios ni reglas de negocio. Compartir solo convenciones y esquemas; cada servicio compila sin importar clases de otro dominio.

## 2. División

| Servicio | Único dueño de | No hace |
|---|---|---|
| identity | Personas, credenciales, roles, asignaciones de sedes, tokens | Estadías, stock |
| hotel | Sedes, pisos, categorías, habitaciones, estado físico, limpieza | Decidir ocupación |
| reception | Estadías, admisión por habitación, bloqueos, cierre y cobro de alojamiento | Escribir habitaciones o ventas |
| inventory | Productos, precios por sede, existencias y movimientos | Carrito y cobros |
| sales | Carrito, ventas, detalle, estado de pago de consumos | Actualizar existencias directamente |
| reporting | Proyecciones y reportes consolidados | Autorizar venta/check-in |
| notification | Trabajos de correo y reintentos | Decidir negocio o contactar público sin solicitud interna |

Notification es un worker pequeño incluido, no un octavo dominio comercial. Hay siete aplicaciones de dominio/soporte y dos piezas Spring de infraestructura.

## 3. Frontends y entrada

Ambas aplicaciones usan HTTP mediante Gateway. Landing: rutas `/api/public/**` únicamente. Panel: rutas privadas y login. Los servicios usan Feign con nombres Eureka para acciones síncronas; Kafka para hechos confirmados; RabbitMQ para trabajos dirigidos.

La base de datos de un servicio solo la modifica ese servicio. Un PostgreSQL local con siete bases/usuarios es suficiente: separación lógica real, sin joins ni FKs entre bases. El mismo clúster no equivale a una BD compartida.

## 4. Stack

- Versiones fijadas (verificadas el 2026-10-12): Java 25, Spring Boot **4.1.1**, Spring Cloud **2025.1.3** (Oakwood), Angular **22.2.2**, PostgreSQL **18.6**, Kafka **4.3.1** y RabbitMQ **4.3.6**. Boot 4.1 es la línea con soporte OSS hasta jul-2027; se abandona Boot 3.5.x (fin de soporte OSS 30-jun-2026). Prever la guía de migración Boot 3 → 4 (JUnit 6, Spring Framework 7); el monolito de referencia queda en 3.5.x.
- Spring Cloud 2025.1.x (Oakwood): la tabla oficial aún declara compatibilidad con Boot 4.0.x; el proyecto fija 2025.1.3 junto a Boot 4.1.1 (decisión verificada el 2026-10-12). **Re-verificar la compatibilidad en cada patch de 2025.1.x** y ajustar en el plan de implementación si la tabla cambia. 2025.0.x está EOL. OpenFeign + LoadBalancer, Eureka, Gateway, Security Resource Server.
- Maven Wrapper y package-lock para reproducibilidad. Angular 22.2.2 requiere Node `^22.22.3 || ^24.15.0 || ^26.0.0` y TypeScript `>=6.0 <6.1`. No usar versiones flotantes ni `latest` en la entrega.
- PostgreSQL 18 (parche pineado, imagen `postgres:18`), JPA para CRUD sencillo.
- Flyway por servicio para schema, funciones y procedimientos. Cada servicio aplica sus migraciones al arrancar sobre su propia base; ningún servicio ejecuta migraciones de otro.
  - Ubicación: `src/main/resources/db/migration/`.
  - Nombres `V<n>__descripcion.sql`. Una migración ya aplicada no se edita; los cambios van en una nueva versión.
  - Seeds de demo en `db/seed`, activos solo con perfiles no productivos (`dev`, `demo`). Nunca se activan en producción.
  - Compose espera a que PostgreSQL esté sano (`healthcheck` + `depends_on: service_healthy`) antes de arrancar cada servicio.
- JPA solo valida el esquema: `ddl-auto=validate`; no `update`.
- Kafka 4.3.1 en KRaft (solo KRaft desde 4.0), un nodo para desarrollo; RabbitMQ 4.3.6 con management; Mailpit para correos de demo. Pinear tags/digests de imagen, no `latest`.
- Docker Compose: sí. Infraestructura en contenedores desde el inicio; Java/Angular en IDE durante desarrollo; perfil completo en contenedores para la presentación.
- Sin Kubernetes, service mesh, Redis, Debezium ni servidor de configuración en el MVP.

## 5. Decisiones de negocio

| Duda | Decisión |
|---|---|
| ¿LP vende o reserva? | No. Catálogo, sedes, precios y contacto; sin disponibilidad garantizada. |
| ¿Se permite ingreso futuro? | No. Check-in hoy, salida prevista futura. No se usa el nombre “reserva” en la UI. |
| ¿Dónde se controla doble ocupación? | En reception: bloqueo de fila e índice único de estadía ACTIVA/CERRANDO. |
| ¿Quién decide si puede entrar alguien? | Gate local de reception + lectura física de hotel. |
| ¿Carrito descuenta? | No; la venta aceptada descuenta en inventory. |
| ¿Qué significa estado de venta? | `estado=PENDIENTE/PAGADO` es pago; `estadoOperacion` es ejecución técnica. |
| ¿Cómo se cobra? | Registro manual, moneda PEN; no movimientos bancarios. |
| ¿Qué hace DELETE? | Baja lógica de maestros; venta impagada se anula con compensación; nunca se borra historia financiera. |
| ¿Y la limpieza? | Reception bloquea inmediatamente; hotel ejecuta y libera mediante evento correlacionado. |
| ¿Procedimientos en todo? | Solo invariantes, transiciones y reportes; CRUD básico usa JPA. No duplicar regla SQL y Java. |
| ¿Social login? | Fuera del MVP; retirar botones y responder 410 en ruta antigua. |
| ¿Registro libre de empleados? | Prohibido: registro público fuerza Cliente; personal lo crea ADMIN. |
| ¿Multisede? | Tres sedes semilla, modelo extensible; idHotel obligatorio para operaciones de una sede. |

## 6. Dinero, fechas y estados

PEN, `numeric(12,2)`/BigDecimal; nunca double. Precios de catálogo finales de la cadena; no se modelan impuestos ni comprobantes fiscales. Fechas operativas en America/Lima; timestamps con offset ISO-8601 y almacenamiento timestamptz UTC.

Alojamiento: noches = max(1, días entre fechaEntrada y fechaSalida prevista); tarifa guardada al check-in; precioInicial = noches × tarifa. Cambiar fechaSalida antes del cierre recalcula el alojamiento con esa tarifa; se rechaza una reducción que deje precioInicial por debajo del adelanto. Penalidad manual explícita y auditada, nunca inferida de un monto arbitrario del cliente.

Adelanto entre 0 y precioInicial, registrado en check-in. Al cerrar: saldoAlojamiento = precioInicial + costoPenalidad - adelanto; saldoConsumos = ventas CONFIRMADAS/PENDIENTE; cobroAhora = suma. Los consumos ya PAGADOS no se cobran otra vez. Persistir cierreId y desglose. Total histórico de alojamiento = adelanto + cobroAlojamientoFinal, separado de consumo.

No se anulan pagos registrados en el MVP. Una corrección posterior necesita flujo de devolución explícito, fuera de alcance; DELETE devuelve 409 si habría que devolver dinero.

## 7. Qué se está entregando ahora

Diseño completo en Markdown. Aún faltan implementar servicios, migraciones, contenedores y pruebas de ejecución. La documentación permite repartir trabajo sin convertir decisiones propuestas en funcionalidades existentes.
