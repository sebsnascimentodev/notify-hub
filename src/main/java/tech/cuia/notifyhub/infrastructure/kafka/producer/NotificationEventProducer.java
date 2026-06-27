package tech.cuia.notifyhub.infrastructure.kafka.producer;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tech.cuia.notifyhub.domain.model.Notification;
import tech.cuia.notifyhub.domain.port.out.NotificationEventPublisher;
import tech.cuia.notifyhub.infrastructure.kafka.config.NotificationEvent;

import java.nio.ByteBuffer;
import java.util.UUID;

import static tech.cuia.notifyhub.infrastructure.kafka.config.KafkaConfig.*;

@Component
public class NotificationEventProducer implements NotificationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventProducer.class);

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public NotificationEventProducer(KafkaTemplate<String, NotificationEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishPending(Notification notification) {
        var event = new NotificationEvent(notification.getId(), 0);
        kafkaTemplate.send(TOPIC_PENDING, notification.getId().toString(), event);
        log.debug("Published to pending: notificationId={}", notification.getId());
    }

    /**
     * Publica no tópico retry com header {@code notification-retry-after} (timestamp ms).
     * O consumer lê o header e aguarda até esse instante antes de processar,
     * implementando backoff exponencial: tentativa 1→1s, 2→2s, 3→4s.
     */
    @Override
    public void publishRetry(UUID notificationId, int currentAttempts) {
        var delayMs = exponentialDelayMs(currentAttempts);
        var retryAfterMs = System.currentTimeMillis() + delayMs;

        var record = new ProducerRecord<String, NotificationEvent>(
                TOPIC_RETRY,
                notificationId.toString(),
                new NotificationEvent(notificationId, currentAttempts));

        // Header como long big-endian — determinístico, sem parsing de String
        record.headers().add(HEADER_RETRY_AFTER,
                ByteBuffer.allocate(Long.BYTES).putLong(retryAfterMs).array());

        kafkaTemplate.send(record);
        log.info("Published to retry: notificationId={} attempt={} delayMs={}",
                notificationId, currentAttempts, delayMs);
    }

    @Override
    public void publishToDlq(UUID notificationId) {
        kafkaTemplate.send(TOPIC_DLQ, notificationId.toString(),
                new NotificationEvent(notificationId, -1));
        log.warn("Published to DLQ: notificationId={}", notificationId);
    }

    private long exponentialDelayMs(int attempt) {
        // Clamp em 60s para não bloquear o consumer por períodos absurdos
        return Math.min((long) Math.pow(2, attempt - 1) * 1_000L, 60_000L);
    }
}
