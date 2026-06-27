package tech.cuia.notifyhub.interfaces.rest.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import tech.cuia.notifyhub.domain.model.Notification;
import tech.cuia.notifyhub.domain.port.in.SendNotificationPort.SendCommand;
import tech.cuia.notifyhub.interfaces.rest.dto.request.SendNotificationRequest;
import tech.cuia.notifyhub.interfaces.rest.dto.response.NotificationResponse;
import tech.cuia.notifyhub.interfaces.rest.dto.response.PageResponse;

import java.util.Map;

@Component
public class NotificationMapper {

    public SendCommand toCommand(SendNotificationRequest req) {
        return new SendCommand(
                req.channel(),
                req.recipient(),
                req.payload() != null ? req.payload() : Map.of(),
                req.idempotencyKey());
    }

    public NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getChannel(),
                n.getRecipient(),
                n.getStatus(),
                n.getAttempts(),
                n.getCreatedAt(),
                n.getUpdatedAt(),
                n.getSentAt().orElse(null));
    }

    public PageResponse<NotificationResponse> toPageResponse(Page<Notification> page) {
        return new PageResponse<>(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
