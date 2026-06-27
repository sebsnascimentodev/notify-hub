package tech.cuia.notifyhub.infrastructure.persistence.repository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import tech.cuia.notifyhub.domain.model.Notification;
import tech.cuia.notifyhub.domain.port.in.GetNotificationPort.NotificationFilter;
import tech.cuia.notifyhub.domain.port.out.NotificationRepository;
import tech.cuia.notifyhub.infrastructure.persistence.entity.NotificationJpaEntity;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final NotificationJpaRepository jpa;

    public NotificationRepositoryAdapter(NotificationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Notification save(Notification notification) {
        return toDomain(jpa.save(toEntity(notification)));
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Notification> findByIdempotencyKey(String idempotencyKey) {
        return jpa.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public Page<Notification> findAll(NotificationFilter filter, Pageable pageable) {
        return jpa.findAll(toSpec(filter), pageable).map(this::toDomain);
    }

    // -------------------------------------------------------------------------
    // Mapping — domínio ↔ entidade JPA (sem framework externo)
    // -------------------------------------------------------------------------

    private Notification toDomain(NotificationJpaEntity e) {
        return Notification.reconstitute(
                e.getId(), e.getChannel(), e.getRecipient(),
                e.getPayload(), e.getIdempotencyKey(),
                e.getStatus(), e.getAttempts(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getSentAt());
    }

    private NotificationJpaEntity toEntity(Notification n) {
        var e = new NotificationJpaEntity();
        e.setId(n.getId());
        e.setChannel(n.getChannel());
        e.setRecipient(n.getRecipient());
        e.setPayload(n.getPayload());
        e.setIdempotencyKey(n.getIdempotencyKey());
        e.setStatus(n.getStatus());
        e.setAttempts(n.getAttempts());
        e.setCreatedAt(n.getCreatedAt());
        e.setUpdatedAt(n.getUpdatedAt());
        e.setSentAt(n.getSentAt().orElse(null));
        return e;
    }

    private Specification<NotificationJpaEntity> toSpec(NotificationFilter filter) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();

            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.channel() != null) {
                predicates.add(cb.equal(root.get("channel"), filter.channel()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.to()));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
