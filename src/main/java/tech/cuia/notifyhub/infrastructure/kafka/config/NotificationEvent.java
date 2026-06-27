package tech.cuia.notifyhub.infrastructure.kafka.config;

import java.util.UUID;

/**
 * Evento transportado entre tópicos Kafka.
 * Carrega apenas o ID — consumer sempre carrega o estado mais recente do banco,
 * evitando stale data quando a notificação é atualizada entre publish e consume.
 *
 * {@code attemptCount} é incluído para o consumer poder calcular o próximo delay
 * de backoff sem carregar a entidade inteira apenas para ler esse campo.
 */
public record NotificationEvent(UUID notificationId, int attemptCount) {

    // Construtor sem argumento necessário para o JsonDeserializer do Spring Kafka
    public NotificationEvent() {
        this(null, 0);
    }
}
