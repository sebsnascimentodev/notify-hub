package tech.cuia.notifyhub.infrastructure.resilience;

import io.github.resilience4j.common.circuitbreaker.configuration.CircuitBreakerConfigCustomizer;
import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tech.cuia.notifyhub.domain.exception.CircuitBreakerOpenException;
import tech.cuia.notifyhub.domain.exception.NotificationDeliveryException;

/**
 * Complementa a configuração YAML do Resilience4j com predicados baseados em tipos de exceção.
 * As propriedades numéricas (threshold, timeout, janela) vivem em application.yml
 * para serem externalizáveis por ambiente — aqui ficam apenas as regras de classificação.
 */
@Configuration
public class ResilienceConfig {

    /**
     * EMAIL Circuit Breaker:
     * - Só conta NotificationDeliveryException como falha (erros de negócio não abrem o CB)
     * - Ignora CircuitBreakerOpenException (evita double-counting quando o CB já está OPEN)
     */
    @Bean
    public CircuitBreakerConfigCustomizer emailCircuitBreakerCustomizer() {
        return CircuitBreakerConfigCustomizer.of("email", builder -> builder
                .recordExceptions(NotificationDeliveryException.class)
                .ignoreExceptions(CircuitBreakerOpenException.class));
    }

    @Bean
    public CircuitBreakerConfigCustomizer webhookCircuitBreakerCustomizer() {
        return CircuitBreakerConfigCustomizer.of("webhook", builder -> builder
                .recordExceptions(NotificationDeliveryException.class)
                .ignoreExceptions(CircuitBreakerOpenException.class));
    }

    /**
     * Retry não retenta quando o CB está OPEN (CallNotPermittedException).
     * Sem este ignore, o Retry tentaria N vezes contra um CB fechado — desperdício de thread.
     */
    @Bean
    public RetryConfigCustomizer emailRetryCustomizer() {
        return RetryConfigCustomizer.of("email", builder -> builder
                .ignoreExceptions(CircuitBreakerOpenException.class));
    }

    @Bean
    public RetryConfigCustomizer webhookRetryCustomizer() {
        return RetryConfigCustomizer.of("webhook", builder -> builder
                .ignoreExceptions(CircuitBreakerOpenException.class));
    }
}
