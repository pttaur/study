-- ===========================================================================
-- Целостность истории запросов и автообновление updated_at
-- ---------------------------------------------------------------------------
-- 1. CHECK на статус федеративного запроса (OK/ERROR/TIMEOUT/CANCELLED).
-- 2. Триггер, проставляющий sc_instances.updated_at при UPDATE.
-- ===========================================================================

ALTER TABLE sc_query_history
    DROP CONSTRAINT IF EXISTS ck_sc_query_history_status;
ALTER TABLE sc_query_history
    ADD CONSTRAINT ck_sc_query_history_status
    CHECK (status IN ('OK', 'ERROR', 'TIMEOUT', 'CANCELLED'));

CREATE OR REPLACE FUNCTION trg_set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS sc_instances_set_updated_at ON sc_instances;
CREATE TRIGGER sc_instances_set_updated_at
    BEFORE UPDATE ON sc_instances
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();

COMMENT ON CONSTRAINT ck_sc_query_history_status ON sc_query_history IS
    'Допустимые статусы федеративного запроса.';
