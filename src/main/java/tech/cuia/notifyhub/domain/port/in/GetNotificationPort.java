package tech.cuia.notifyhub.domain.port.in;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import tech.cuia.notifyhub.domain.model.ChannelType;
import tech.cuia.notifyhub.domain.model.Notification;
import tech.cuia.notifyhub.domain.model.NotificationStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface GetNotificationPort {

    Optional<Notification> findById(UUID id);

    // Concessão pragmática: Page/Pageable do Spring Data evitam duplicar
    // uma abstração de paginação no domínio sem benefício real de testabilidade.
    Page<Notification> findAll(NotificationFilter filter, Pageable pageable);

    record NotificationFilter(
            NotificationStatus status,
            ChannelType channel,
            Instant from,
            Instant to
    ) {
        public static NotificationFilter empty() {
            return new NotificationFilter(null, null, null, null);
        }
    }
}
