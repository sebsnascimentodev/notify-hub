package tech.cuia.notifyhub.application.usecase;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.cuia.notifyhub.domain.model.Notification;
import tech.cuia.notifyhub.domain.port.in.SendNotificationPort;
import tech.cuia.notifyhub.domain.port.out.NotificationEventPublisher;
import tech.cuia.notifyhub.domain.port.out.NotificationRepository;

import java.util.Objects;

@Service
@Transactional
public class SendNotificationUseCase implements SendNotificationPort {

    private final NotificationRepository repository;
    private final NotificationEventPublisher eventPublisher;

    public SendNotificationUseCase(
            NotificationRepository repository,
            NotificationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public SendResult send(SendCommand command) {
        Objects.requireNonNull(command, "command is required");

        // Idempotência na camada de aplicação: mesma chave → retorna ID existente
        // sem reprocessar nem lançar erro, comportamento seguro para produtores que retentam.
        var existing = repository.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return new SendResult.Duplicate(existing.get().getId());
        }

        var notification = Notification.create(
                command.channel(),
                command.recipient(),
                command.payload(),
                command.idempotencyKey());

        repository.save(notification);

        // Publicar no Kafka fora do commit de transação não é ideal em produção.
        // A solução correta é o padrão Outbox (tabela de eventos + CDC), mas aqui
        // optamos pela simplicidade para manter o foco arquitetural.
        eventPublisher.publishPending(notification);

        return new SendResult.Accepted(notification.getId());
    }
}
