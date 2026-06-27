package tech.cuia.notifyhub.infrastructure.channel.webhook;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tech.cuia.notifyhub.domain.exception.CircuitBreakerOpenException;
import tech.cuia.notifyhub.domain.exception.NotificationDeliveryException;
import tech.cuia.notifyhub.domain.model.ChannelType;
import tech.cuia.notifyhub.domain.model.Notification;
import tech.cuia.notifyhub.domain.port.out.NotificationChannelPort;
import tech.cuia.notifyhub.infrastructure.metrics.NotificationMetrics;

@Component
public class WebhookChannelAdapter implements NotificationChannelPort {

    private static final Logger log = LoggerFactory.getLogger(WebhookChannelAdapter.class);

    private final RestClient restClient;
    private final NotificationMetrics metrics;

    public WebhookChannelAdapter(WebhookChannelProperties properties, NotificationMetrics metrics) {
        this.restClient = RestClient.builder()
                .requestFactory(buildRequestFactory(properties))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Notify-Hub", "1.0")
                .build();
        this.metrics = metrics;
    }

    @Override
    public ChannelType supportedChannel() {
        return ChannelType.WEBHOOK;
    }

    @Override
    @Retry(name = "webhook")
    @CircuitBreaker(name = "webhook", fallbackMethod = "circuitBreakerFallback")
    public void deliver(Notification notification) {
        var sample = metrics.startDeliveryTimer();
        try {
            restClient.post()
                    .uri(notification.getRecipient())
                    .header("X-Notification-Id", notification.getId().toString())
                    .body(notification.getPayload())
                    .retrieve()
                    // Considera 4xx como falha de negócio (não retryable), 5xx como falha transitória
                    .onStatus(status -> status.is4xxClientError(), (req, res) -> {
                        throw new NotificationDeliveryException(
                                notification.getId(), ChannelType.WEBHOOK,
                                "Recipient returned HTTP " + res.getStatusCode() + " (non-retryable)", null);
                    })
                    .toBodilessEntity();

            metrics.recordSent(ChannelType.WEBHOOK);
            log.debug("Webhook delivered to {} [notificationId={}]",
                    notification.getRecipient(), notification.getId());

        } catch (HttpStatusCodeException e) {
            metrics.recordFailed(ChannelType.WEBHOOK, "http_" + e.getStatusCode().value());
            throw new NotificationDeliveryException(
                    notification.getId(), ChannelType.WEBHOOK, "HTTP " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            metrics.recordFailed(ChannelType.WEBHOOK, "network_error");
            throw new NotificationDeliveryException(
                    notification.getId(), ChannelType.WEBHOOK, "Network error: " + e.getMessage(), e);
        } finally {
            metrics.recordLatency(sample, ChannelType.WEBHOOK);
        }
    }

    private void circuitBreakerFallback(Notification notification, Throwable t) {
        log.error("Circuit breaker OPEN for WEBHOOK channel [notificationId={}]: {}",
                notification.getId(), t.getMessage());
        metrics.recordFailed(ChannelType.WEBHOOK, "circuit_breaker_open");
        throw new CircuitBreakerOpenException(notification.getId(), ChannelType.WEBHOOK);
    }

    private SimpleClientHttpRequestFactory buildRequestFactory(WebhookChannelProperties props) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return factory;
    }
}
