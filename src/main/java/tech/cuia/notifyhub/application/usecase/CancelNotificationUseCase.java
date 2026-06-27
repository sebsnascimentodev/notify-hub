package tech.cuia.notifyhub.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.cuia.notifyhub.domain.port.in.CancelNotificationPort;
import tech.cuia.notifyhub.domain.port.out.NotificationRepository;

import java.util.UUID;

@Service
@Transactional
public class CancelNotificationUseCase implements CancelNotificationPort {

    private final NotificationRepository repository;

    public CancelNotificationUseCase(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public CancelResult cancel(UUID notificationId) {
        var found = repository.findById(notificationId);

        if (found.isEmpty()) {
            return new CancelResult.NotFound(notificationId);
        }

        var notification = found.get();

        if (!notification.canCancel()) {
            return new CancelResult.NotCancellable(
                    notificationId,
                    "Only PENDING notifications can be cancelled, current status: "
                            + notification.getStatus());
        }

        notification.cancel();
        repository.save(notification);

        return new CancelResult.Cancelled(notificationId);
    }
}
