"""Utilidades comunes del runner de runbooks de F5.

Solo usa la biblioteca estándar y las CLI de los contenedores del Compose. No
escribe credenciales: lee `.env` (ignorado por Git) y las pasa al entorno de
los procesos que arranca.
"""

from __future__ import annotations

import base64
import json
import os
import platform
import re
import shutil
import signal
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Iterable

ROOT = Path(__file__).resolve().parents[2]
ENV_FILE = ROOT / ".env"
EVIDENCE = ROOT / "tests" / "evidence" / "qa-runbooks"
RUNTIME = EVIDENCE / "_runtime"
COMPOSE = ["docker", "compose", "-f", str(ROOT / "infra" / "compose.yaml"), "--env-file", str(ENV_FILE)]
POSTGRES = "hotel-postgres-1"
KAFKA = "hotel-kafka-1"
RABBIT = "hotel-rabbitmq-1"
MAILPIT = "hotel-mailpit-1"
BOOTSTRAP = "localhost:9092"
RABBIT_MGMT = "http://localhost:15673/api"
RABBIT_VHOST = "%2Fhotel-demo"
GATEWAY = "http://localhost:8080"

SERVICES = {
    "discovery-server": {"jar": "discovery-server", "port": 8761, "kind": "discovery"},
    "identity-service": {"jar": "identity-service", "port": 8081},
    "hotel-service": {"jar": "hotel-service", "port": 8082},
    "reception-service": {"jar": "reception-service", "port": 8083},
    "inventory-service": {"jar": "inventory-service", "port": 8084},
    "sales-service": {"jar": "sales-service", "port": 8085},
    "reporting-service": {"jar": "reporting-service", "port": 8086},
    "notification-service": {"jar": "notification-service", "port": 8087},
    "api-gateway": {"jar": "api-gateway", "port": 8080, "kind": "gateway"},
}

START_ORDER = [
    "discovery-server",
    "identity-service",
    "hotel-service",
    "inventory-service",
    "reception-service",
    "sales-service",
    "reporting-service",
    "notification-service",
    "api-gateway",
]

DB_SERVICE = {
    "identity_db": "identity",
    "hotel_db": "hotel",
    "reception_db": "reception",
    "inventory_db": "inventory",
    "sales_db": "sales",
    "reporting_db": "reporting",
    "notification_db": "notification",
}

DATABASES = list(DB_SERVICE)


class RunbookError(RuntimeError):
    """Fallo de un simulacro con el motivo ya redactado para la evidencia."""


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


def iso(dt: datetime | None = None) -> str:
    return (dt or now_utc()).astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def load_env(path: Path = ENV_FILE) -> dict[str, str]:
    if not path.exists():
        raise RunbookError(f"falta {path.name}: copia .env.example y completa las credenciales locales")
    env: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        env[key.strip()] = value.strip().strip('"').strip("'")
    return env


ENV = load_env()


def env(name: str, default: str | None = None) -> str:
    value = ENV.get(name, default)
    if value is None:
        raise RunbookError(f"falta la variable {name} en .env")
    return value


# ---------------------------------------------------------------------------
# Procesos y contenedores
# ---------------------------------------------------------------------------


def run(
    cmd: list[str],
    *,
    input_text: str | None = None,
    timeout: int = 120,
    check: bool = True,
    env_extra: dict[str, str] | None = None,
    cwd: Path | None = None,
) -> subprocess.CompletedProcess:
    proc = subprocess.run(
        cmd,
        input=input_text,
        text=True,
        capture_output=True,
        timeout=timeout,
        cwd=str(cwd or ROOT),
        env={**os.environ, **(env_extra or {})},
    )
    if check and proc.returncode != 0:
        raise RunbookError(
            f"comando falló ({proc.returncode}): {' '.join(cmd[:4])}...\n"
            f"stdout: {proc.stdout[-1500:]}\nstderr: {proc.stderr[-1500:]}"
        )
    return proc


