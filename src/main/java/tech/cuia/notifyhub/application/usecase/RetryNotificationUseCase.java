package tech.cuia.notifyhub.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.cuia.notifyhub.domain.model.NotificationStatus;
import tech.cuia.notifyhub.domain.port.in.RetryNotificationPort;
import tech.cuia.notifyhub.domain.port.out.NotificationEventPublisher;
import tech.cuia.notifyhub.domain.port.out.NotificationRepository;

import java.util.UUID;

@Service
@Transactional
public class RetryNotificationUseCase implements RetryNotificationPort {

    private final NotificationRepository repository;
    private final NotificationEventPublisher eventPublisher;

    public RetryNotificationUseCase(
            NotificationRepository repository,
            NotificationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public RetryResult retry(UUID notificationId) {
        var found = repository.findById(notificationId);

        if (found.isEmpty()) {
            return new RetryResult.NotFound(notificationId);
        }

        var notification = found.get();

        if (!notification.getStatus().isRetryable()) {
            return new RetryResult.NotRetryable(
                    notificationId,
                    "Only FAILED or DLQ notifications can be retried, current status: "
                            + notification.getStatus());
        }

        // Retry manual: zera tentativas para dar nova janela completa de 3 tentativas.
        notification.resetAttempts();
        repository.save(notification);
        eventPublisher.publishPending(notification);

        return new RetryResult.Queued(notificationId);
    }
}
