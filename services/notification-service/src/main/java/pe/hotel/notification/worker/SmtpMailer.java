package pe.hotel.notification.worker;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Envío SMTP del job, siempre fuera de la transacción de base de datos. El {@code Message-ID} es
 * estable por comando ({@code <commandId@hotel.test>}): reduce duplicados en el cliente, no
 * garantiza exactamente una entrega (docs/especificacion/microservicios/notification/
 * base-de-datos.md § Procedimientos).
 */
public class SmtpMailer {

  private final JavaMailSender sender;

  public SmtpMailer(JavaMailSender sender) {
    this.sender = sender;
  }

  public void enviar(String destinatario, String asunto, String cuerpo, String messageId) {
    MimeMessage mensaje = sender.createMimeMessage();
    try {
      MimeMessageHelper helper = new MimeMessageHelper(mensaje, false, "UTF-8");
      helper.setTo(destinatario);
      helper.setSubject(asunto);
      helper.setText(cuerpo, false);
      mensaje.setHeader("Message-ID", messageId);
      // saveChanges respeta el Message-ID ya fijado; el envío no debe reescribirlo.
      mensaje.saveChanges();
      mensaje.setHeader("Message-ID", messageId);
      sender.send(mensaje);
    } catch (jakarta.mail.MessagingException ex) {
      throw new IllegalStateException("SMTP no aceptó el mensaje: " + ex.getMessage(), ex);
    }
  }
}