#!/bin/bash
# Crea las siete bases y sus roles runtime/migrador como exige
# docs/especificacion/infraestructura/gateway-eureka-docker.md §Bases/usuarios y
# docs/especificacion/03-contrato-comun.md §Persistencia transversal.
#
# Lo ejecuta el entrypoint de la imagen `postgres` solo la primera vez, cuando el
# volumen está vacío. No crea tablas de negocio: el esquema lo levanta Flyway en
# cada servicio.
#
# Aislamiento conseguido:
#   - CONNECT revocado a PUBLIC: un runtime no puede conectarse a otra base.
#   - `public` cerrado a PUBLIC.
#   - runtime solo con USAGE (sin CREATE) y DML por privilegios por defecto sobre
#     lo que cree el migrador.
#   - el migrador es dueño de la base y puede crear el esquema.
set -euo pipefail

SERVICIOS="identity hotel reception inventory sales reporting notification"

crear_base() {
  local servicio="$1"
  local servicio_mayusculas
  servicio_mayusculas="$(printf '%s' "$servicio" | tr '[:lower:]' '[:upper:]')"

  local app_pass migrator_pass
  app_pass="$(printenv "POSTGRES_${servicio_mayusculas}_APP_PASSWORD")"
  migrator_pass="$(printenv "POSTGRES_${servicio_mayusculas}_MIGRATOR_PASSWORD")"

  # Roles y base (en `postgres`, sin transacción: CREATE DATABASE no va en bloque).
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v app_role="${servicio}_app" \
    -v migrator_role="${servicio}_migrator" \
    -v app_pass="$app_pass" \
    -v migrator_pass="$migrator_pass" \
    -v db="${servicio}_db" <<-'SQL'
		CREATE ROLE :"app_role" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'app_pass';
		CREATE ROLE :"migrator_role" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'migrator_pass';
		CREATE DATABASE :"db" OWNER :"migrator_role" ENCODING 'UTF8';
		REVOKE CONNECT ON DATABASE :"db" FROM PUBLIC;
		GRANT CONNECT ON DATABASE :"db" TO :"app_role", :"migrator_role";
	SQL

  # Esquema y privilegios dentro de la base.
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "${servicio}_db" \
    -v app_role="${servicio}_app" \
    -v migrator_role="${servicio}_migrator" <<-'SQL'
		REVOKE ALL ON SCHEMA public FROM PUBLIC;
		GRANT USAGE, CREATE ON SCHEMA public TO :"migrator_role";
		GRANT USAGE ON SCHEMA public TO :"app_role";
		ALTER DEFAULT PRIVILEGES FOR ROLE :"migrator_role" IN SCHEMA public
		  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_role";
		ALTER DEFAULT PRIVILEGES FOR ROLE :"migrator_role" IN SCHEMA public
		  GRANT USAGE, SELECT ON SEQUENCES TO :"app_role";
		ALTER DEFAULT PRIVILEGES FOR ROLE :"migrator_role" IN SCHEMA public
		  GRANT EXECUTE ON FUNCTIONS TO :"app_role";
	SQL
}

for servicio in $SERVICIOS; do
  echo "infra-compose: creando base y roles de ${servicio}"
  crear_base "$servicio"
done

echo "infra-compose: siete bases y catorce roles listos"
