"""Integración real del módulo de cierre del panel por el Gateway.

Ejecuta cotización, cierre 200 con penalidad y medio de pago, el 409
`AMOUNT_MISMATCH` de un monto desviado 0.01 (con la nueva cotización y su
`closureId`), el 409 `STAY_NOT_ACTIVE` al recerrar una estadía cerrada, la
anulación de check-in (412 con ETag viejo, 200 con el If-Match del detalle) y el
archivado (200 y 409 al repetirlo) contra `http://localhost:8080` con los
servicios de la campaña levantados. Si el cierre devuelve 202, sondea `Location`
según `Retry-After` hasta el estado terminal.

No usa mocks ni puertos de servicios: todo pasa por el Gateway.

Uso (desde la raíz del repositorio):
    python apps/admin-web/tests/evidence/integracion-cierre.py \
        > apps/admin-web/tests/evidence/front-admin-web-cierre-integracion.txt
"""

from __future__ import annotations

import json
import sys
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT))

from scripts.qa import common as c  # noqa: E402


def mostrar(etiqueta: str, resp: c.Resp, extra: str | None = None) -> None:
    cuerpo = resp.body
    if len(cuerpo) > 1400:
        cuerpo = cuerpo[:1400] + "…"
    print(f"\n[{etiqueta}] HTTP {resp.status}")
    if extra:
        print(f"  {extra}")
    print(f"  {cuerpo}")


def etag(resp: c.Resp) -> str | None:
    return resp.headers.get("etag")


def if_match(resp: c.Resp) -> str | None:
    directo = etag(resp)
    if directo:
        return directo
    version = resp.field("data", "version")
    return f'"{version}"' if version is not None else None


def filas(resp: c.Resp) -> list[dict]:
    data = resp.field("data") or []
    if isinstance(data, dict):
        data = next((v for v in data.values() if isinstance(v, list)), [])
    return [f for f in data if isinstance(f, dict)]


def esperar_limpieza(
    token: str, id_hotel: int, id_habitacion: int, *, timeout: int = 60
) -> None:
    """Tras el checkout el gate queda en limpieza (evento asíncrono).

    Espera la tarea PENDIENTE de la sala (puede tardar en proyectarse) y la
    completa para dejar el gate en LISTA y poder reusar la habitación.
    """
    limite = time.time() + timeout
    tarea = None
    while time.time() < limite:
        pendientes = c.http(
            "GET",
            f"/api/habitacion/limpiezas?page=0&size=50&idHotel={id_hotel}&estado=PENDIENTE",
            token=token,
        )
        tarea = next(
            (
                t
                for t in filas(pendientes)
                if t.get("idHabitacion") == id_habitacion and t.get("estado") == "PENDIENTE"
            ),
            None,
        )
        if tarea:
            mostrar("habitacion/limpiezas?estado=PENDIENTE (gate en limpieza)", pendientes)
            break
        time.sleep(2)
    if not tarea:
        print("  (no apareció tarea PENDIENTE: la sala ya estaba disponible)")
        return
    hecho = c.http(
        "PUT",
        f"/api/habitacion/{id_habitacion}/limpieza-completada",
        token=token,
        headers={
            "Idempotency-Key": str(uuid.uuid4()),
            "If-Match": f'"{tarea["version"]}"',
        },
        body={"cleaningCycleId": tarea["cleaningCycleId"]},
    )
    mostrar("habitacion/limpieza-completada (gate vuelve a LISTA)", hecho)


