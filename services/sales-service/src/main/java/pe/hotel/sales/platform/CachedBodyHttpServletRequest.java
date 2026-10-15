package pe.hotel.sales.platform;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Envuelve la petición para poder leer el cuerpo (hash de idempotencia) sin consumirlo para el
 * controlador: cada {@link #getInputStream()} devuelve una copia desde el inicio.
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

  private final byte[] body;

  public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
    super(request);
    this.body = request.getInputStream().readAllBytes();
  }

  public byte[] body() {
    return body;
  }

  @Override
  public ServletInputStream getInputStream() {
    ByteArrayInputStream source = new ByteArrayInputStream(body);
    return new ServletInputStream() {

      @Override
      public int read() {
        return source.read();
      }

      @Override
      public int read(byte[] bytes, int offset, int length) {
        return source.read(bytes, offset, length);
      }

      @Override
      public boolean isFinished() {
        return source.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener readListener) {
        // El cuerpo ya está en memoria; no se usa lectura asíncrona.
      }
    };
  }

  @Override
  public BufferedReader getReader() {
    return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
  }
}
