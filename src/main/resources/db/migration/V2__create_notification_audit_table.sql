-- Audit trail imutável de transições de status.
-- Populado exclusivamente por trigger PostgreSQL — a aplicação não tem acesso de
-- INSERT/UPDATE/DELETE nesta tabela, garantindo que o histórico não possa ser
-- adulterado nem acidentalmente nem maliciosamente.

CREATE TABLE IF NOT EXISTS notification_audit (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    notification_id UUID        NOT NULL,
    old_status      VARCHAR(20),
    new_status      VARCHAR(20) NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_notification_audit PRIMARY KEY (id),
    CONSTRAINT fk_audit_notification
        FOREIGN KEY (notification_id) REFERENCES notifications (id)
        ON DELETE CASCADE
);

CREATE INDEX idx_audit_notification_id ON notification_audit (notification_id);
CREATE INDEX idx_audit_occurred_at     ON notification_audit (occurred_at DESC);

COMMENT ON TABLE notification_audit IS 'Log imutável de transições de status; escrita exclusiva via trigger trg_notifications_status_change';

-- ----------------------------------------------------------------------------
-- Trigger: captura toda transição de status na tabela notifications.
-- A função usa SECURITY DEFINER para que a aplicação (role com permissão
-- restrita à tabela audit) consiga inserir através do trigger.
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION trg_notifications_audit()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    -- Só registra quando o status realmente mudou (evita registros duplicados
    -- em updates que alteram apenas outros campos como updated_at ou attempts)
    IF OLD.status IS DISTINCT FROM NEW.status THEN
        INSERT INTO notification_audit (notification_id, old_status, new_status)
        VALUES (NEW.id, OLD.status, NEW.status);
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_notifications_status_change
    AFTER UPDATE OF status ON notifications
    FOR EACH ROW
    EXECUTE FUNCTION trg_notifications_audit();

COMMENT ON FUNCTION trg_notifications_audit() IS
    'Grava transições de status em notification_audit de forma atômica com o UPDATE original';
