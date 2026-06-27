package tech.cuia.notifyhub.domain.model;

import tech.cuia.notifyhub.domain.exception.InvalidNotificationStateException;

import java.time.Instant;
import java.util.*;

/**
 * Aggregate Root do domínio. Toda mutação de estado passa por métodos explícitos
 * que validam as transições permitidas — nunca via setters diretos.
 */
public final class Notification {

    private final UUID id;
    private final ChannelType channel;
    private final String recipient;
    private final Map<String, Object> payload;
    private final String idempotencyKey;

    private NotificationStatus status;
    private int attempts;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant sentAt;

    private Notification(
            UUID id, ChannelType channel, String recipient,
            Map<String, Object> payload, String idempotencyKey,
            NotificationStatus status, int attempts,
            Instant createdAt, Instant updatedAt, Instant sentAt) {
        this.id = id;
        this.channel = channel;
        this.recipient = recipient;
        this.payload = Collections.unmodifiableMap(new HashMap<>(payload));
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.attempts = attempts;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.sentAt = sentAt;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    public static Notification create(
            ChannelType channel, String recipient,
            Map<String, Object> payload, String idempotencyKey) {
        Objects.requireNonNull(channel, "channel is required");
        Objects.requireNonNull(recipient, "recipient is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        if (recipient.isBlank()) throw new IllegalArgumentException("recipient must not be blank");
        if (idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey must not be blank");

        var now = Instant.now();
        return new Notification(
                UUID.randomUUID(), channel, recipient,
                payload != null ? payload : Map.of(), idempotencyKey,
                NotificationStatus.PENDING, 0, now, now, null);
    }

    /**
     * Reconstitui uma Notification a partir de estado persistido.
     * Não aplica as invariantes de criação — o estado já foi validado quando o registro foi criado.
     */
    public static Notification reconstitute(
            UUID id, ChannelType channel, String recipient,
            Map<String, Object> payload, String idempotencyKey,
            NotificationStatus status, int attempts,
            Instant createdAt, Instant updatedAt, Instant sentAt) {
        return new Notification(
                id, channel, recipient,
                payload != null ? payload : Map.of(), idempotencyKey,
                status, attempts, createdAt, updatedAt, sentAt);
    }

    // -------------------------------------------------------------------------
    // State transitions — única forma de mutar o estado do aggregate
    // -------------------------------------------------------------------------

    public void markAsSent() {
        requireOneOf("markAsSent", NotificationStatus.PENDING, NotificationStatus.FAILED);
        var now = Instant.now();
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
        this.updatedAt = now;
    }

    public void markAsFailed() {
        requireOneOf("markAsFailed", NotificationStatus.PENDING);
        this.status = NotificationStatus.FAILED;
        this.updatedAt = Instant.now();
    }

    public void sendToDlq() {
        this.status = NotificationStatus.DLQ;
        this.updatedAt = Instant.now();
    }

    public void cancel() {
        if (!canCancel()) {
            throw new InvalidNotificationStateException(id, status, "cancel");
        }
        this.status = NotificationStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }

    public void incrementAttempts() {
        this.attempts++;
        this.updatedAt = Instant.now();
    }

    /** Zera o contador para dar uma nova janela completa de retentativas (retry manual via API). */
    public void resetAttempts() {
        this.attempts = 0;
        this.updatedAt = Instant.now();
    }

    // -------------------------------------------------------------------------
    // Business queries
    // -------------------------------------------------------------------------

    public boolean canRetry(int maxAttempts) {
        return status.isRetryable() && attempts < maxAttempts;
    }

    public boolean canCancel() {
        return status == NotificationStatus.PENDING;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void requireOneOf(String operation, NotificationStatus... allowed) {
        for (var s : allowed) {
            if (this.status == s) return;
        }
        throw new InvalidNotificationStateException(id, status, operation);
    }

    // -------------------------------------------------------------------------
    // Getters — read-only; mutações apenas via métodos de domínio
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }
    public ChannelType getChannel() { return channel; }
    public String getRecipient() { return recipient; }
    public Map<String, Object> getPayload() { return payload; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public NotificationStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Optional<Instant> getSentAt() { return Optional.ofNullable(sentAt); }

    // -------------------------------------------------------------------------
    // Identity — baseada apenas no ID (value object semântico)
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Notification other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "Notification{id=%s, channel=%s, status=%s, attempts=%d}"
                .formatted(id, channel, status, attempts);
    }
}
