package tech.cuia.notifyhub.domain.exception;

import tech.cuia.notifyhub.domain.model.ChannelType;

import java.util.UUID;

public class NotificationDeliveryException extends RuntimeException {

    private final UUID notificationId;
    private final ChannelType channel;

    public NotificationDeliveryException(UUID notificationId, ChannelType channel, String message, Throwable cause) {
        super("Delivery failed for notification %s via %s: %s".formatted(notificationId, channel, message), cause);
        this.notificationId = notificationId;
        this.channel = channel;
    }

    public UUID getNotificationId() { return notificationId; }
    public ChannelType getChannel() { return channel; }
}
