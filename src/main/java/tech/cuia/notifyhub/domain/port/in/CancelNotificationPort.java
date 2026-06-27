package tech.cuia.notifyhub.domain.port.in;

import java.util.UUID;

public interface CancelNotificationPort {

    CancelResult cancel(UUID notificationId);

    sealed interface CancelResult
            permits CancelResult.Cancelled, CancelResult.NotFound, CancelResult.NotCancellable {
        record Cancelled(UUID notificationId) implements CancelResult {}
        record NotFound(UUID notificationId) implements CancelResult {}
        record NotCancellable(UUID notificationId, String reason) implements CancelResult {}
    }
}
