package tech.cuia.notifyhub.infrastructure.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tech.cuia.notifyhub.domain.port.out.DeduplicationPort;

import java.time.Duration;
import java.util.UUID;

@Component
public class RedisDeduplicationAdapter implements DeduplicationPort {

    private static final Logger log = LoggerFactory.getLogger(RedisDeduplicationAdapter.class);
    private static final String KEY_PREFIX = "dedup:notification:";

    private final StringRedisTemplate redis;
    private final Duration deduplicationTtl;

    public RedisDeduplicationAdapter(
            StringRedisTemplate redis,
            @Value("${notify-hub.delivery.deduplication.ttl:PT24H}") Duration deduplicationTtl) {
        this.redis = redis;
        this.deduplicationTtl = deduplicationTtl;
    }

    /**
     * SETNX atômico: apenas um consumer vence a corrida entre partições paralelas.
     * Retorna {@code true} (é duplicata) se a chave já existia.
     *
     * <p>Usa TTL curto aqui (5 min) para travar o processamento concorrente.
     * {@link #markAsProcessed} estende para o TTL completo após entrega confirmada.</p>
     */
    @Override
    public boolean isDuplicate(UUID notificationId) {
        var key = KEY_PREFIX + notificationId;
        // setIfAbsent = SETNX — operação atômica, sem race condition
        var isNew = redis.opsForValue().setIfAbsent(key, "processing", Duration.ofMinutes(5));
        var isDuplicate = !Boolean.TRUE.equals(isNew);
        if (isDuplicate) {
            log.debug("Duplicate event detected: notificationId={}", notificationId);
        }
        return isDuplicate;
    }

    /**
     * Estende o TTL da chave para a janela completa de deduplicação após entrega bem-sucedida.
     * Chamado apenas em caso de sucesso — falhas não estendem o TTL,
     * garantindo que retentativas legítimas não sejam bloqueadas pela chave de lock.
     */
    @Override
    public void markAsProcessed(UUID notificationId) {
        var key = KEY_PREFIX + notificationId;
        redis.opsForValue().set(key, "processed", deduplicationTtl);
        log.debug("Marked as processed: notificationId={} ttl={}", notificationId, deduplicationTtl);
    }
}
