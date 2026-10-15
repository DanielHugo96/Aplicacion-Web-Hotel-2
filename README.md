# Aplicacion-Web-Hotel-2

Sistema de gestion hotelera en microservicios: nueve módulos Spring Boot/Spring Cloud (`services/`), dos
aplicaciones Angular (`apps/`) y la infraestructura local en Compose (`infra/`).

- Especificación (punto de entrada): [`docs/ESPECIFICACION.md`](docs/ESPECIFICACION.md).
- Planes de ejecución y su norma: [`docs/plans/README.md`](docs/plans/README.md).
- Contratos (OpenAPI y eventos): [`docs/contracts/README.md`](docs/contracts/README.md).
- Configuración por servicio (variables de entorno): [`docs/especificacion/infraestructura/configuracion.md`](docs/especificacion/infraestructura/configuracion.md).

Versiones fijadas ([`01-arquitectura.md` §4](docs/especificacion/01-arquitectura.md#4-stack)): Java 25, Spring Boot 4.1.1,
Spring Cloud 2025.1.3, Angular 22.2.2, PostgreSQL 18.6, Kafka 4.3.1 y RabbitMQ 4.3.6. Sin `latest`.

## Estructura

| Ruta | Qué contiene |
|---|---|
| `services/` | Agregador Maven (`pom.xml`) y los nueve módulos: siete servicios de dominio, `api-gateway` y `discovery-server`. |
| `apps/` | Apps Angular: `public-web` (puerto 4200) y `admin-web` (puerto 4300). |
| `infra/` | Compose y recursos de infraestructura (`postgres/init/`, `rabbitmq/`, `kafka/`, `nginx/`). |
| `tests/` | Colecciones Postman (`postman/`) y pruebas de integración (`integration/`). |
| `scripts/` | Utilidades del repositorio, entre ellas el validador de planes. |
| `docs/` | Especificación, contratos y planes. |

## Verificación

Estos son los comandos canónicos: los demás documentos y planes los citan tal cual y no definen otros
([norma §6.7](docs/plans/README.md#6-contrato-con-la-ejecutora)).

**1. Build del backend** (los nueve módulos; usa el Maven Wrapper de la raíz):

```bash
./mvnw -f services/pom.xml verify          # Linux/macOS
mvnw.cmd -f services/pom.xml verify        # Windows
```

Resultado esperado: `BUILD SUCCESS` de los nueve módulos (incluye las pruebas de contexto).

**2. Build y pruebas de las apps Angular** (en cada una):

```bash
cd apps/public-web   # y luego en apps/admin-web
npm ci
npm run build
npm test -- --watch=false
```

Resultado esperado: build sin errores y pruebas en verde.

**3. Infraestructura local con Compose** (disponible a partir del plan `infra-compose`, fase F1):

Copia la plantilla y rellena las credenciales locales; sin `.env` Compose no arranca:

```bash
cp .env.example .env   # y completa las credenciales (los valores no van a Git)
```

```bash
docker compose -f infra/compose.yaml --env-file .env config        # valida el archivo
docker compose -f infra/compose.yaml --env-file .env up -d --wait  # levanta y espera healthchecks verdes
docker compose -f infra/compose.yaml --env-file .env down          # la detiene
```

`--env-file .env` es obligatorio: con `-f infra/compose.yaml` Compose buscaría el `.env` junto
al compose (`infra/`), no en la raíz. `down` sin `-v` conserva los volúmenes (`postgres_data`,
`kafka_data`, `rabbitmq_data`); usa `down -v` solo para reiniciar la infraestructura de cero.

**4. Validador de planes** (norma de `docs/plans/`):

```bash
python scripts/validar-planes.py
```

Resultado esperado: 0 errores.
