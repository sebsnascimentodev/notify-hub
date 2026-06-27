package tech.cuia.notifyhub.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tech.cuia.notifyhub.infrastructure.persistence.entity.NotificationJpaEntity;

import java.util.Optional;
import java.util.UUID;

public interface NotificationJpaRepository
        extends JpaRepository<NotificationJpaEntity, UUID>,
                JpaSpecificationExecutor<NotificationJpaEntity> {

    Optional<NotificationJpaEntity> findByIdempotencyKey(String idempotencyKey);
}
