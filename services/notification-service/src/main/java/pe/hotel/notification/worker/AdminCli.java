package pe.hotel.notification.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI local del operador con perfil {@code admin}
 * (docs/especificacion/microservicios/notification/api.md § Herramienta del operador): corre en el
 * mismo proceso contra {@code notification_db} con credencial de entorno, no abre puerto, no pasa
 * por Gateway y cada acción queda en {@code audit_log}. El envío por red sigue prohibido: aquí solo
 * se opera lo persistido.
 */
public class AdminCli {

  private static final Logger log = LoggerFactory.getLogger(AdminCli.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int LIMITE_LISTADO = 50;

  private final NotificationJobStore jobs;
  private final AuditLogStore audit;
  private final NotificationProperties properties;

  public AdminCli(
      NotificationJobStore jobs, AuditLogStore audit, NotificationProperties properties) {
    this.jobs = jobs;
    this.audit = audit;
    this.properties = properties;
  }

  /** Ejecuta el subcomando y devuelve el código de salida del proceso. */
  public int run(String[] args, PrintStream out) {
    List<String> resto = new ArrayList<>(Arrays.asList(args));
    resto.remove("--admin");
    String actor = valorDe(resto, "--actor").orElse(System.getProperty("user.name", "operator"));
    if (resto.isEmpty()) {
      uso(out);
      return 2;
    }
    String subcomando = resto.get(0);
    return switch (subcomando) {
      case "list" -> listar(valorDe(resto, "--estado").orElse("ERROR"), actor, out);
      case "retry" -> reintentar(resto, actor, out);
      case "discard" -> descartar(resto, actor, out);
      case "purge" -> purgar(resto, actor, out);
      default -> {
        uso(out);
        yield 2;
      }
    };
  }

  private int listar(String estado, String actor, PrintStream out) {
    List<NotificationJob> filas = jobs.listar(estado.toUpperCase(), LIMITE_LISTADO);
    out.printf(
        "estado=%s filas=%d (actor=%s)%n", estado.toUpperCase(), filas.size(), actor);
    for (NotificationJob job : filas) {
      out.printf(
          "%s | tipo=%s | intentos=%d | ultimoError=%s | destinatario=%s | proximo=%s%n",
          job.commandId(),
          job.tipo(),
          job.attempts(),
          job.lastErrorCode() == null ? "-" : job.lastErrorCode(),
          Privacy.maskEmail(job.destinatario()),
          job.nextAttemptAt());
    }
    return 0;
  }

  private int reintentar(List<String> resto, String actor, PrintStream out) {
    UUID commandId = uuidDe(resto, 1, out);
    if (commandId == null) {
      return 2;
    }
    String payload = valorDe(resto, "--payload").orElse(null);
    if (payload == null) {
      int filas = jobs.reintentar(commandId);
      if (filas == 0) {
        out.printf("no hay job en ERROR con commandId %s%n", commandId);
        return 1;
      }
      audit.registrar(
          actor,
          AuditLogStore.RETRY,
          commandId.toString(),
          null,
          null,
          "[\"estado\"]",
          "{\"estado\":\"ERROR\"}",
          "{\"estado\":\"PENDIENTE\"}");
      out.printf("reintento encolado commandId=%s estado=PENDIENTE%n", commandId);
      return 0;
    }
    return corregirPayload(commandId, payload, actor, out);
  }

  /**
   * Corrección de un payload inválido: nunca se edita el job original ni se salta el inbox; se crea
   * un {@code commandId} nuevo con referencia al original en auditoría.
   */
  private int corregirPayload(UUID original, String fichero, String actor, PrintStream out) {
    ObjectNode nodo;
    try {
      JsonNode leido = MAPPER.readTree(Files.readString(Path.of(fichero)));
      if (!leido.isObject()) {
        throw new IllegalArgumentException("el fichero no contiene un objeto JSON");
      }
      nodo = (ObjectNode) leido;
    } catch (Exception ex) {
      out.printf("no se pudo leer el payload corregido: %s%n", ex.getMessage());
      return 2;
    }
    UUID nuevo = UUID.randomUUID();
    nodo.put("commandId", nuevo.toString());
    String cuerpo = nodo.toString();
    try {
      NotificationCommand comando = NotificationCommand.parse(cuerpo);
      jobs.encolar(comando.commandId(), comando.tipo(), cuerpo);
      audit.registrar(
          actor,
          AuditLogStore.RETRY,
          nuevo.toString(),
          comando.idHotel(),
          comando.correlationId(),
          "[\"commandId\"]",
          "{\"commandId\":\"" + original + "\"}",
          "{\"commandId\":\"" + nuevo + "\"}");
      out.printf(
          "payload corregido: commandId nuevo=%s referencia=%s%n", nuevo, original);
      return 0;
    } catch (NotificationPermanentException permanente) {
      out.printf("el payload corregido sigue siendo inválido: %s%n", permanente.getMessage());
      return 1;
    }
  }

  private int descartar(List<String> resto, String actor, PrintStream out) {
    UUID commandId = uuidDe(resto, 1, out);
    if (commandId == null) {
      return 2;
    }
    String motivo = valorDe(resto, "--motivo").orElse("descarte manual del operador");
    int filas = jobs.descartar(commandId, motivo);
    if (filas == 0) {
      out.printf("no hay job descartable con commandId %s%n", commandId);
      return 1;
    }
    audit.registrar(
        actor,
        AuditLogStore.DISCARD,
        commandId.toString(),
        null,
        null,
        "[\"estado\"]",
        "{\"estado\":\"ERROR\"}",
        "{\"estado\":\"DESCARTADO\"}");
    out.printf("descartado commandId=%s motivo=%s (histórico conservado)%n", commandId, motivo);
    return 0;
  }

  private int purgar(List<String> resto, String actor, PrintStream out) {
    String antes = valorDe(resto, "--before").orElse(null);
    Instant fecha;
    try {
      fecha =
          antes == null
              ? Instant.now().minus(java.time.Duration.ofDays(properties.getRetentionSentDays()))
              : parseFecha(antes);
    } catch (Exception ex) {
      out.printf("fecha inválida: %s%n", antes);
      return 2;
    }
    int filas = jobs.purgar(fecha);
    audit.registrar(
        actor,
        AuditLogStore.PURGE,
        "notification_job",
        null,
        null,
        "[\"destinatario\",\"variables\"]",
        "{\"destinatario\":\"<oculto>\",\"variables\":\"<oculto>\"}",
        "{\"destinatario\":null,\"variables\":null}");
    out.printf(
        "purga aplicada: ENVIADA anteriores a %s anonimizadas=%d (PENDIENTE y ERROR intactos)%n",
        fecha, filas);
    log.info("notification_purge actor={} antes={} filas={}", actor, fecha, filas);
    return 0;
  }

  private static Instant parseFecha(String valor) {
    if (valor.length() == 10) {
      return LocalDate.parse(valor).atStartOfDay(ZoneOffset.UTC).toInstant();
    }
    return Instant.parse(valor);
  }

  private static UUID uuidDe(List<String> resto, int indice, PrintStream out) {
    if (resto.size() <= indice) {
      out.println("falta el commandId");
      return null;
    }
    try {
      return UUID.fromString(resto.get(indice));
    } catch (IllegalArgumentException ex) {
      out.printf("commandId no es un UUID: %s%n", resto.get(indice));
      return null;
    }
  }

  private static java.util.Optional<String> valorDe(List<String> resto, String opcion) {
    for (int i = 0; i < resto.size(); i++) {
      String actual = resto.get(i);
      if (actual.equals(opcion) && i + 1 < resto.size()) {
        return java.util.Optional.of(resto.get(i + 1));
      }
      if (actual.startsWith(opcion + "=")) {
        return java.util.Optional.of(actual.substring(opcion.length() + 1));
      }
    }
    return java.util.Optional.empty();
  }

  private static void uso(PrintStream out) {
    out.println("Uso: notification-service --admin <subcomando>");
    out.println("  list --estado ERROR");
    out.println("  retry <commandId> [--payload <fichero.json>]");
    out.println("  discard <commandId> [--motivo <texto>]");
    out.println("  purge --before <yyyy-MM-dd>");
  }
}