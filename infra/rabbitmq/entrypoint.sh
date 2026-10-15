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
# §Reintentos y DLQ Rabbit). Un productor solo publica al exchange de comandos
# `hotel.commands.v1` (write) sin declarar nada (configure ^$). Un consumidor
# declara y lee sus colas y también los exchanges compartidos `hotel.commands.v1`
# y `hotel.dlx.v1`, porque la topología la declara por código
# `back-plataforma-mensajeria` al arranque (el primero que arranca los crea;
# declarar es idempotente). Declarar colas y bindings requiere, además de la
# regex de sus propias colas, acceso a los dos exchanges y al exchange por
# defecto (`amq.default`) que sostiene el binding implícito de cada cola; por eso
# el consumidor lleva las mismas tres regexes acotadas a sus colas y a esos
# exchanges. Las regexes se escriben con `[.]` (sin barras invertidas) para
# sobrevivir al heredoc y quedar como JSON válido.
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

    { "user": "hotel",     "vhost": "/hotel-demo", "configure": "^(limpieza[.]solicitada.*|hotel[.]commands[.]v1|hotel[.]dlx[.]v1|amq[.]default)$", "write": "^(limpieza[.]solicitada.*|hotel[.]commands[.]v1|hotel[.]dlx[.]v1|amq[.]default)$", "read": "^(limpieza[.]solicitada.*|hotel[.]commands[.]v1|hotel[.]dlx[.]v1)$" },
    { "user": "inventory", "vhost": "/hotel-demo", "configure": "^(stock[.]restituir.*|hotel[.]commands[.]v1|hotel[.]dlx[.]v1|amq[.]default)$",           "write": "^(stock[.]restituir.*|hotel[.]commands[.]v1|hotel[.]dlx[.]v1|amq[.]default)$", "read": "^(stock[.]restituir.*|hotel[.]commands[.]v1|hotel[.]dlx[.]v1)$" },

    { "user": "reception", "vhost": "/hotel-demo", "configure": "^(recepcion[.]operacion-finalizar.*|hotel[.]commands[.]v1|hotel[.]dlx[.]v1|amq[.]default)$", "write": "^(recepcion[.]operacion-finalizar.*|hotel[.]commands[.]v1|hotel[.]dlx[.]v1|amq[.]default)$", "read": "^(recepcion[.]operacion-finalizar.*|hotel[.]commands[.]v1|hotel[.]dlx[.]v1)$" },

    { "user": "notification", "vhost": "/hotel-demo", "configure": "^(notificacion[.](bienvenida|checkin|checkout).*|hotel[.]commands[.]v1|hotel[.]dlx[.]v1|amq[.]default)$", "write": "^(notificacion[.](bienvenida|checkin|checkout).*|hotel[.]commands[.]v1|hotel[.]dlx[.]v1|amq[.]default)$", "read": "^(notificacion[.](bienvenida|checkin|checkout).*|hotel[.]commands[.]v1|hotel[.]dlx[.]v1)$" }
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
