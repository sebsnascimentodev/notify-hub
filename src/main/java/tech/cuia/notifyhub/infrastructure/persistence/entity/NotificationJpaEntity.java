package tech.cuia.notifyhub.infrastructure.persistence.entity;

import jakarta.persistence.*;
import tech.cuia.notifyhub.domain.model.ChannelType;
import tech.cuia.notifyhub.domain.model.NotificationStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_status",   columnList = "status"),
                @Index(name = "idx_notifications_channel",  columnList = "channel"),
                @Index(name = "idx_notifications_created",  columnList = "created_at DESC"),
                @Index(name = "idx_notifications_idem_key", columnList = "idempotency_key", unique = true)
        }
)
public class NotificationJpaEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChannelType channel;

    @Column(nullable = false, length = 500)
    private String recipient;

    // TEXT + converter: sem dependência de hypersistence-utils.
    // Para queries no payload em produção, migrar para JSONB + @Type(JsonBinaryType.class).
    @Convert(converter = MapToJsonConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    private Map<String, Object> payload;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    // Locking otimista: previne que dois consumers atualizem a mesma linha concorrentemente.
    // Ex: consumer Kafka + cancelamento via API ao mesmo tempo.
    @Version
    private Long version;

    // -------------------------------------------------------------------------
    // Getters / Setters — JPA exige; domínio não os usa diretamente
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public ChannelType getChannel() { return channel; }
    public void setChannel(ChannelType channel) { this.channel = channel; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
