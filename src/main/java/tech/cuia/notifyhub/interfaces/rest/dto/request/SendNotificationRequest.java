package tech.cuia.notifyhub.interfaces.rest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tech.cuia.notifyhub.domain.model.ChannelType;

import java.util.Map;

@Schema(description = "Requisição para agendamento de notificação")
public record SendNotificationRequest(

        @NotNull(message = "channel é obrigatório")
        @Schema(description = "Canal de entrega", example = "EMAIL")
        ChannelType channel,

        @NotBlank(message = "recipient é obrigatório")
        @Size(max = 500, message = "recipient não pode exceder 500 caracteres")
        @Schema(description = "Destinatário: endereço de e-mail (EMAIL) ou URL (WEBHOOK)",
                example = "usuario@exemplo.com")
        String recipient,

        @Schema(description = "Dados do canal: {subject, body, html?} para EMAIL; payload livre para WEBHOOK",
                example = "{\"subject\": \"Bem-vindo\", \"body\": \"Sua conta foi criada.\"}")
        Map<String, Object> payload,

        @NotBlank(message = "idempotencyKey é obrigatório")
        @Size(max = 255, message = "idempotencyKey não pode exceder 255 caracteres")
        @Schema(description = "Chave única do produtor para garantir idempotência",
                example = "signup-user-42")
        String idempotencyKey
) {}
