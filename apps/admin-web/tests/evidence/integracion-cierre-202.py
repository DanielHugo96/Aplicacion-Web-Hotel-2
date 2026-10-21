"""Integración del 202 de cierre con recuperación real por el Gateway.

Retiene el advisory lock de confirmación de Sales (`pg_advisory_xact_lock(7101,
idRecepcion)`, el mismo que usa `SalesService.confirmarCierre`) mientras se
envía el cierre: reception ya leyó el resumen, persiste la operación durable y
la confirmación no termina dentro del request, así que responde 202 con
`Location`/`Retry-After`. Al liberar el lock, el recuperador de reception
confirma con el payload congelado y el sondeo a `Location` llega a `CERRADA`.
Después se reenvía el mismo `Idempotency-Key` y el servidor devuelve el mismo
cierre persistido, sin crear una segunda salida.

El `read-timeout` de Feign de reception se baja a 1500 ms solo para este
escenario (`FEIGN_READ_TIMEOUT_MS`, variable ya prevista en `application.yml`):
con el valor por defecto de 5000 ms, el Gateway (5000 ms) corta antes y
devuelve `503 DEPENDENCY_UNAVAILABLE` en vez del 202. Al terminar se restaura
reception con los valores por defecto.

No usa mocks ni puertos de servicios: el cliente habla solo con
`http://localhost:8080`.

Uso (desde la raíz del repositorio):
    python apps/admin-web/tests/evidence/integracion-cierre-202.py \
        >> apps/admin-web/tests/evidence/front-admin-web-cierre-integracion.txt
"""

from __future__ import annotations

import json
import sys
import time
import uuid
from datetime import date, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT))

from scripts.qa import common as c  # noqa: E402

FEIGN_READ_TIMEOUT_MS = "1500"


def mostrar(etiqueta: str, resp: c.Resp, extra: str = "") -> None:
    cuerpo = resp.body if len(resp.body) <= 1400 else resp.body[:1400] + "…"
    print(f"\n[{etiqueta}] HTTP {resp.status}{(' · ' + extra) if extra else ''}")
    print(f"  {cuerpo}")


def sala_libre(token: str, id_hotel: int) -> int:
    listado = c.http(
        "GET", f"/api/habitacion/listar?page=0&size=100&idHotel={id_hotel}", token=token
    )
    for habitacion in listado.field("data") or []:
        if (
            habitacion.get("estado") is True
            and (habitacion.get("estadoFisico") or {}).get("codigo") == "LISTA"
            and (habitacion.get("ocupacion") or {}).get("idRecepcion") is None
            and habitacion.get("blockId") is None
        ):
            return int(habitacion["idHabitacion"])
    raise c.RunbookError("no hay habitación LISTA y libre en la sede 1")


def checkin(token: str, id_hotel: int, id_habitacion: int, salida: str) -> c.Resp:
    resp = None
    for intento in range(1, 11):
        resp = c.http(
            "POST",
            "/api/recepcion/registrar",
            token=token,
            headers={"Idempotency-Key": str(uuid.uuid4())},
            body={
                "idHotel": id_hotel,
                "idCliente": 100,
                "idHabitacion": id_habitacion,
                "fechaSalida": salida,
                "adelanto": 20.0,
                "medioPago": "EFECTIVO",
                "observacion": "Integración del 202 de cierre",
            },
        )
        if resp.status in (200, 201) or resp.field("error", "code") != "ROOM_NOT_READY":
            return resp
        print(f"  -> ROOM_NOT_READY transitorio (intento {intento}); se reintenta")
        time.sleep(3)
    return resp


