package tech.cuia.notifyhub.application.usecase;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.cuia.notifyhub.domain.model.Notification;
import tech.cuia.notifyhub.domain.port.in.GetNotificationPort;
import tech.cuia.notifyhub.domain.port.out.NotificationRepository;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetNotificationUseCase implements GetNotificationPort {

    private final NotificationRepository repository;

    public GetNotificationUseCase(NotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Page<Notification> findAll(NotificationFilter filter, Pageable pageable) {
        return repository.findAll(filter, pageable);
    }
}
