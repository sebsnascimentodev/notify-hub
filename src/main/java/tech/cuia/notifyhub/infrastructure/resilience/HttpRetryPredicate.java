package tech.cuia.notifyhub.infrastructure.resilience;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpStatusCodeException;
import tech.cuia.notifyhub.domain.exception.NotificationDeliveryException;

import java.util.function.Predicate;

/**
 * Determina se uma exceção de entrega webhook deve ser retentada.
 * Erros 4xx (Bad Request, Not Found, etc.) são falhas permanentes de configuração —
 * retentar não vai mudar o resultado, apenas gera ruído no CB.
 * Erros 5xx e de rede são transitórios e merecem retry.
 */
public class HttpRetryPredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable t) {
        if (t instanceof HttpStatusCodeException httpEx) {
            return httpEx.getStatusCode().is5xxServerError();
        }
        if (t instanceof NotificationDeliveryException deliveryEx) {
            // Preserva a decisão tomada em WebhookChannelAdapter para erros 4xx
            return !deliveryEx.getMessage().contains("non-retryable");
        }
        return true;
    }
}
