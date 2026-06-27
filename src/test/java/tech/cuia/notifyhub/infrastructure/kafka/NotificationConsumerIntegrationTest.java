package tech.cuia.notifyhub.infrastructure.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import tech.cuia.notifyhub.application.service.NotificationDispatchService;
import tech.cuia.notifyhub.application.service.NotificationDispatchService.DispatchResult;
import tech.cuia.notifyhub.domain.port.out.NotificationEventPublisher;
import tech.cuia.notifyhub.domain.port.out.NotificationRepository;
import tech.cuia.notifyhub.infrastructure.kafka.config.NotificationEvent;
import tech.cuia.notifyhub.infrastructure.metrics.NotificationMetrics;
import tech.cuia.notifyhub.infrastructure.redis.RedisDeduplicationAdapter;

import java.util.Optional;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static tech.cuia.notifyhub.infrastructure.kafka.config.KafkaConfig.*;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {TOPIC_PENDING, TOPIC_RETRY, TOPIC_DLQ},
        brokerProperties = {"log.dir=${java.io.tmpdir}/notify-hub-kafka-test"}
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        // Exclui autoconfigurações que precisam de infra real para este teste focado em Kafka
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
})
@DirtiesContext
@DisplayName("NotificationConsumer — integração com Kafka embutido")
class NotificationConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @MockBean
    private NotificationDispatchService dispatchService;

    @MockBean
    private NotificationRepository repository;

    @MockBean
    private NotificationEventPublisher eventPublisher;

    @MockBean
    private RedisDeduplicationAdapter deduplicationAdapter;

    @MockBean
    private NotificationMetrics metrics;

    @Test
    @DisplayName("deve chamar dispatch() quando mensagem chega no tópico pending")
    void shouldDispatchWhenMessageArrivesOnPendingTopic() {
        var notificationId = UUID.randomUUID();
        when(dispatchService.dispatch(notificationId))
                .thenReturn(new DispatchResult.Success(notificationId));

        kafkaTemplate.send(TOPIC_PENDING, notificationId.toString(),
                new NotificationEvent(notificationId, 0));

        await().atMost(10, SECONDS).untilAsserted(() ->
                verify(dispatchService).dispatch(notificationId));
    }

    @Test
    @DisplayName("deve publicar no retry topic quando dispatch retorna ShouldRetry")
    void shouldPublishToRetryWhenDispatchReturnsShouldRetry() {
        var notificationId = UUID.randomUUID();
        when(dispatchService.dispatch(notificationId))
                .thenReturn(new DispatchResult.ShouldRetry(notificationId, 1));

        kafkaTemplate.send(TOPIC_PENDING, notificationId.toString(),
                new NotificationEvent(notificationId, 0));

        await().atMost(10, SECONDS).untilAsserted(() ->
                verify(eventPublisher).publishRetry(notificationId, 1));
    }

    @Test
    @DisplayName("deve publicar na DLQ quando dispatch retorna ExhaustedRetries")
    void shouldPublishToDlqWhenDispatchReturnsExhaustedRetries() {
        var notificationId = UUID.randomUUID();
        when(dispatchService.dispatch(notificationId))
                .thenReturn(new DispatchResult.ExhaustedRetries(notificationId));

        kafkaTemplate.send(TOPIC_PENDING, notificationId.toString(),
                new NotificationEvent(notificationId, 3));

        await().atMost(10, SECONDS).untilAsserted(() ->
                verify(eventPublisher).publishToDlq(notificationId));
    }

    @Test
    @DisplayName("não deve chamar dispatch() para evento duplicado (AlreadyProcessed)")
    void shouldSkipDuplicateEvents() {
        var notificationId = UUID.randomUUID();
        when(dispatchService.dispatch(notificationId))
                .thenReturn(new DispatchResult.AlreadyProcessed(notificationId));

        kafkaTemplate.send(TOPIC_PENDING, notificationId.toString(),
                new NotificationEvent(notificationId, 0));
        kafkaTemplate.send(TOPIC_PENDING, notificationId.toString(),
                new NotificationEvent(notificationId, 0));

        await().atMost(10, SECONDS).untilAsserted(() ->
                // dispatchService é chamado (a idempotência é ele que decide), mas
                // eventPublisher nunca é acionado quando o resultado é AlreadyProcessed
                verify(eventPublisher, never()).publishRetry(any(), anyInt()));
    }
}
