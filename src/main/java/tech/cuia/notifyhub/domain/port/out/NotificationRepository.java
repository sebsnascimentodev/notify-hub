package tech.cuia.notifyhub.domain.port.out;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tech.cuia.notifyhub.domain.model.Notification;
import tech.cuia.notifyhub.domain.port.in.GetNotificationPort.NotificationFilter;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    Page<Notification> findAll(NotificationFilter filter, Pageable pageable);
}
