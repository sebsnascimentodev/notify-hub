package tech.cuia.notifyhub.domain.port.out;

import java.util.UUID;

public interface DeduplicationPort {

    /**
     * Retorna {@code true} se a notificação já foi processada com sucesso
     * dentro da janela de deduplicação configurada.
     */
    boolean isDuplicate(UUID notificationId);

    /**
     * Marca a notificação como processada. Chamado apenas após entrega bem-sucedida
     * para não bloquear retentativas de falhas genuínas.
     */
    void markAsProcessed(UUID notificationId);
}