def main() -> int:
    token = c.login("admin@hotel.test", c.env("DEMO_PASSWORD"))
    id_hotel = 1
    salida = (date.today() + timedelta(days=1)).isoformat()

    print("\n=== 202 de cierre con recuperación real (confirmación de Sales retenida) ===")

    id_habitacion = sala_libre(token, id_hotel)
    alta = checkin(token, id_hotel, id_habitacion, salida)
    mostrar("recepcion/registrar (estadía para el 202)", alta)
    if alta.status not in (200, 201):
        return 1
    id_recepcion = alta.field("data", "idRecepcion")

    cotizacion = c.http(
        "GET", f"/api/recepcion/{id_recepcion}/cotizacion-salida?costoPenalidad=0", token=token
    )
    mostrar("recepcion/{id}/cotizacion-salida?costoPenalidad=0", cotizacion)
    cobro_ahora = cotizacion.field("data", "cobroAhora")
    if cobro_ahora is None:
        return 1

    clave = str(uuid.uuid4())
    cuerpo = {
        "idRecepcion": id_recepcion,
        "idHabitacion": id_habitacion,
        "costoPenalidad": 0,
        "montoRecibido": cobro_ahora,
        "medioPago": "TARJETA",
    }

    print(
        "\nReiniciando reception-service con FEIGN_READ_TIMEOUT_MS="
        f"{FEIGN_READ_TIMEOUT_MS} (por debajo del corte de 5 s del Gateway)…"
    )
    c.stop_service("reception-service")
    c.start_service(
        "reception-service",
        wait=True,
        timeout=300,
        env_overrides={"FEIGN_READ_TIMEOUT_MS": FEIGN_READ_TIMEOUT_MS},
    )
    print("reception-service listo con el timeout reducido.")

    print(
        f"\nReteniendo pg_advisory_xact_lock(7101, {id_recepcion}) en sales_db "
        "para que la confirmación no termine en el request…"
    )
    with c.AdvisoryLock("sales_db", id_recepcion):
        print("lock retenido; enviando el cierre.")
        pendiente = c.http(
            "POST",
            "/api/recepcion/registrar-salida",
            token=token,
            headers={"Idempotency-Key": clave},
            body=cuerpo,
            timeout=60,
        )
        mostrar(
            "recepcion/registrar-salida con la confirmación retenida (202 esperado)",
            pendiente,
            f"Location={pendiente.headers.get('location')} "
            f"Retry-After={pendiente.headers.get('retry-after')} "
            f"estadoEstadia={pendiente.field('data', 'estadoEstadia')} "
            f"estadoCierre={pendiente.field('data', 'cierre', 'estado')}",
        )
    print("lock liberado; el recuperador de reception ya puede confirmar.")

    location = pendiente.headers.get("location")
    reintento = pendiente.headers.get("retry-after")
    if pendiente.status != 202 or not location:
        print("El entorno no produjo el 202 de cierre; no se puede demostrar el polling real.")
        return 1

    terminal = None
    for intento in range(1, 61):
        time.sleep(float(reintento or 2))
        seguimiento = c.http("GET", location, token=token)
        print(
            f"[polling cierre 202 {intento}] HTTP {seguimiento.status} · estadoEstadia="
            f"{seguimiento.field('data', 'estadoEstadia')} · estadoCierre="
            f"{seguimiento.field('data', 'cierre', 'estado')} · lastErrorCode="
            f"{seguimiento.field('data', 'cierre', 'lastErrorCode')}"
        )
        if seguimiento.field("data", "estadoEstadia") == "CERRADA":
            terminal = seguimiento
            break
    if terminal is None:
        print("El cierre no llegó a CERRADA en el tiempo de sondeo.")
        return 1
    mostrar("cierre 202 terminal", terminal)

    reenvio = c.http(
        "POST",
        "/api/recepcion/registrar-salida",
        token=token,
        headers={"Idempotency-Key": clave},
        body=cuerpo,
    )
    mostrar(
        "recepcion/registrar-salida con la misma Idempotency-Key (mismo closureId, sin segunda salida)",
        reenvio,
        f"estadoEstadia={reenvio.field('data', 'estadoEstadia')} "
        f"· closureId={reenvio.field('data', 'closureId')}",
    )

    cierres = c.psql_one(
        "reception_db", f"select count(*) from cierre where id_recepcion={id_recepcion}"
    )
    print(f"  cierres persistidos para la estadía {id_recepcion}: {cierres} (esperado 1)")

    print("\nRestaurando reception-service con el read-timeout por defecto…")
    c.stop_service("reception-service")
    c.start_service("reception-service", wait=True, timeout=300)
    print("reception-service listo otra vez con los valores por defecto.")

    print(
        json.dumps(
            {
                "idRecepcion": id_recepcion,
                "resultado": "CERRADA tras 202 con polling",
                "cierresPersistidos": cierres,
            },
            ensure_ascii=False,
        )
    )
    return 0 if cierres == "1" else 1


if __name__ == "__main__":
    raise SystemExit(main())
