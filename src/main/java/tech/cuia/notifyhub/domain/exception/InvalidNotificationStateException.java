package tech.cuia.notifyhub.domain.exception;

import tech.cuia.notifyhub.domain.model.NotificationStatus;

import java.util.UUID;

public class InvalidNotificationStateException extends RuntimeException {

    private final UUID notificationId;
    private final NotificationStatus currentStatus;
    private final String operation;

    public InvalidNotificationStateException(UUID notificationId, NotificationStatus currentStatus, String operation) {
        super("Cannot perform '%s' on notification %s in status %s"
                .formatted(operation, notificationId, currentStatus));
        this.notificationId = notificationId;
        this.currentStatus = currentStatus;
        this.operation = operation;
    }

    public UUID getNotificationId() { return notificationId; }
    public NotificationStatus getCurrentStatus() { return currentStatus; }
    public String getOperation() { return operation; }
}
