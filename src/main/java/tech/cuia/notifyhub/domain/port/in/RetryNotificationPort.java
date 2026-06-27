package tech.cuia.notifyhub.domain.port.in;

import java.util.UUID;

public interface RetryNotificationPort {

    RetryResult retry(UUID notificationId);

    sealed interface RetryResult
            permits RetryResult.Queued, RetryResult.NotFound, RetryResult.NotRetryable {
        record Queued(UUID notificationId) implements RetryResult {}
        record NotFound(UUID notificationId) implements RetryResult {}
        record NotRetryable(UUID notificationId, String reason) implements RetryResult {}
    }
}
