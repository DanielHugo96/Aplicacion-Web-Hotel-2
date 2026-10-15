#!/bin/bash
# Genera /etc/rabbitmq/definitions.json desde las variables de entorno y arranca
# el broker.
#
# Se ejecuta en cada arranque del contenedor y es idempotente: la carga nativa de
# definiciones vuelve a declarar el mismo vhost, los mismos usuarios y los mismos
# permisos. No crea exchanges ni colas: esa topología es de
# `back-plataforma-mensajeria` (docs/especificacion/05-mensajeria.md).
#
# Permisos por rol productor/consumidor (docs/especificacion/05-mensajeria.md
# §Reintentos y DLQ Rabbit). Un productor solo puede publicar al exchange de
# comandos `hotel.commands.v1`; un consumidor solo declara y lee sus colas. Las
# regexes se escriben con `[.]` (sin barras invertidas) para sobrevivir al
# heredoc y quedar como JSON válido.
set -euo pipefail

DESTINO=/etc/rabbitmq/definitions.json
PLANTILLA="$(mktemp)"
trap 'rm -f "$PLANTILLA"' EXIT

cat > "$PLANTILLA" <<'JSON'
{
  "rabbit_version": "4.3.6",
  "users": [
    { "name": "@@ADMIN_USER@@", "password": "@@ADMIN_PASSWORD@@", "tags": ["administrator"] },
    { "name": "identity",     "password": "@@IDENTITY_PASSWORD@@",     "tags": [] },
    { "name": "hotel",        "password": "@@HOTEL_PASSWORD@@",        "tags": [] },
    { "name": "reception",    "password": "@@RECEPTION_PASSWORD@@",    "tags": [] },
    { "name": "inventory",    "password": "@@INVENTORY_PASSWORD@@",    "tags": [] },
    { "name": "sales",        "password": "@@SALES_PASSWORD@@",        "tags": [] },
    { "name": "notification", "password": "@@NOTIFICATION_PASSWORD@@", "tags": [] }
  ],
  "vhosts": [
    { "name": "/hotel-demo" }
  ],
  "permissions": [
    { "user": "@@ADMIN_USER@@", "vhost": "/hotel-demo", "configure": ".*", "write": ".*", "read": ".*" },

    { "user": "identity", "vhost": "/hotel-demo", "configure": "^$", "write": "^hotel[.]commands[.]v1$", "read": "^$" },
    { "user": "sales",    "vhost": "/hotel-demo", "configure": "^$", "write": "^hotel[.]commands[.]v1$", "read": "^$" },

    { "user": "hotel",     "vhost": "/hotel-demo", "configure": "^limpieza[.]solicitada", "write": "^$", "read": "^limpieza[.]solicitada" },
    { "user": "inventory", "vhost": "/hotel-demo", "configure": "^stock[.]restituir",     "write": "^$", "read": "^stock[.]restituir" },

    { "user": "reception", "vhost": "/hotel-demo", "configure": "^recepcion[.]operacion-finalizar", "write": "^hotel[.]commands[.]v1$", "read": "^recepcion[.]operacion-finalizar" },

    { "user": "notification", "vhost": "/hotel-demo", "configure": "^notificacion[.](bienvenida|checkin|checkout)", "write": "^$", "read": "^notificacion[.](bienvenida|checkin|checkout)" }
  ],
  "topic_permissions": [],
  "parameters": [],
  "global_parameters": [],
  "policies": [],
  "queues": [],
  "exchanges": [],
  "bindings": []
}
JSON

sustituir() {
  local marcador="$1" valor="$2" escapado
  escapado="$(printf '%s' "$valor" | sed -e 's/[\/&\\]/\\&/g')"
  sed -i "s/@@${marcador}@@/${escapado}/g" "$PLANTILLA"
}

sustituir ADMIN_USER "${RABBITMQ_ADMIN_USER}"
sustituir ADMIN_PASSWORD "${RABBITMQ_ADMIN_PASSWORD}"
sustituir IDENTITY_PASSWORD "${RABBITMQ_IDENTITY_PASSWORD}"
sustituir HOTEL_PASSWORD "${RABBITMQ_HOTEL_PASSWORD}"
sustituir RECEPTION_PASSWORD "${RABBITMQ_RECEPTION_PASSWORD}"
sustituir INVENTORY_PASSWORD "${RABBITMQ_INVENTORY_PASSWORD}"
sustituir SALES_PASSWORD "${RABBITMQ_SALES_PASSWORD}"
sustituir NOTIFICATION_PASSWORD "${RABBITMQ_NOTIFICATION_PASSWORD}"

install -m 0644 "$PLANTILLA" "$DESTINO"
echo "infra-compose: definiciones de RabbitMQ listas en $DESTINO"

exec docker-entrypoint.sh rabbitmq-server
