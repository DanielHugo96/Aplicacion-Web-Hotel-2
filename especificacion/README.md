# Especificación del hotel — v1

## Leer en este orden

1. [Decisiones y arquitectura](01-arquitectura.md): alcance, dueños, monorepo y Docker.
2. [Landing y panel](02-landing-y-panel.md): qué permite cada frontend.
3. [Contrato común y seguridad](03-contrato-comun.md): headers, respuestas, roles, sedes, idempotencia.
4. [Flujos y consistencia](04-flujos.md): stock, check-in, check-out, limpieza y mantenimiento.
5. [Mensajería](05-mensajeria.md): todos los topics, colas, consumidores y envelopes.
6. [Migración y aceptación](06-migracion-y-pruebas.md): diferencias del monolito, secuencia y pruebas.
7. [Infraestructura](infraestructura/gateway-eureka-docker.md): rutas del Gateway, Eureka, puertos y configuración.

## Documento por microservicio

| Servicio | Base de datos, procedimientos y seeds | API REST e integración |
|---|---|---|
| identity-service | [BD](microservicios/identity/base-de-datos.md) | [API](microservicios/identity/api.md) |
| hotel-service | [BD](microservicios/hotel/base-de-datos.md) | [API](microservicios/hotel/api.md) |
| reception-service | [BD](microservicios/reception/base-de-datos.md) | [API](microservicios/reception/api.md) |
| inventory-service | [BD](microservicios/inventory/base-de-datos.md) | [API](microservicios/inventory/api.md) |
| sales-service | [BD](microservicios/sales/base-de-datos.md) | [API](microservicios/sales/api.md) |
| reporting-service | [BD](microservicios/reporting/base-de-datos.md) | [API](microservicios/reporting/api.md) |
| notification-service | [BD](microservicios/notification/base-de-datos.md) | [API](microservicios/notification/api.md) |

Gateway y Eureka son infraestructura, no dominios adicionales: no tienen BD de negocio, procedimientos ni seeds. Su contrato está en el documento de infraestructura.

## Cómo interpretar el diseño

- Especificación objetivo, no inventario de endpoints ya funcionando.
- `LEGACY` conserva método/ruta; puede exigir adaptación de seguridad, DTO o respuesta. `NUEVA` se implementará. `RETIRADA` tiene sustituto o queda fuera del MVP.
- Los contratos comunes forman parte de cada API. Un campo no admitido se rechaza con 400; no se hace binding directo sobre entidades JPA.
- Un endpoint REST nunca “va por Kafka”. Es HTTP; el servicio puede llamar por Feign y emitir eventos/comandos como efecto posterior.
- Ningún servicio se conecta obligatoriamente a ambos brokers. El mapa exacto está en mensajería.
- Las firmas SQL aquí son especificaciones, no scripts de migración ejecutados.
- Cambiar reglas de este paquete requiere actualizar contratos, BD, flujo y prueba relacionada en el mismo PR.

## Alcance cerrado

Tres sedes de demostración; estadías inmediatas; cobro manual registrado, sin procesador bancario; carrito y consumo de productos; limpieza; reportes; correo de demostración. Landing informativa independiente del panel.

No se implementan reservas futuras, pasarela de pago, facturación fiscal, fidelización, OTA, aplicación móvil, Kubernetes ni Config Server. No se añaden servicios de “pagos”, “reservas”, “clientes” o “limpieza” separados.

## Fuentes y trazabilidad

Código inspeccionado: controllers/DTO del backend, POM, package.json Angular y `database/01_schema.sql`, `02_procedures.sql`, `03_reports.sql`, `seed.sql`. Hallazgos y cambios están en migración.

La guía académica requiere Spring Data/MVC/Security/Lombok, Angular, login con BCrypt y CRUD persistente. La división y los brokers son decisiones de este proyecto; no se presentan como requisitos textuales de la rúbrica. La entrega del informe y sustentación sigue siendo un trabajo separado de esta especificación.

Referencias técnicas: [compatibilidad Spring Cloud](https://github.com/spring-cloud/spring-cloud-release/wiki/Supported-Versions), [Feign](https://docs.spring.io/spring-cloud-openfeign/reference/spring-cloud-openfeign.html), [JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [índices parciales PostgreSQL](https://www.postgresql.org/docs/current/indexes-partial.html), [entrega fiable RabbitMQ](https://www.rabbitmq.com/docs/reliability).
