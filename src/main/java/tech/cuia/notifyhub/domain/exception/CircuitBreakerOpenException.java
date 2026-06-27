package tech.cuia.notifyhub.domain.exception;

import tech.cuia.notifyhub.domain.model.ChannelType;

import java.util.UUID;

/**
 * Sinaliza que o Circuit Breaker do canal está OPEN.
 * O dispatch service trata esta exceção de forma diferente de uma falha transitória:
 * em vez de colocar na fila de retry, envia diretamente para a DLQ.
 */
public class CircuitBreakerOpenException extends NotificationDeliveryException {

    public CircuitBreakerOpenException(UUID notificationId, ChannelType channel) {
        super(notificationId, channel, "Circuit breaker is OPEN — delivery skipped", null);
    }
}
