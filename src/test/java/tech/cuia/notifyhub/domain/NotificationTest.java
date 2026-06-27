package tech.cuia.notifyhub.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.cuia.notifyhub.domain.exception.InvalidNotificationStateException;
import tech.cuia.notifyhub.domain.model.ChannelType;
import tech.cuia.notifyhub.domain.model.Notification;
import tech.cuia.notifyhub.domain.model.NotificationStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Notification — regras de domínio")
class NotificationTest {

    private static final Map<String, Object> PAYLOAD = Map.of("subject", "Olá", "body", "Teste");

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("deve criar com status PENDING e zero tentativas")
        void shouldCreateAsPending() {
            var notification = Notification.create(ChannelType.EMAIL, "user@test.com", PAYLOAD, "key-1");

            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(notification.getAttempts()).isZero();
            assertThat(notification.getId()).isNotNull();
            assertThat(notification.getCreatedAt()).isNotNull();
            assertThat(notification.getSentAt()).isEmpty();
        }

        @Test
        @DisplayName("deve rejeitar recipient em branco")
        void shouldRejectBlankRecipient() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> Notification.create(ChannelType.EMAIL, "  ", PAYLOAD, "key-1"));
        }

        @Test
        @DisplayName("deve rejeitar channel nulo")
        void shouldRejectNullChannel() {
            assertThatNullPointerException()
                    .isThrownBy(() -> Notification.create(null, "user@test.com", PAYLOAD, "key-1"));
        }
    }

    @Nested
    @DisplayName("markAsSent()")
    class MarkAsSent {

        @Test
        @DisplayName("deve transitar de PENDING para SENT e registrar sentAt")
        void shouldTransitFromPendingToSent() {
            var notification = Notification.create(ChannelType.EMAIL, "user@test.com", PAYLOAD, "key-2");

            notification.markAsSent();

            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(notification.getSentAt()).isPresent();
        }

        @Test
        @DisplayName("deve rejeitar transição a partir de CANCELLED")
        void shouldRejectTransitionFromCancelled() {
            var notification = Notification.create(ChannelType.EMAIL, "user@test.com", PAYLOAD, "key-3");
            notification.cancel();

            assertThatExceptionOfType(InvalidNotificationStateException.class)
                    .isThrownBy(notification::markAsSent);
        }
    }

    @Nested
    @DisplayName("canRetry()")
    class CanRetry {

        @Test
        @DisplayName("deve permitir retry quando FAILED e abaixo do limite")
        void shouldAllowRetryWhenFailedAndBelowLimit() {
            var notification = Notification.create(ChannelType.WEBHOOK, "https://hook.test", PAYLOAD, "key-4");
            notification.incrementAttempts(); // 1 tentativa
            notification.markAsFailed();

            assertThat(notification.canRetry(3)).isTrue();
        }

        @Test
        @DisplayName("deve bloquear retry quando atingiu o máximo de tentativas")
        void shouldBlockRetryWhenMaxAttemptsReached() {
            var notification = Notification.create(ChannelType.WEBHOOK, "https://hook.test", PAYLOAD, "key-5");
            notification.incrementAttempts();
            notification.incrementAttempts();
            notification.incrementAttempts();
            notification.markAsFailed();

            assertThat(notification.canRetry(3)).isFalse();
        }
    }

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("deve cancelar notificação PENDING")
        void shouldCancelPending() {
            var notification = Notification.create(ChannelType.EMAIL, "user@test.com", PAYLOAD, "key-6");

            notification.cancel();

            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
        }

        @Test
        @DisplayName("deve rejeitar cancelamento de notificação SENT")
        void shouldRejectCancelOfSent() {
            var notification = Notification.create(ChannelType.EMAIL, "user@test.com", PAYLOAD, "key-7");
            notification.markAsSent();

            assertThatExceptionOfType(InvalidNotificationStateException.class)
                    .isThrownBy(notification::cancel);
        }
    }

    @Test
    @DisplayName("payload deve ser imutável")
    void payloadShouldBeImmutable() {
        var notification = Notification.create(ChannelType.EMAIL, "user@test.com", PAYLOAD, "key-8");

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> notification.getPayload().put("hack", "value"));
    }

    @Test
    @DisplayName("igualdade deve ser baseada apenas no ID")
    void equalityBasedOnId() {
        var n1 = Notification.create(ChannelType.EMAIL, "a@test.com", PAYLOAD, "key-9");
        var n2 = Notification.reconstitute(
                n1.getId(), n1.getChannel(), n1.getRecipient(),
                n1.getPayload(), n1.getIdempotencyKey(),
                n1.getStatus(), n1.getAttempts(),
                n1.getCreatedAt(), n1.getUpdatedAt(), null);

        assertThat(n1).isEqualTo(n2);
    }
}
