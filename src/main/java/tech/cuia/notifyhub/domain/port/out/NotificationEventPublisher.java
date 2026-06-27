package tech.cuia.notifyhub.domain.port.out;

import tech.cuia.notifyhub.domain.model.Notification;
import java.util.UUID;

public interface NotificationEventPublisher {

    void publishPending(Notification notification);

    // Só o ID é necessário — o producer calcula o delay e encapsula no header Kafka.
    void publishRetry(UUID notificationId, int currentAttempts);

    void publishToDlq(UUID notificationId);
}
