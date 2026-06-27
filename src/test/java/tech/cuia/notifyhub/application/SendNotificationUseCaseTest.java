package tech.cuia.notifyhub.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.cuia.notifyhub.application.usecase.SendNotificationUseCase;
import tech.cuia.notifyhub.domain.model.ChannelType;
import tech.cuia.notifyhub.domain.model.Notification;
import tech.cuia.notifyhub.domain.model.NotificationStatus;
import tech.cuia.notifyhub.domain.port.in.SendNotificationPort.SendCommand;
import tech.cuia.notifyhub.domain.port.in.SendNotificationPort.SendResult;
import tech.cuia.notifyhub.domain.port.out.NotificationEventPublisher;
import tech.cuia.notifyhub.domain.port.out.NotificationRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SendNotificationUseCase")
class SendNotificationUseCaseTest {

    @Mock
    private NotificationRepository repository;

    @Mock
    private NotificationEventPublisher eventPublisher;

    @InjectMocks
    private SendNotificationUseCase useCase;

    private final SendCommand validCommand = new SendCommand(
            ChannelType.EMAIL,
            "user@test.com",
            Map.of("subject", "Bem-vindo", "body", "Olá!"),
            "signup-user-42"
    );

    @Test
    @DisplayName("deve aceitar nova notificação e publicar evento")
    void shouldAcceptAndPublish() {
        when(repository.findByIdempotencyKey("signup-user-42")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = useCase.send(validCommand);

        assertThat(result).isInstanceOf(SendResult.Accepted.class);
        verify(repository).save(argThat(n ->
                n.getStatus() == NotificationStatus.PENDING &&
                n.getChannel() == ChannelType.EMAIL &&
                n.getAttempts() == 0));
        verify(eventPublisher).publishPending(any(Notification.class));
    }

    @Test
    @DisplayName("deve retornar Duplicate para idempotencyKey já existente")
    void shouldReturnDuplicateForExistingKey() {
        var existingId = UUID.randomUUID();
        var existing = Notification.create(ChannelType.EMAIL, "user@test.com", Map.of(), "signup-user-42");
        when(repository.findByIdempotencyKey("signup-user-42")).thenReturn(Optional.of(existing));

        var result = useCase.send(validCommand);

        assertThat(result).isInstanceOf(SendResult.Duplicate.class);
        verify(repository, never()).save(any());
        verify(eventPublisher, never()).publishPending(any());
    }
}
