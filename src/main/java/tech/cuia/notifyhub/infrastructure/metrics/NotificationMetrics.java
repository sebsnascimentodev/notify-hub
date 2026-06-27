package tech.cuia.notifyhub.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import tech.cuia.notifyhub.domain.model.ChannelType;

import java.time.Duration;

@Component
public class NotificationMetrics {

    private final MeterRegistry registry;

    public NotificationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // Micrometer adiciona _total automaticamente para Counters no formato Prometheus.
    // Nomes sem sufixo _total aqui → prometheus: notify_hub_sent_total, notify_hub_failed_total, notify_hub_dlq_total

    public void recordSent(ChannelType channel) {
        Counter.builder("notify_hub.sent")
                .description("Total de notificações entregues com sucesso")
                .tag("channel", channel.name().toLowerCase())
                .register(registry)
                .increment();
    }

    public void recordFailed(ChannelType channel, String reason) {
        Counter.builder("notify_hub.failed")
                .description("Total de falhas de entrega")
                .tag("channel", channel.name().toLowerCase())
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    public void recordDlq(ChannelType channel) {
        Counter.builder("notify_hub.dlq")
                .description("Total de notificações enviadas para DLQ")
                .tag("channel", channel.name().toLowerCase())
                .register(registry)
                .increment();
    }

    public Timer.Sample startDeliveryTimer() {
        return Timer.start(registry);
    }

    public void recordLatency(Timer.Sample sample, ChannelType channel) {
        sample.stop(Timer.builder("notify_hub.latency")
                .description("Latência de entrega por canal")
                .tag("channel", channel.name().toLowerCase())
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(100),
                        Duration.ofMillis(500),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(5))
                .register(registry));
    }
}
