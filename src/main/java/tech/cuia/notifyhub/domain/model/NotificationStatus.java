package tech.cuia.notifyhub.domain.model;

public enum NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    DLQ,
    CANCELLED;

    public boolean isTerminal() {
        return this == SENT || this == DLQ || this == CANCELLED;
    }

    public boolean isRetryable() {
        return this == FAILED || this == DLQ;
    }
}
