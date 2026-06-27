-- Tabela principal de notificações
-- O campo `version` habilita locking otimista (Hibernate @Version):
-- previne que consumer Kafka e cancelamento via API sobrescrevam-se mutuamente.

CREATE TABLE IF NOT EXISTS notifications (
    id              UUID            NOT NULL,
    channel         VARCHAR(20)     NOT NULL,
    recipient       VARCHAR(500)    NOT NULL,
    payload         TEXT            NOT NULL DEFAULT '{}',
    idempotency_key VARCHAR(255)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    attempts        INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMPTZ,
    version         BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT uq_notifications_idempotency_key UNIQUE (idempotency_key),

    CONSTRAINT chk_notifications_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DLQ', 'CANCELLED')),
    CONSTRAINT chk_notifications_channel
        CHECK (channel IN ('EMAIL', 'WEBHOOK')),
    CONSTRAINT chk_notifications_attempts
        CHECK (attempts >= 0),
    CONSTRAINT chk_notifications_payload_json
        CHECK (payload IS NULL OR payload::json IS NOT NULL)
);

-- Índices para os filtros mais comuns da API de listagem
CREATE INDEX idx_notifications_status   ON notifications (status);
CREATE INDEX idx_notifications_channel  ON notifications (channel);
CREATE INDEX idx_notifications_created  ON notifications (created_at DESC);

COMMENT ON TABLE  notifications IS 'Aggregate root de notificações — toda mutação passa pelo domínio Java';
COMMENT ON COLUMN notifications.idempotency_key IS 'Chave fornecida pelo produtor; garante at-most-once semantics na criação';
COMMENT ON COLUMN notifications.version IS 'Controle de versão para locking otimista (Hibernate @Version)';
COMMENT ON COLUMN notifications.payload IS 'JSON com campos específicos do canal: {subject, body} para EMAIL; payload livre para WEBHOOK';
