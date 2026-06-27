package tech.cuia.notifyhub.infrastructure.channel.email;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import tech.cuia.notifyhub.domain.exception.CircuitBreakerOpenException;
import tech.cuia.notifyhub.domain.exception.NotificationDeliveryException;
import tech.cuia.notifyhub.domain.model.ChannelType;
import tech.cuia.notifyhub.domain.model.Notification;
import tech.cuia.notifyhub.domain.port.out.NotificationChannelPort;
import tech.cuia.notifyhub.infrastructure.metrics.NotificationMetrics;

import java.nio.charset.StandardCharsets;

@Component
public class EmailChannelAdapter implements NotificationChannelPort {

    private static final Logger log = LoggerFactory.getLogger(EmailChannelAdapter.class);

    private final JavaMailSender mailSender;
    private final NotificationMetrics metrics;

    public EmailChannelAdapter(JavaMailSender mailSender, NotificationMetrics metrics) {
        this.mailSender = mailSender;
        this.metrics = metrics;
    }

    @Override
    public ChannelType supportedChannel() {
        return ChannelType.EMAIL;
    }

    /**
     * {@code @Retry} trata falhas transitórias rápidas (ex: timeout de conexão SMTP).
     * {@code @CircuitBreaker} isola o canal quando a taxa de falhas ultrapassa o threshold,
     * evitando que threads fiquem paradas esperando por um servidor inoperante.
     * A ordem AOP no Resilience4j: Retry(CircuitBreaker(Function)) —
     * o CB conta falha após todas as tentativas do Retry se esgotarem.
     */
    @Override
    @Retry(name = "email")
    @CircuitBreaker(name = "email", fallbackMethod = "circuitBreakerFallback")
    public void deliver(Notification notification) {
        var sample = metrics.startDeliveryTimer();
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setTo(notification.getRecipient());
            helper.setSubject((String) notification.getPayload()
                    .getOrDefault("subject", "Notification from notify-hub"));

            var body = (String) notification.getPayload().getOrDefault("body", "");
            var html = Boolean.TRUE.equals(notification.getPayload().get("html"));
            helper.setText(body, html);

            mailSender.send(message);
            metrics.recordSent(ChannelType.EMAIL);
            log.debug("Email delivered to {} [notificationId={}]",
                    notification.getRecipient(), notification.getId());

        } catch (MessagingException e) {
            metrics.recordFailed(ChannelType.EMAIL, "messaging_error");
            throw new NotificationDeliveryException(
                    notification.getId(), ChannelType.EMAIL, e.getMessage(), e);
        } finally {
            metrics.recordLatency(sample, ChannelType.EMAIL);
        }
    }

    // Assinatura exigida pelo Resilience4j: mesmo retorno + Throwable no final
    private void circuitBreakerFallback(Notification notification, Throwable t) {
        log.error("Circuit breaker OPEN for EMAIL channel [notificationId={}]: {}",
                notification.getId(), t.getMessage());
        metrics.recordFailed(ChannelType.EMAIL, "circuit_breaker_open");
        throw new CircuitBreakerOpenException(notification.getId(), ChannelType.EMAIL);
    }
}