def run_bytes(cmd: list[str], *, timeout: int = 300, check: bool = True) -> subprocess.CompletedProcess:
    proc = subprocess.run(cmd, capture_output=True, timeout=timeout, cwd=str(ROOT))
    if check and proc.returncode != 0:
        raise RunbookError(
            f"comando falló ({proc.returncode}): {' '.join(cmd[:4])}...\n"
            f"stderr: {proc.stderr.decode('utf-8', 'replace')[-1500:]}"
        )
    return proc


def docker(*args: str, timeout: int = 120, check: bool = True) -> subprocess.CompletedProcess:
    return run(["docker", *args], timeout=timeout, check=check)


def compose(*args: str, timeout: int = 300, check: bool = True) -> subprocess.CompletedProcess:
    return run([*COMPOSE, *args], timeout=timeout, check=check)


def compose_ps() -> dict[str, str]:
    out = compose("ps", "--format", "{{.Service}}|{{.State}}|{{.Health}}", check=True).stdout
    result: dict[str, str] = {}
    for line in out.splitlines():
        parts = line.strip().split("|")
        if len(parts) == 3:
            result[parts[0]] = f"{parts[1]}/{parts[2]}"
    return result


# ---------------------------------------------------------------------------
# HTTP
# ---------------------------------------------------------------------------


@dataclass
class Resp:
    status: int
    headers: dict[str, str]
    body: str

    @property
    def json(self) -> Any:
        try:
            return json.loads(self.body)
        except Exception:
            return None

    def field(self, *path: str) -> Any:
        node = self.json
        for key in path:
            if not isinstance(node, dict):
                return None
            node = node.get(key)
        return node

    def summary(self) -> dict[str, Any]:
        return {"status": self.status, "headers": self.headers, "body": self.body}


