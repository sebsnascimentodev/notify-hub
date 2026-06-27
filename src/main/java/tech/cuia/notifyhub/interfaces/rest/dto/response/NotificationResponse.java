package tech.cuia.notifyhub.interfaces.rest.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import tech.cuia.notifyhub.domain.model.ChannelType;
import tech.cuia.notifyhub.domain.model.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Estado atual de uma notificação")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(

        @Schema(description = "Identificador único da notificação")
        UUID id,

        @Schema(description = "Canal de entrega utilizado")
        ChannelType channel,

        @Schema(description = "Destinatário da notificação")
        String recipient,

        @Schema(description = "Status atual no ciclo de vida")
        NotificationStatus status,

        @Schema(description = "Número de tentativas de entrega realizadas")
        int attempts,

        @Schema(description = "Momento em que a notificação foi criada")
        Instant createdAt,

        @Schema(description = "Momento da última atualização de status")
        Instant updatedAt,

        @Schema(description = "Momento em que a entrega foi confirmada; ausente enquanto não entregue")
        Instant sentAt
) {}
