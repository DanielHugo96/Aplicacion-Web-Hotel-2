# Tasks

## 1. Dependencias y propuesta

- [ ] 1.1 Confirmar `infra-compose` y `back-plataforma-comunes` disponibles y verificar `docker compose -f infra/compose.yaml ps` con infra sana y readiness 200 por servicio.
- [ ] 1.2 Mantener esta propuesta, specs delta y diseño alineados con los pasos del plan y verificar `openspec validate back-plataforma-mensajeria --strict` sin errores al cerrar.

## 2. Persistencia y publicación

- [ ] 2.1 Aplicar el DDL de `outbox` e `inbox` en las siete bases con Flyway propio y verificar ambas tablas en cada base y `mvn -q -f services/pom.xml validate` con código 0.
- [ ] 2.2 Configurar el poller cada 5 s con lote de 50 y verificar el intervalo y el tamaño en la configuración y en el log del poller.
- [ ] 2.3 Aplicar backoff exponencial con tope de 5 min y alertas por más de 15 min o más de 10 intentos y verificar `attempts` y `next_attempt_at` con el broker detenido.
- [ ] 2.4 Aplicar orden por agregado en publicación y consumo y verificar que N+1 no se publica antes que N y que la versión vieja no sobrescribe.

## 3. Topología de brokers

- [ ] 3.1 Declarar los exchanges hotel.commands.v1 y hotel.dlx.v1 y las seis colas durables y verificar ambos exchanges y las seis colas con `rabbitmqadmin list`.
- [ ] 3.2 Declarar reintentos con TTL y DLQ por cola y verificar las 24 derivadas y que el tercer fallo termina en `q.dlq` con el mismo identificador.
- [ ] 3.3 Configurar productor Kafka con `acks=all`, idempotencia y confirmación de offset tras commit y verificar que sin confirmación no se marca `published_at`.
- [ ] 3.4 Configurar los cuatro grupos con sus DLT y reintentos y verificar que el sexto fallo queda en `<topic>.<consumerGroup>.dlt` con alerta.

## 4. Consumo y validación

- [ ] 4.1 Validar cada mensaje contra su JSON Schema v1 y verificar `python -m jsonschema -i` con código 0 para ejemplos y error ante campo extra.
- [ ] 4.2 Aplicar `inbox` idempotente con retención de 30 días y verificar efecto único ante identifiers duplicados y derivación directa ante esquema inválido.
- [ ] 4.3 Dejar visibles degradación, alertas y logs sin secretos y verificar readiness degradado y `503 DEPENDENCY_UNAVAILABLE` donde aplique.
- [ ] 4.4 Ejecutar el humo MSG01 y MSG02 y verificar publicación y efecto únicos y contradicción en DLT/DLQ con evidencia en `outbox` e `inbox`.