def http(
    method: str,
    path: str,
    *,
    token: str | None = None,
    body: Any = None,
    headers: dict[str, str] | None = None,
    timeout: int = 30,
    base: str = GATEWAY,
    basic: tuple[str, str] | None = None,
) -> Resp:
    url = path if path.startswith("http") else base + path
    data = None
    hdrs = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        hdrs["Content-Type"] = "application/json"
    if token:
        hdrs["Authorization"] = f"Bearer {token}"
    if headers:
        hdrs.update({k: str(v) for k, v in headers.items() if v is not None})
    if basic:
        raw = f"{basic[0]}:{basic[1]}".encode("utf-8")
        hdrs["Authorization"] = "Basic " + base64.b64encode(raw).decode("ascii")
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return Resp(resp.status, {k.lower(): v for k, v in resp.headers.items()}, resp.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as err:
        return Resp(err.code, {k.lower(): v for k, v in err.headers.items()}, err.read().decode("utf-8", "replace"))
    except Exception as err:  # conexión rechazada, timeout, DNS...
        return Resp(0, {}, f"{type(err).__name__}: {err}")


_TOKEN_CACHE: dict[str, tuple[str, float]] = {}


def login(email: str, password: str) -> str:
    cached = _TOKEN_CACHE.get(email)
    if cached and time.time() < cached[1]:
        return cached[0]
    resp = http("POST", "/api/auth/login", body={"correo": email, "clave": password})
    if resp.status == 429:
        time.sleep(12)
        resp = http("POST", "/api/auth/login", body={"correo": email, "clave": password})
    if resp.status != 200:
        raise RunbookError(f"login {email} devolvió {resp.status}: {resp.body[:300]}")
    token = resp.field("data", "token")
    if not token:
        raise RunbookError(f"login {email} sin token: {resp.body[:300]}")
    expires = resp.field("data", "expiresIn") or 900
    _TOKEN_CACHE[email] = (token, time.time() + max(60, int(expires) - 120))
    return token


def machine_token(service: str, audience: str) -> str:
    """Token máquina para las rutas /internal/** (no entra por el Gateway).

    El filtro Basic de identity autentica `clientId:secret` y el controlador exige
    `application/x-www-form-urlencoded` con `audience`; la respuesta es
    `{access_token, token_type, expires_in}` sin envelope.
    """
    client_id = f"{service}-service"
    secret_key = {
        "identity": "SERVICE_CLIENT_SECRET",
        "reception": "SERVICE_CLIENT_RECEPTION_SECRET",
        "hotel": "SERVICE_CLIENT_HOTEL_SECRET",
        "inventory": "SERVICE_CLIENT_INVENTORY_SECRET",
        "sales": "SERVICE_CLIENT_SALES_SECRET",
    }[service]
    raw = f"{client_id}:{env(secret_key)}".encode("utf-8")
    body = urllib.parse.urlencode({"grant_type": "client_credentials", "audience": audience}).encode()
    req = urllib.request.Request(
        "http://localhost:8081/internal/auth/token",
        data=body,
        headers={
            "Authorization": "Basic " + base64.b64encode(raw).decode("ascii"),
            "Content-Type": "application/x-www-form-urlencoded",
            "Accept": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as err:
        raise RunbookError(f"token máquina {service}->{audience} devolvió {err.code}: {err.read().decode('utf-8', 'replace')[:300]}")
    except Exception as err:
        raise RunbookError(f"token máquina {service}->{audience} falló: {err}")
    token = payload.get("access_token")
    if not token:
        raise RunbookError(f"token máquina {service}->{audience} sin access_token: {payload}")
    return token


# ---------------------------------------------------------------------------
# PostgreSQL (solo lectura salvo la retención de locks de los simulacros)
# ---------------------------------------------------------------------------


def psql(db: str, sql: str, *, timeout: int = 120, user: str = "postgres") -> list[list[str]]:
    proc = docker(
        "exec",
        POSTGRES,
        "psql",
        "-U",
        user,
        "-d",
        db,
        "-v",
        "ON_ERROR_STOP=1",
        "-t",
        "-A",
        "-F",
        "|",
        "-c",
        sql,
        timeout=timeout,
        check=False,
    )
    if proc.returncode != 0:
        raise RunbookError(f"psql {db} falló: {proc.stderr[-1200:]}")
    return [line.split("|") for line in proc.stdout.splitlines() if line.strip()]


def psql_one(db: str, sql: str, default: str = "") -> str:
    rows = psql(db, sql)
    if not rows or not rows[0]:
        return default
    return rows[0][0]


def psql_json(db: str, sql: str) -> Any:
    raw = psql_one(db, sql)
    if raw in ("", "NULL"):
        return None
    try:
        return json.loads(raw)
    except Exception:
        return raw


def table_exists(db: str, table: str) -> bool:
    return psql_one(
        db,
        "select count(*) from information_schema.tables "
        f"where table_schema='public' and table_name='{table}'",
    ) == "1"


def db_tables(db: str) -> list[str]:
    rows = psql(
        db,
        "select table_name from information_schema.tables "
        "where table_schema='public' and table_type='BASE TABLE' order by table_name",
    )
    return [r[0] for r in rows]


# ---------------------------------------------------------------------------
# Kafka
# ---------------------------------------------------------------------------


def kafka(*args: str, timeout: int = 120, check: bool = True, input_text: str | None = None) -> subprocess.CompletedProcess:
    return run(
        ["docker", "exec", "-i", KAFKA, "/opt/kafka/bin/" + args[0], *args[1:]],
        timeout=timeout,
        check=check,
        input_text=input_text,
    )


def kafka_topics() -> list[str]:
    proc = kafka("kafka-topics.sh", "--bootstrap-server", BOOTSTRAP, "--list", check=False)
    return [line.strip() for line in proc.stdout.splitlines() if line.strip()]


def kafka_end_offset(topic: str) -> int:
    if topic not in kafka_topics():
        return 0
    proc = kafka(
        "kafka-get-offsets.sh",
        "--bootstrap-server",
        BOOTSTRAP,
        "--topic",
        topic,
        "--time",
        "-1",
        check=False,
    )
    total = 0
    for line in proc.stdout.splitlines():
        parts = line.strip().split(":")
        if len(parts) == 3 and parts[2].isdigit():
            total += int(parts[2])
    return total


def kafka_count_matching(suffix: str) -> tuple[int, dict[str, int]]:
    detail: dict[str, int] = {}
    for topic in kafka_topics():
        if topic.endswith(suffix):
            detail[topic] = kafka_end_offset(topic)
    return sum(detail.values()), detail


def kafka_publish(topic: str, key: str, payload: dict[str, Any]) -> None:
    value = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    kafka(
        "kafka-console-producer.sh",
        "--bootstrap-server",
        BOOTSTRAP,
        "--topic",
        topic,
        "--property",
        "parse.key=true",
        "--property",
        "key.separator=\t",
        input_text=f"{key}\t{value}\n",
        timeout=60,
    )


def kafka_consume(topic: str, max_messages: int = 20, timeout_ms: int = 5000) -> list[str]:
    if topic not in kafka_topics():
        return []
    proc = kafka(
        "kafka-console-consumer.sh",
        "--bootstrap-server",
        BOOTSTRAP,
        "--topic",
        topic,
        "--from-beginning",
        "--max-messages",
        str(max_messages),
        "--timeout-ms",
        str(timeout_ms),
        timeout=max(30, timeout_ms // 1000 + 20),
        check=False,
    )
    return [line for line in proc.stdout.splitlines() if line.strip()]


def kafka_delete_topic(topic: str) -> None:
    if topic in kafka_topics():
        kafka("kafka-topics.sh", "--bootstrap-server", BOOTSTRAP, "--delete", "--topic", topic, check=False)


def kafka_dlt_counts() -> tuple[int, dict[str, int]]:
    return kafka_count_matching(".dlt")


# ---------------------------------------------------------------------------
# RabbitMQ (management API)
# ---------------------------------------------------------------------------


def rabbit(path: str, method: str = "GET", body: Any = None, timeout: int = 30) -> Resp:
    return http(
        method,
        RABBIT_MGMT + path,
        body=body,
        basic=(env("RABBITMQ_ADMIN_USER"), env("RABBITMQ_ADMIN_PASSWORD")),
        base="",
        timeout=timeout,
    )


def rabbit_queue(name: str) -> dict[str, Any]:
    resp = rabbit(f"/queues/{RABBIT_VHOST}/{name}")
    if resp.status != 200:
        return {"name": name, "messages": 0, "consumers": 0, "missing": True, "status": resp.status}
    data = resp.json or {}
    return {
        "name": name,
        "messages": data.get("messages", 0),
        "messages_ready": data.get("messages_ready", 0),
        "consumers": data.get("consumers", 0),
        "missing": False,
    }


def rabbit_queues() -> dict[str, dict[str, Any]]:
    resp = rabbit(f"/queues/{RABBIT_VHOST}")
    if resp.status != 200:
        raise RunbookError(f"management Rabbit devolvió {resp.status}: {resp.body[:300]}")
    return {
        q["name"]: {
            "name": q["name"],
            "messages": q.get("messages", 0),
            "messages_ready": q.get("messages_ready", 0),
            "consumers": q.get("consumers", 0),
        }
        for q in (resp.json or [])
    }


def rabbit_dlq_counts() -> tuple[int, dict[str, int]]:
    queues = rabbit_queues()
    detail = {name: q["messages"] for name, q in queues.items() if name.endswith(".dlq")}
    return sum(detail.values()), detail


def rabbit_publish(exchange: str, routing_key: str, payload: dict[str, Any], *, command_id: str | None = None) -> Resp:
    props: dict[str, Any] = {
        "content_type": "application/json",
        "delivery_mode": 2,
        "headers": {"x-schema-version": 1},
    }
    if command_id:
        props["message_id"] = command_id
        props["correlation_id"] = str(uuid.uuid4())
    body = {
        "properties": props,
        "routing_key": routing_key,
        "payload": json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
        "payload_encoding": "string",
    }
    return rabbit(f"/exchanges/{RABBIT_VHOST}/{exchange}/publish", method="POST", body=body)


def rabbit_get(queue: str, count: int = 100) -> list[dict[str, Any]]:
    resp = rabbit(
        f"/queues/{RABBIT_VHOST}/{queue}/get",
        method="POST",
        body={"count": count, "ackmode": "ack_requeue_false", "encoding": "auto"},
    )
    if resp.status != 200:
        raise RunbookError(f"lectura de {queue} devolvió {resp.status}: {resp.body[:300]}")
    return (resp.json or [])


def rabbit_purge(queue: str) -> None:
    rabbit(f"/queues/{RABBIT_VHOST}/{queue}/contents", method="DELETE")


def rabbit_purge_dlq() -> list[dict[str, Any]]:
    """Descarta las DLQ dejando el contenido leído como evidencia."""
    discarded = []
    for name, q in rabbit_queues().items():
        if name.endswith(".dlq") and q["messages"]:
            messages = rabbit_get(name, q["messages"])
            discarded.append({"queue": name, "messages": messages})
            rabbit_purge(name)
    return discarded


# ---------------------------------------------------------------------------
# Servicios locales
# ---------------------------------------------------------------------------


def service_env(name: str) -> dict[str, str]:
    base = {
        "SPRING_PROFILES_ACTIVE": env("SPRING_PROFILES_ACTIVE", "dev"),
        "EUREKA_URL": env("EUREKA_URL"),
        "EUREKA_USERNAME": env("EUREKA_USERNAME"),
        "EUREKA_PASSWORD": env("EUREKA_PASSWORD"),
        "KAFKA_BOOTSTRAP_SERVERS": env("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092").replace("localhost", "127.0.0.1"),
        "RABBITMQ_HOST": "127.0.0.1",
        "RABBITMQ_PORT": env("RABBITMQ_PORT", "5673"),
        "RABBITMQ_VHOST": env("RABBITMQ_VHOST", "/hotel-demo"),
        "MSYS2_ENV_CONV_EXCL": "RABBITMQ_VHOST",
        "JWT_ISSUER": env("JWT_ISSUER", "hotel-identity"),
        "JWK_SET_URI": env("JWK_SET_URI"),
        "LOG_LEVEL": env("LOG_LEVEL", "INFO"),
        "DEMO_PASSWORD": env("DEMO_PASSWORD"),
        "SMTP_HOST": "127.0.0.1",
        "SMTP_PORT": env("SMTP_PORT", "1025"),
    }
    if name == "discovery-server":
        return base
    if name == "api-gateway":
        base.update(
            {
                "JWK_SET_URI": env("JWK_SET_URI"),
            }
        )
        return base
    service = name.replace("-service", "")
    db = f"{service}_db"
    base.update(
        {
            "SPRING_DATASOURCE_URL": f"jdbc:postgresql://localhost:55432/{db}",
            "SPRING_DATASOURCE_USERNAME": f"{service}_app",
            "SPRING_DATASOURCE_PASSWORD": env(f"POSTGRES_{service.upper()}_APP_PASSWORD"),
            "SPRING_FLYWAY_USER": f"{service}_migrator",
            "SPRING_FLYWAY_PASSWORD": env(f"POSTGRES_{service.upper()}_MIGRATOR_PASSWORD"),
            "SERVICE_CLIENT_ID": name,
            "SERVICE_CLIENT_SECRET": env(
                {
                    "identity": "SERVICE_CLIENT_SECRET",
                    "reception": "SERVICE_CLIENT_RECEPTION_SECRET",
                    "hotel": "SERVICE_CLIENT_HOTEL_SECRET",
                    "inventory": "SERVICE_CLIENT_INVENTORY_SECRET",
                    "sales": "SERVICE_CLIENT_SALES_SECRET",
                }.get(service, "SERVICE_CLIENT_SECRET")
            ),
        }
    )
    if service != "reporting":
        base.update(
            {
                "RABBITMQ_USER": service,
                "RABBITMQ_PASSWORD": env(f"RABBITMQ_{service.upper()}_PASSWORD"),
            }
        )
    if service == "identity":
        base.update(
            {
                "IDENTITY_SIGNING_KEY_FILE": env("IDENTITY_SIGNING_KEY_FILE"),
                "IDENTITY_JWK_KID": env("IDENTITY_JWK_KID", "v1"),
                "SERVICE_CLIENT_RECEPTION_SECRET": env("SERVICE_CLIENT_RECEPTION_SECRET"),
                "SERVICE_CLIENT_HOTEL_SECRET": env("SERVICE_CLIENT_HOTEL_SECRET"),
                "SERVICE_CLIENT_INVENTORY_SECRET": env("SERVICE_CLIENT_INVENTORY_SECRET"),
                "SERVICE_CLIENT_SALES_SECRET": env("SERVICE_CLIENT_SALES_SECRET"),
            }
        )
    return base


def jar_for(name: str) -> Path:
    jar = ROOT / "services" / name / "target" / f"{name}-0.0.1-SNAPSHOT.jar"
    if not jar.exists():
        raise RunbookError(f"falta {jar.relative_to(ROOT)}; ejecuta el build del backend")
    return jar


def port_open(port: int, host: str = "127.0.0.1") -> bool:
    import socket

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(0.7)
        return sock.connect_ex((host, port)) == 0


def _port_pid(port: int) -> int | None:
    if platform.system() == "Windows":
        out = run(["netstat", "-ano"], check=False).stdout
        for line in out.splitlines():
            if f":{port} " in line and "LISTENING" in line:
                return int(line.strip().split()[-1])
        return None
    out = run(["lsof", "-ti", f"tcp:{port}"], check=False).stdout
    return int(out.split()[0]) if out.strip() else None


def kill_port(port: int) -> None:
    pid = _port_pid(port)
    if not pid:
        return
    if platform.system() == "Windows":
        run(["taskkill", "/PID", str(pid), "/T", "/F"], check=False)
    else:
        try:
            os.kill(pid, signal.SIGTERM)
        except ProcessLookupError:
            pass


def stop_service(name: str) -> None:
    pid_file = RUNTIME / f"{name}.pid"
    if pid_file.exists():
        try:
            pid = int(pid_file.read_text().strip())
            if platform.system() == "Windows":
                run(["taskkill", "/PID", str(pid), "/T", "/F"], check=False)
            else:
                os.kill(pid, signal.SIGTERM)
        except Exception:
            pass
        pid_file.unlink(missing_ok=True)
    kill_port(SERVICES[name]["port"])


def start_service(name: str, *, wait: bool = True, timeout: int = 150) -> None:
    info = SERVICES[name]
    if wait and port_open(info["port"]):
        return
    RUNTIME.mkdir(parents=True, exist_ok=True)
    log = RUNTIME / f"{name}.log"
    envs = {**os.environ, **service_env(name)}
    with log.open("ab") as handle:
        handle.write(f"\n===== {iso()} arranque {name} =====\n".encode())
        handle.flush()
        proc = subprocess.Popen(
            [
                bash_executable(),
                "scripts/mvn.sh",
                "-f",
                "services/pom.xml",
                "-pl",
                name,
                "spring-boot:run",
                "-q",
            ],
            cwd=str(ROOT),
            env=envs,
            stdout=handle,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
    (RUNTIME / f"{name}.pid").write_text(str(proc.pid))
    if wait:
        wait_ready(name, timeout=timeout)


def wait_ready(name: str, timeout: int = 150) -> None:
    info = SERVICES[name]
    port = info["port"]
    deadline = time.time() + timeout
    last = ""
    while time.time() < deadline:
        if port_open(port):
            if info.get("kind") == "discovery":
                resp = http("GET", f"http://localhost:{port}/actuator/health/liveness", base="")
                last = resp.body
                if resp.status == 200:
                    return
            elif info.get("kind") == "gateway":
                return
            else:
                resp = http("GET", f"http://localhost:{port}/actuator/health/readiness", base="")
                last = resp.body
                if resp.status == 200 and '"UP"' in resp.body:
                    return
        time.sleep(2)
    raise RunbookError(f"{name} no quedó listo en {timeout}s (último: {last[:200]})")


def stop_all_services() -> None:
    for name in reversed(START_ORDER):
        stop_service(name)


def start_all_services(timeout: int = 180) -> None:
    for name in START_ORDER:
        start_service(name, timeout=timeout)


def bash_executable() -> str:
    if platform.system() == "Windows":
        candidate = Path(r"C:\Program Files\Git\bin\bash.exe")
        if candidate.exists():
            return str(candidate)
    return "bash"


def reset_demo(*, build: bool = False) -> dict[str, Any]:
    """Resetea la demo sobre volúmenes nuevos del Compose y rearranca los servicios."""
    started = iso()
    stop_all_services()
    if build:
        run([bash_executable(), "scripts/mvn.sh", "-f", "services/pom.xml", "-DskipTests", "package", "-q"], timeout=1800)
    compose("down", "-v", timeout=180, check=False)
    up = compose("up", "-d", "--wait", timeout=300)
    start_all_services()
    bootstrap_gates()
    wait_reporting_listo(timeout=240)
    return {
        "iniciado": started,
        "compose_up": up.stdout.strip().splitlines()[-3:],
        "compose_ps": compose_ps(),
        "servicios": {name: service_health(name) for name in START_ORDER},
    }


def service_health(name: str) -> dict[str, Any]:
    info = SERVICES[name]
    if info.get("kind") == "discovery":
        resp = http("GET", f"http://localhost:{info['port']}/actuator/health/liveness", base="")
    elif info.get("kind") == "gateway":
        resp = http("GET", f"http://localhost:{info['port']}/", base="")
    else:
        resp = http("GET", f"http://localhost:{info['port']}/actuator/health/readiness", base="")
    return {"status": resp.status, "body": resp.body[:600]}


def wait_reporting_listo(timeout: int = 240) -> None:
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            state = psql_one("reporting_db", "select estado from projection_bootstrap where id=1")
            if state == "LISTO":
                return
        except RunbookError:
            pass
        time.sleep(3)
    raise RunbookError("reporting no alcanzó LISTO")


def bootstrap_gates(timeout: int = 120) -> int:
    """Completa los gates que el bootstrap de seeds de hotel debería emitir.

    La spec ([hotel/base-de-datos.md § Seeds]) dice que el arranque encola una
    `HabitacionCreada` por habitación sembrada. Si el servicio no la emite, el
    runner publica el mismo evento (mismo contrato, eventId estable) en vez de
    escribir `room_gate` por SQL. Así los gates se crean por el consumidor real.
    """
    rooms = psql(
        "hotel_db",
        "select h.id, h.id_hotel, h.numero, h.id_categoria, c.descripcion, p.descripcion, "
        "h.precio, h.estado, eh.codigo, h.version "
        "from habitacion h join categoria c on c.id=h.id_categoria "
        "join piso p on p.id=h.id_piso join estado_habitacion eh on eh.id=h.id_estado_habitacion "
        "order by h.id",
    )
    gates = int(psql_one("reception_db", "select count(*) from room_gate") or 0)
    published = 0
    for row in rooms:
        id_habitacion = int(row[0])
        existing = psql_one("reception_db", f"select count(*) from room_gate where id_habitacion={id_habitacion}")
        if existing == "1":
            continue
        event_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f"qa-runbooks:bootstrap:habitacion:{id_habitacion}"))
        payload = {
            "eventId": event_id,
            "type": "HabitacionCreada",
            "schemaVersion": 1,
            "aggregateType": "habitacion",
            "aggregateId": str(id_habitacion),
            "aggregateVersion": int(row[9]),
            "idHotel": int(row[1]),
            "occurredAt": iso(),
            "correlationId": str(uuid.uuid4()),
            "payload": {
                "idHabitacion": id_habitacion,
                "idHotel": int(row[1]),
                "numero": row[2],
                "idCategoria": int(row[3]),
                "categoriaNombre": row[4],
                "pisoNombre": row[5],
                "precio": float(row[6]),
                "estado": row[7] == "t",
                "estadoFisico": row[8],
                "version": int(row[9]),
                "cleaningCycleId": None,
                "cleaningRoomVersion": None,
                "blockId": None,
            },
        }
        kafka_publish("hotel.habitacion.v1", str(id_habitacion), payload)
        published += 1
    expected = len(rooms)
    deadline = time.time() + timeout
    while time.time() < deadline:
        gates = int(psql_one("reception_db", "select count(*) from room_gate") or 0)
        if gates >= expected:
            return published
        time.sleep(3)
    raise RunbookError(f"bootstrap de gates incompleto: {gates}/{expected} tras {timeout}s")


# ---------------------------------------------------------------------------
# Evidencia
# ---------------------------------------------------------------------------


def evidence_path(sim: str, filename: str) -> Path:
    folder = EVIDENCE / sim
    folder.mkdir(parents=True, exist_ok=True)
    return folder / filename


def write_evidence(sim: str, filename: str, content: str) -> Path:
    path = evidence_path(sim, filename)
    path.write_text(content, encoding="utf-8")
    return path


def write_json(sim: str, filename: str, content: Any) -> Path:
    return write_evidence(sim, filename, json.dumps(content, indent=2, ensure_ascii=False, default=str) + "\n")


def run_evidence(sim: str, name: str, cmd: list[str], *, timeout: int = 120) -> Path:
    """Ejecuta un comando y guarda comando, salida y código en la evidencia."""
    proc = run(cmd, check=False, timeout=timeout)
    text = (
        f"$ {' '.join(cmd)}\n"
        f"exit={proc.returncode}\n"
        f"--- stdout ---\n{proc.stdout}\n"
        f"--- stderr ---\n{proc.stderr}\n"
    )
    return write_evidence(sim, name, text)


def redact_headers(headers: dict[str, str]) -> dict[str, str]:
    out = dict(headers)
    for key in list(out):
        if key.lower() in {"authorization", "set-cookie"}:
            out[key] = "<redactado>"
    return out


def wait_until(
    predicate: Callable[[], Any],
    *,
    timeout: int,
    interval: int = 5,
    description: str = "condición",
) -> Any:
    deadline = time.time() + timeout
    last = None
    while time.time() < deadline:
        last = predicate()
        if last:
            return last
        time.sleep(interval)
    raise RunbookError(f"timeout esperando {description} ({timeout}s); último valor: {last}")


class AdvisoryLock:
    """Retiene `pg_advisory_lock(clase, id)` en una sesión psql viva, sin editar filas.

    Reproduce el «commit retenido» de RB2 con la misma serialización que usa Sales
    (`pg_advisory_xact_lock(7101, idRecepcion)`).
    """

    def __init__(self, db: str, value: int, clase: int = 7101, user: str = "postgres") -> None:
        self.db = db
        self.value = value
        self.clase = clase
        self.proc = subprocess.Popen(
            ["docker", "exec", "-i", POSTGRES, "psql", "-U", user, "-d", db, "-q", "-t", "-A", "-v", "ON_ERROR_STOP=1"],
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
            text=True,
        )
        assert self.proc.stdin is not None
        self.proc.stdin.write(f"SELECT pg_advisory_lock({clase}, {value});\n")
        self.proc.stdin.flush()
        deadline = time.time() + 15
        while time.time() < deadline:
            held = psql_one(
                db,
                "select count(*) from pg_locks where locktype='advisory' "
                f"and classid={clase} and objid={value} and granted",
            )
            if held not in ("", "0"):
                return
            time.sleep(0.3)
        raise RunbookError(f"no se pudo retener el advisory lock ({clase},{value})")

    def release(self) -> None:
        if self.proc.poll() is None and self.proc.stdin:
            try:
                self.proc.stdin.write(f"SELECT pg_advisory_unlock({self.clase}, {self.value});\n\\q\n")
                self.proc.stdin.flush()
            except Exception:
                pass
        try:
            self.proc.wait(timeout=10)
        except Exception:
            self.proc.kill()
            self.proc.wait(timeout=5)

    def __enter__(self) -> "AdvisoryLock":
        return self

    def __exit__(self, *_: Any) -> None:
        self.release()



def sleep_with_note(seconds: int, description: str) -> None:
    print(f"  esperando {seconds}s: {description}", flush=True)
    time.sleep(seconds)


def runbook_step(title: str) -> None:
    print(f"\n=== {title} ===", flush=True)
