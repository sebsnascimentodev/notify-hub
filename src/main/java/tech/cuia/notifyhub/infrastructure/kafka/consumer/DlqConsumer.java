package tech.cuia.notifyhub.infrastructure.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tech.cuia.notifyhub.domain.port.out.NotificationRepository;
import tech.cuia.notifyhub.infrastructure.kafka.config.NotificationEvent;
import tech.cuia.notifyhub.infrastructure.metrics.NotificationMetrics;

import static tech.cuia.notifyhub.infrastructure.kafka.config.KafkaConfig.*;

/**
 * Consome o tópico DLQ para observabilidade e alertas.
 * O status da notificação já foi atualizado para DLQ no banco pelo DispatchService;
 * este consumer é responsável apenas por métricas e logging estruturado.
 * Ponto de extensão natural para integrar PagerDuty, Slack ou e-mail de ops.
 */
@Component
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    private final NotificationRepository repository;
    private final NotificationMetrics metrics;

    public DlqConsumer(NotificationRepository repository, NotificationMetrics metrics) {
        this.repository = repository;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = TOPIC_DLQ,
            groupId = "notify-hub-dlq-consumers",
            containerFactory = "dlqListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<String, NotificationEvent> record,
            Acknowledgment acknowledgment) {

        var event = record.value();

        repository.findById(event.notificationId()).ifPresentOrElse(
                notification -> {
                    metrics.recordDlq(notification.getChannel());
                    log.error("[DLQ] Notification permanently failed: notificationId={} channel={} recipient={} attempts={}",
                            notification.getId(),
                            notification.getChannel(),
                            notification.getRecipient(),
                            notification.getAttempts());
                },
                () -> log.error("[DLQ] Notification not found in DB: notificationId={}", event.notificationId())
        );

        acknowledgment.acknowledge();
    }
}
