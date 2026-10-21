package pe.hotel.sales.support;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * {@link PlatformTransactionManager} sin recursos: ejecuta el callback en el mismo hilo y sin
 * commit real, para probar la lógica de servicio y del recuperador sin PostgreSQL.
 */
public final class TransaccionFalsa implements PlatformTransactionManager {

  @Override
  public TransactionStatus getTransaction(TransactionDefinition definition) {
    return new SimpleTransactionStatus();
  }

  @Override
  public void commit(TransactionStatus status) {}

  @Override
  public void rollback(TransactionStatus status) {}
}
