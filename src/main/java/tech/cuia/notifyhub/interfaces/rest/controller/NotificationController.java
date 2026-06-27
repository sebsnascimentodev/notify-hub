package tech.cuia.notifyhub.interfaces.rest.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.cuia.notifyhub.domain.model.ChannelType;
import tech.cuia.notifyhub.domain.model.NotificationStatus;
import tech.cuia.notifyhub.domain.port.in.*;
import tech.cuia.notifyhub.domain.port.in.CancelNotificationPort.CancelResult;
import tech.cuia.notifyhub.domain.port.in.GetNotificationPort.NotificationFilter;
import tech.cuia.notifyhub.domain.port.in.RetryNotificationPort.RetryResult;
import tech.cuia.notifyhub.domain.port.in.SendNotificationPort.SendResult;
import tech.cuia.notifyhub.interfaces.rest.dto.request.SendNotificationRequest;
import tech.cuia.notifyhub.interfaces.rest.dto.response.ErrorResponse;
import tech.cuia.notifyhub.interfaces.rest.dto.response.NotificationResponse;
import tech.cuia.notifyhub.interfaces.rest.dto.response.PageResponse;
import tech.cuia.notifyhub.interfaces.rest.mapper.NotificationMapper;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Ciclo de vida de notificações assíncronas")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final SendNotificationPort sendPort;
    private final GetNotificationPort getPort;
    private final RetryNotificationPort retryPort;
    private final CancelNotificationPort cancelPort;
    private final NotificationMapper mapper;

    public NotificationController(
            SendNotificationPort sendPort,
            GetNotificationPort getPort,
            RetryNotificationPort retryPort,
            CancelNotificationPort cancelPort,
            NotificationMapper mapper) {
        this.sendPort = sendPort;
        this.getPort = getPort;
        this.retryPort = retryPort;
        this.cancelPort = cancelPort;
        this.mapper = mapper;
    }

    // -------------------------------------------------------------------------

    @PostMapping
    @Operation(
            summary = "Agendar notificação",
            description = "Aceita a notificação para entrega assíncrona. " +
                          "Idempotente: o mesmo `idempotencyKey` retorna 200 com a notificação existente."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Notificação aceita para entrega",
                    headers = @Header(name = HttpHeaders.LOCATION,
                            description = "URI da notificação criada",
                            schema = @Schema(type = "string"))),
            @ApiResponse(responseCode = "200", description = "Notificação já existe (idempotente)",
                    content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Payload inválido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<NotificationResponse> send(
            @Valid @RequestBody SendNotificationRequest request) {

        var result = sendPort.send(mapper.toCommand(request));

        return switch (result) {
            case SendResult.Accepted a -> {
                var notification = getPort.findById(a.notificationId()).orElseThrow();
                yield ResponseEntity.accepted()
                        .header(HttpHeaders.LOCATION, "/api/v1/notifications/" + a.notificationId())
                        .body(mapper.toResponse(notification));
            }
            case SendResult.Duplicate d -> {
                var notification = getPort.findById(d.existingId()).orElseThrow();
                yield ResponseEntity.ok(mapper.toResponse(notification));
            }
        };
    }

    // -------------------------------------------------------------------------

    @GetMapping("/{id}")
    @Operation(
            summary = "Consultar status da notificação",
            description = "Retorna o estado atual e histórico de tentativas de uma notificação."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificação encontrada"),
            @ApiResponse(responseCode = "404", description = "Notificação não encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<NotificationResponse> findById(
            @Parameter(description = "ID da notificação") @PathVariable UUID id) {

        return getPort.findById(id)
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------------

    @GetMapping
    @Operation(
            summary = "Listar notificações",
            description = "Lista notificações com filtros opcionais. Suporta paginação e ordenação."
    )
    @ApiResponse(responseCode = "200", description = "Lista paginada de notificações")
    public ResponseEntity<PageResponse<NotificationResponse>> findAll(
            @Parameter(description = "Filtrar por status")
            @RequestParam(required = false) NotificationStatus status,

            @Parameter(description = "Filtrar por canal")
            @RequestParam(required = false) ChannelType channel,

            @Parameter(description = "Início do período (ISO-8601)", example = "2026-01-01T00:00:00Z")
            @RequestParam(required = false) Instant from,

            @Parameter(description = "Fim do período (ISO-8601)", example = "2026-12-31T23:59:59Z")
            @RequestParam(required = false) Instant to,

            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        var filter = new NotificationFilter(status, channel, from, to);
        var page = getPort.findAll(filter, pageable);
        return ResponseEntity.ok(mapper.toPageResponse(page));
    }

    // -------------------------------------------------------------------------

    @PostMapping("/{id}/retry")
    @Operation(
            summary = "Reprocessar da DLQ",
            description = "Recoloca uma notificação FAILED ou DLQ na fila de entrega com contador zerado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Notificação enfileirada para nova tentativa"),
            @ApiResponse(responseCode = "404", description = "Notificação não encontrada"),
            @ApiResponse(responseCode = "422", description = "Status não permite retry (ex: SENT, CANCELLED)")
    })
    public ResponseEntity<Void> retry(
            @Parameter(description = "ID da notificação") @PathVariable UUID id) {

        return switch (retryPort.retry(id)) {
            case RetryResult.Queued q       -> ResponseEntity.accepted().build();
            case RetryResult.NotFound n     -> ResponseEntity.notFound().build();
            case RetryResult.NotRetryable r -> ResponseEntity.unprocessableEntity().build();
        };
    }

    // -------------------------------------------------------------------------

    @PostMapping("/{id}/cancel")
    @Operation(
            summary = "Cancelar notificação",
            description = "Cancela uma notificação PENDING antes que seja processada pelo consumer."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Notificação cancelada"),
            @ApiResponse(responseCode = "404", description = "Notificação não encontrada"),
            @ApiResponse(responseCode = "422", description = "Status não permite cancelamento (ex: já SENT)")
    })
    public ResponseEntity<Void> cancel(
            @Parameter(description = "ID da notificação") @PathVariable UUID id) {

        return switch (cancelPort.cancel(id)) {
            case CancelResult.Cancelled c      -> ResponseEntity.noContent().build();
            case CancelResult.NotFound n       -> ResponseEntity.notFound().build();
            case CancelResult.NotCancellable r -> ResponseEntity.unprocessableEntity().build();
        };
    }
}
