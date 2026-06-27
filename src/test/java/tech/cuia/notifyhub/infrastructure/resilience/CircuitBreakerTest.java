package tech.cuia.notifyhub.infrastructure.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tech.cuia.notifyhub.domain.exception.NotificationDeliveryException;
import tech.cuia.notifyhub.domain.model.ChannelType;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Testa o comportamento do Circuit Breaker independentemente do Spring AOP.
 * Usa o CircuitBreakerRegistry programaticamente para garantir testes rápidos,
 * determinísticos e sem necessidade de contexto Spring.
 *
 * Estes testes complementam os testes de integração (NotificationConsumerIntegrationTest)
 * que verificam o CB em conjunto com os channel adapters e AOP.
 */
@DisplayName("Circuit Breaker — configuração e máquina de estados")
class CircuitBreakerTest {

    private static final CircuitBreakerConfig TEST_CONFIG = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50f)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(false)
            .recordExceptions(NotificationDeliveryException.class)
            .build();

    private CircuitBreakerRegistry registry;

    @BeforeEach
    void setUp() {
        registry = CircuitBreakerRegistry.of(TEST_CONFIG);
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Transição CLOSED → OPEN")
    class ClosedToOpen {

        @Test
        @DisplayName("deve abrir após 50% de falhas em 10 chamadas (mínimo 5)")
        void shouldOpenAfterFailureThreshold() {
            var cb = registry.circuitBreaker("email");
            var notification = notificationId();

            // 6 falhas em 10 chamadas = 60% > threshold de 50%
            IntStream.range(0, 10).forEach(i ->
                    recordResult(cb, i < 6
                            ? new NotificationDeliveryException(notification, ChannelType.EMAIL, "SMTP error", null)
                            : null));

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThat(cb.getMetrics().getFailureRate()).isGreaterThanOrEqualTo(50f);
        }

        @Test
        @DisplayName("deve permanecer CLOSED com taxa de falha abaixo do threshold")
        void shouldRemainClosedBelowThreshold() {
            var cb = registry.circuitBreaker("email");
            var notification = notificationId();

            // 4 falhas em 10 = 40% < threshold de 50%
            IntStream.range(0, 10).forEach(i ->
                    recordResult(cb, i < 4
                            ? new NotificationDeliveryException(notification, ChannelType.EMAIL, "error", null)
                            : null));

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("não deve abrir antes do número mínimo de chamadas")
        void shouldNotOpenBeforeMinimumCalls() {
            var cb = registry.circuitBreaker("email");
            var notification = notificationId();

            // Apenas 4 chamadas (< mínimo de 5), todas falhas
            IntStream.range(0, 4).forEach(i ->
                    recordResult(cb, new NotificationDeliveryException(notification, ChannelType.EMAIL, "error", null)));

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Transição OPEN → HALF_OPEN → CLOSED")
    class Recovery {

        @Test
        @DisplayName("deve fechar após chamadas bem-sucedidas em HALF_OPEN")
        void shouldCloseAfterSuccessInHalfOpen() {
            var cb = registry.circuitBreaker("email");
            cb.transitionToOpenState();
            cb.transitionToHalfOpenState();

            // 3 chamadas bem-sucedidas (= permittedNumberOfCallsInHalfOpenState)
            IntStream.range(0, 3).forEach(i -> recordResult(cb, null));

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }

        @Test
        @DisplayName("deve reabrir após falha em HALF_OPEN")
        void shouldReopenAfterFailureInHalfOpen() {
            var cb = registry.circuitBreaker("email");
            var notification = notificationId();
            cb.transitionToOpenState();
            cb.transitionToHalfOpenState();

            // 1 falha em 3 chamadas em HALF_OPEN = 33% > 0 threshold → reabre
            recordResult(cb, new NotificationDeliveryException(notification, ChannelType.EMAIL, "error", null));
            recordResult(cb, null);
            recordResult(cb, new NotificationDeliveryException(notification, ChannelType.EMAIL, "error", null));

            assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        }
    }

    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Exceções ignoradas")
    class IgnoredExceptions {

        @Test
        @DisplayName("exceções não registradas não devem contar como falha no CB")
        void unregisteredExceptionsShouldNotCountAsFailure() {
            // CB configurado apenas para NotificationDeliveryException
            var cb = registry.circuitBreaker("webhook");

            // RuntimeException pura não está em recordExceptions → não conta
            IntStream.range(0, 10).forEach(i -> {
                cb.onError(0, TimeUnit.NANOSECONDS, new IllegalStateException("ignored"));
            });

            // CB não deve abrir pois o tipo de exceção não está configurado
            assertThat(cb.getMetrics().getNumberOfFailedCalls()).isZero();
        }
    }

    // -------------------------------------------------------------------------

    private void recordResult(CircuitBreaker cb, Exception error) {
        if (error == null) {
            cb.onSuccess(0, TimeUnit.NANOSECONDS);
        } else {
            cb.onError(0, TimeUnit.NANOSECONDS, error);
        }
    }

    private UUID notificationId() {
        return UUID.randomUUID();
    }
}
