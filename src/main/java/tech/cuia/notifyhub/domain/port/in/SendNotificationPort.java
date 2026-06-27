package tech.cuia.notifyhub.domain.port.in;

import tech.cuia.notifyhub.domain.model.ChannelType;

import java.util.Map;
import java.util.UUID;

public interface SendNotificationPort {

    SendResult send(SendCommand command);

    record SendCommand(
            ChannelType channel,
            String recipient,
            Map<String, Object> payload,
            String idempotencyKey
    ) {}

    sealed interface SendResult permits SendResult.Accepted, SendResult.Duplicate {
        record Accepted(UUID notificationId) implements SendResult {}
        record Duplicate(UUID existingId) implements SendResult {}
    }
}
