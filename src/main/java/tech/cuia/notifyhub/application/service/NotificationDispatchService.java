package tech.cuia.notifyhub.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.cuia.notifyhub.domain.exception.CircuitBreakerOpenException;
import tech.cuia.notifyhub.domain.exception.NotificationNotFoundException;
import tech.cuia.notifyhub.domain.model.ChannelType;
import tech.cuia.notifyhub.domain.port.out.DeduplicationPort;
import tech.cuia.notifyhub.domain.port.out.NotificationChannelPort;
import tech.cuia.notifyhub.domain.port.out.NotificationRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orquestra o ciclo de vida de entrega de uma notificação.
 * Chamado pelo Kafka consumer — retorna um {@link DispatchResult} para que o
 * consumer decida roteamento (retry topic vs DLQ) sem conhecer a lógica de negócio.
 */
@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final Map<ChannelType, NotificationChannelPort> channels;
    private final NotificationRepository repository;
    private final DeduplicationPort deduplication;

    @Value("${notify-hub.delivery.max-attempts:3}")
    private int maxAttempts;

    public NotificationDispatchService(
            List<NotificationChannelPort> channelList,
            NotificationRepository repository,
            DeduplicationPort deduplication) {
        // Constrói o mapa canal → adapter uma única vez na inicialização do bean
        this.channels = channelList.stream()
                .collect(Collectors.toUnmodifiableMap(
                        NotificationChannelPort::supportedChannel,
                        Function.identity()));
        this.repository = repository;
        this.deduplication = deduplication;
    }

    @Transactional
    public DispatchResult dispatch(UUID notificationId) {
        // Idempotência a nível de consumo: Redis garante "exatamente uma entrega bem-sucedida"
        // mesmo com at-least-once do Kafka (rebalanceamentos, reprocessamentos após crash).
        if (deduplication.isDuplicate(notificationId)) {
            log.debug("Duplicate event skipped: {}", notificationId);
            return new DispatchResult.AlreadyProcessed(notificationId);
        }

        var notification = repository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        if (notification.getStatus().isTerminal()) {
            log.warn("Terminal notification received on queue, skipping: {}", notification);
            return new DispatchResult.AlreadyProcessed(notificationId);
        }

        notification.incrementAttempts();

        var channel = channels.get(notification.getChannel());
        if (channel == null) {
            log.error("No channel adapter registered for: {}", notification.getChannel());
            notification.sendToDlq();
            repository.save(notification);
            return new DispatchResult.ExhaustedRetries(notificationId);
        }

        try {
            channel.deliver(notification);
            notification.markAsSent();
            repository.save(notification);
            deduplication.markAsProcessed(notificationId);
            log.info("Notification delivered: {}", notification);
            return new DispatchResult.Success(notificationId);

        } catch (CircuitBreakerOpenException e) {
            // CB OPEN → não adianta retentar; vai direto para DLQ e libera a thread do consumer.
            log.error("Circuit breaker OPEN — routing directly to DLQ: {}", notification);
            notification.sendToDlq();
            repository.save(notification);
            return new DispatchResult.ExhaustedRetries(notificationId);

        } catch (Exception e) {
            log.warn("Delivery failed for {}: {}", notification, e.getMessage());
            notification.markAsFailed();
            repository.save(notification);

            if (notification.canRetry(maxAttempts)) {
                return new DispatchResult.ShouldRetry(notificationId, notification.getAttempts());
            }

            notification.sendToDlq();
            repository.save(notification);
            return new DispatchResult.ExhaustedRetries(notificationId);
        }
    }

    // -------------------------------------------------------------------------
    // Resultado selado — permite pattern matching exhaustivo no consumer Kafka
    // -------------------------------------------------------------------------

    public sealed interface DispatchResult
            permits DispatchResult.Success, DispatchResult.AlreadyProcessed,
                    DispatchResult.ShouldRetry, DispatchResult.ExhaustedRetries {

        record Success(UUID notificationId) implements DispatchResult {}
        record AlreadyProcessed(UUID notificationId) implements DispatchResult {}
        record ShouldRetry(UUID notificationId, int attempts) implements DispatchResult {}
        record ExhaustedRetries(UUID notificationId) implements DispatchResult {}
    }
}
