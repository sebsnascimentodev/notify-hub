package tech.cuia.notifyhub.infrastructure.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tech.cuia.notifyhub.application.service.NotificationDispatchService;
import tech.cuia.notifyhub.application.service.NotificationDispatchService.DispatchResult;
import tech.cuia.notifyhub.domain.port.out.NotificationEventPublisher;
import tech.cuia.notifyhub.infrastructure.kafka.config.NotificationEvent;

import java.nio.ByteBuffer;

import static tech.cuia.notifyhub.infrastructure.kafka.config.KafkaConfig.*;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationDispatchService dispatchService;
    private final NotificationEventPublisher eventPublisher;

    public NotificationConsumer(
            NotificationDispatchService dispatchService,
            NotificationEventPublisher eventPublisher) {
        this.dispatchService = dispatchService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Consome dos tópicos {@code pending} e {@code retry} com o mesmo consumer group.
     * O header {@code notification-retry-after} (presente apenas no tópico retry) carrega
     * o timestamp em que a mensagem deve ser processada — implementando backoff exponencial
     * sem precisar de um scheduler externo.
     *
     * <p><b>Trade-off do sleep:</b> bloqueia a thread do consumer, mas evita complexidade de
     * pause/resume de partição. Configurar {@code max.poll.interval.ms >= 65000} no consumer
     * para suportar o delay máximo de 60s sem causar rebalanceamento.</p>
     */
    @KafkaListener(
            topics = {TOPIC_PENDING, TOPIC_RETRY},
            groupId = "notify-hub-consumers",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<String, NotificationEvent> record,
            Acknowledgment acknowledgment) {

        var event = record.value();
        log.debug("Received from topic={} notificationId={} attempt={}",
                record.topic(), event.notificationId(), event.attemptCount());

        applyRetryDelayIfPresent(record.headers().lastHeader(HEADER_RETRY_AFTER));

        try {
            var result = dispatchService.dispatch(event.notificationId());

            switch (result) {
                case DispatchResult.Success s ->
                        log.info("Delivered notificationId={}", s.notificationId());

                case DispatchResult.AlreadyProcessed a ->
                        log.debug("Duplicate skipped notificationId={}", a.notificationId());

                case DispatchResult.ShouldRetry r -> {
                    eventPublisher.publishRetry(r.notificationId(), r.attempts());
                    log.info("Queued for retry notificationId={} attempt={}",
                            r.notificationId(), r.attempts());
                }

                case DispatchResult.ExhaustedRetries e -> {
                    eventPublisher.publishToDlq(e.notificationId());
                    log.warn("Exhausted retries, routed to DLQ notificationId={}", e.notificationId());
                }
            }

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Unexpected error processing notificationId={}: {}",
                    event.notificationId(), e.getMessage(), e);
            // Não faz ack — Spring Kafka vai reintentar ou acionar o error handler configurado
            throw e;
        }
    }

    private void applyRetryDelayIfPresent(Header retryAfterHeader) {
        if (retryAfterHeader == null) return;

        var retryAfterMs = ByteBuffer.wrap(retryAfterHeader.value()).getLong();
        var waitMs = retryAfterMs - System.currentTimeMillis();

        if (waitMs <= 0) return;

        // Clamp: nunca bloquear além do delay máximo configurado para evitar rebalanceamento
        var sleepMs = Math.min(waitMs, 65_000L);
        log.debug("Applying retry delay of {}ms", sleepMs);

        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during retry delay", ie);
        }
    }
}