def main() -> int:
    token = c.login("admin@hotel.test", c.env("DEMO_PASSWORD"))
    print("Integración front-admin-web-cierre por el Gateway http://localhost:8080")
    print("Token de administrador obtenido con /api/auth/login (sin imprimir la credencial).")

    id_hotel = 1

    # ------------------------------------------------ estadía ACTIVA a cerrar
    activas = c.http(
        "GET",
        f"/api/recepcion/listar?page=0&size=20&idHotel={id_hotel}&estadoEstadia=ACTIVA",
        token=token,
    )
    mostrar("recepcion/listar?estadoEstadia=ACTIVA", activas)
    pendientes = filas(activas)
    if not pendientes:
        print("No hay estadía ACTIVA en la sede 1; no se puede ejecutar el cierre real.")
        return 1
    id_recepcion = pendientes[0]["idRecepcion"]
    id_habitacion = pendientes[0]["idHabitacion"]
    fecha_salida = pendientes[0]["fechaSalida"]

    detalle = c.http("GET", f"/api/recepcion/{id_recepcion}", token=token)
    mostrar(
        "recepcion/{id} (detalle con ETag de partida)",
        detalle,
        f"ETag={etag(detalle)} · estadoEstadia={detalle.field('data', 'estadoEstadia')}",
    )

    # ------------------------------------------------------------- cotización
    cotizacion = c.http(
        "GET", f"/api/recepcion/{id_recepcion}/cotizacion-salida?costoPenalidad=10", token=token
    )
    mostrar(
        "recepcion/{id}/cotizacion-salida?costoPenalidad=10",
        cotizacion,
        "el frontend solo pinta este desglose; no recalcula importes",
    )
    cobro_ahora = cotizacion.field("data", "cobroAhora")
    if cobro_ahora is None:
        return 1

    # --------------------------------- cotización sin penalidad (recotización)
    recotizada = c.http(
        "GET", f"/api/recepcion/{id_recepcion}/cotizacion-salida?costoPenalidad=0", token=token
    )
    mostrar(
        "recepcion/{id}/cotizacion-salida?costoPenalidad=0 (recotiza sin tocar el detalle)",
        recotizada,
        f"penalidad={recotizada.field('data', 'penalidad')} "
        f"· cobroAhora={recotizada.field('data', 'cobroAhora')}",
    )

    # --------------------------------------- cierre 409 AMOUNT_MISMATCH (0.01)
    desviado = round(float(cobro_ahora) + 0.01, 2)
    mismatch = c.http(
        "POST",
        "/api/recepcion/registrar-salida",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4())},
        body={
            "idRecepcion": id_recepcion,
            "idHabitacion": id_habitacion,
            "costoPenalidad": 10,
            "motivoPenalidad": "Salida tardía (integración)",
            "montoRecibido": desviado,
            "medioPago": "EFECTIVO",
        },
    )
    mostrar(
        "recepcion/registrar-salida con monto +0.01 (409 AMOUNT_MISMATCH esperado)",
        mismatch,
        f"cobroAhora={cobro_ahora} · montoRecibido={desviado} "
        f"· code={mismatch.field('error', 'code')} "
        f"· nuevaCotizacion={mismatch.field('data', 'cotizacion', 'cobroAhora')} "
        f"· closureIdRechazado={mismatch.field('data', 'closureId')}",
    )

    # ---------------------------------------- cierre 200 con penalidad y cobro
    cierre = c.http(
        "POST",
        "/api/recepcion/registrar-salida",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4())},
        body={
            "idRecepcion": id_recepcion,
            "idHabitacion": id_habitacion,
            "costoPenalidad": 10,
            "motivoPenalidad": "Salida tardía (integración)",
            "montoRecibido": cobro_ahora,
            "medioPago": "EFECTIVO",
        },
    )
    mostrar(
        "recepcion/registrar-salida con el monto calculado (intención nueva)",
        cierre,
        f"ETag={etag(cierre)} · estadoEstadia={cierre.field('data', 'estadoEstadia')} "
        f"· estadoCierre={cierre.field('data', 'cierre', 'estado')} "
        f"· medioPago={cierre.field('data', 'cierre', 'medioPago')}",
    )
    if cierre.status not in (200, 202):
        return 1
    if cierre.status == 202:
        location = cierre.headers.get("location")
        reintento = cierre.headers.get("retry-after")
        print(f"  -> 202: Location={location} · Retry-After={reintento}")
        for intento in range(1, 16):
            time.sleep(float(reintento or 2))
            sondeo = c.http("GET", location or f"/api/recepcion/{id_recepcion}", token=token)
            mostrar(f"sondeo {intento} del cierre pendiente", sondeo)
            if sondeo.field("data", "estadoEstadia") != "CERRANDO":
                break

    cerrada = c.http("GET", f"/api/recepcion/{id_recepcion}", token=token)
    mostrar(
        "recepcion/{id} tras el cierre",
        cerrada,
        f"ETag={etag(cerrada)} · estadoEstadia={cerrada.field('data', 'estadoEstadia')} "
        f"· totalPagado={cerrada.field('data', 'totalPagado')}",
    )

    # ------------------------------- 409 STAY_NOT_ACTIVE al recerrar la cerrada
    recerrar = c.http(
        "POST",
        "/api/recepcion/registrar-salida",
        token=token,
        headers={"Idempotency-Key": str(uuid.uuid4())},
        body={
            "idRecepcion": id_recepcion,
            "idHabitacion": id_habitacion,
            "costoPenalidad": 0,
            "montoRecibido": 0,
            "medioPago": "EFECTIVO",
        },
    )
    mostrar(
        "recepcion/registrar-salida sobre una estadía CERRADA (409 STAY_NOT_ACTIVE)",
        recerrar,
        f"code={recerrar.field('error', 'code')}",
    )

    # ------------------------------------------- anular check-in (nueva ACTIVA)
    esperar_limpieza(token, id_hotel, id_habitacion)
    sala = c.http("GET", f"/api/habitacion/buscar/{id_habitacion}", token=token)
    mostrar(
        "habitacion/buscar (gate tras completar limpieza)",
        sala,
        f"estadoFisico={sala.field('data', 'estadoFisico', 'codigo')} "
        f"· ocupacion={sala.field('data', 'ocupacion')}",
    )

    checkin = None
    for intento in range(1, 11):
        checkin = c.http(
            "POST",
            "/api/recepcion/registrar",
            token=token,
            headers={"Idempotency-Key": str(uuid.uuid4())},
            body={
                "idHotel": id_hotel,
                "idCliente": 100,
                "idHabitacion": id_habitacion,
                "fechaSalida": fecha_salida,
                "adelanto": 0.0,
                "observacion": "Integración de anulación de check-in",
            },
        )
        mostrar(f"recepcion/registrar (check-in para anular, intento {intento})", checkin)
        if checkin.status in (200, 201) or checkin.field("error", "code") != "ROOM_NOT_READY":
            break
        # El gate de recepción se proyecta por evento: tras completar la limpieza
        # puede tardar unos segundos en verse LISTA. No es un defecto de la app.
        print("  -> ROOM_NOT_READY transitorio; se reintenta con intención nueva")
        time.sleep(3)
    if checkin.status not in (200, 201):
        return 1
    id_anular = checkin.field("data", "idRecepcion")

    detalle_anular = c.http("GET", f"/api/recepcion/{id_anular}", token=token)
    mostrar(
        "recepcion/{id} de la estadía a anular",
        detalle_anular,
        f"ETag={etag(detalle_anular)} · estadoEstadia={detalle_anular.field('data', 'estadoEstadia')}",
    )

    # 409 de archivado: la estadía sigue ACTIVA (solo se archiva CERRADA).
    archivar_activa = c.http(
        "DELETE",
        f"/api/recepcion/eliminar/{id_anular}",
        token=token,
        headers={
            "Idempotency-Key": str(uuid.uuid4()),
            "If-Match": if_match(detalle_anular),
        },
    )
    mostrar(
        "recepcion/eliminar/{id} sobre una estadía ACTIVA (409 esperado)",
        archivar_activa,
        f"code={archivar_activa.field('error', 'code')}",
    )
    sin_motivo = c.http(
        "POST",
        f"/api/recepcion/{id_anular}/anular-checkin",
        token=token,
        headers={
            "Idempotency-Key": str(uuid.uuid4()),
            "If-Match": if_match(detalle_anular),
        },
        body={"motivo": ""},
    )
    mostrar("recepcion/{id}/anular-checkin con motivo vacío (400 esperado)", sin_motivo)

    # 412: If-Match viejo no sobrescribe.
    etag_viejo = '"1"' if if_match(detalle_anular) != '"1"' else '"2"'
    precondicion = c.http(
        "POST",
        f"/api/recepcion/{id_anular}/anular-checkin",
        token=token,
        headers={
            "Idempotency-Key": str(uuid.uuid4()),
            "If-Match": etag_viejo,
        },
        body={"motivo": "Check-in equivocado (If-Match viejo)"},
    )
    mostrar(
        f"recepcion/{id_anular}/anular-checkin con If-Match {etag_viejo} (412 esperado)",
        precondicion,
        f"code={precondicion.field('error', 'code')}",
    )

    anulada = c.http(
        "POST",
        f"/api/recepcion/{id_anular}/anular-checkin",
        token=token,
        headers={
            "Idempotency-Key": str(uuid.uuid4()),
            "If-Match": if_match(detalle_anular),
        },
        body={"motivo": "Check-in equivocado (integración)"},
    )
    mostrar(
        "recepcion/{id}/anular-checkin (200)",
        anulada,
        f"estadoEstadia={anulada.field('data', 'estadoEstadia')} "
        f"· archivada={anulada.field('data', 'archivada')} "
        f"· totalPagado={anulada.field('data', 'totalPagado')}",
    )

    repetida = c.http(
        "POST",
        f"/api/recepcion/{id_anular}/anular-checkin",
        token=token,
        headers={
            "Idempotency-Key": str(uuid.uuid4()),
            "If-Match": if_match(anulada),
        },
        body={"motivo": "Check-in equivocado (repetido)"},
    )
    mostrar(
        "recepcion/{id}/anular-checkin repetido (409 esperado, sin cierre en curso)",
        repetida,
        f"code={repetida.field('error', 'code')}",
    )

    # ------------------------------------------------------------- archivado
    detalle_archivar = c.http("GET", f"/api/recepcion/{id_recepcion}", token=token)
    etag_archivar = if_match(detalle_archivar)
    viejo_archivar = '"1"' if etag_archivar != '"1"' else '"2"'
    precondicion_archivar = c.http(
        "DELETE",
        f"/api/recepcion/eliminar/{id_recepcion}",
        token=token,
        headers={
            "Idempotency-Key": str(uuid.uuid4()),
            "If-Match": viejo_archivar,
        },
    )
    mostrar(
        f"recepcion/eliminar/{id_recepcion} con If-Match {viejo_archivar} (412 esperado)",
        precondicion_archivar,
        f"code={precondicion_archivar.field('error', 'code')}",
    )

    archivada = c.http(
        "DELETE",
        f"/api/recepcion/eliminar/{id_recepcion}",
        token=token,
        headers={
            "Idempotency-Key": str(uuid.uuid4()),
            "If-Match": etag_archivar,
        },
    )
    mostrar(
        "recepcion/eliminar/{id} (archivar CERRADA)",
        archivada,
        f"archivada={archivada.field('data', 'archivada')} · ETag={etag(archivada)}",
    )

    ya_archivada = c.http(
        "DELETE",
        f"/api/recepcion/eliminar/{id_recepcion}",
        token=token,
        headers={
            "Idempotency-Key": str(uuid.uuid4()),
            "If-Match": if_match(archivada),
        },
    )
    mostrar("recepcion/eliminar/{id} repetido (la estadía ya está archivada)", ya_archivada)

    print(
        "\nResumen JSON:",
        json.dumps(
            {
                "idRecepcionCerrada": id_recepcion,
                "idRecepcionAnulada": id_anular,
                "situacion": "Integración por el Gateway completada",
            },
            ensure_ascii=False,
        ),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
