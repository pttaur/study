-- ===========================================================================
-- Снапшоты строк (эталон содержимого таблиц) для контроля drift по данным
-- ---------------------------------------------------------------------------
-- sc_rows хранит эталон содержимого таблицы: ограниченный снапшот строк
-- (rows_json), их число (row_count) и хэши (row_hash — строк, rec_hash —
-- «эталон + число строк»). Сравнение "эталон vs живая таблица" идёт по хэшам
-- и числу строк. Каждая новая регистрация для пары (instance_id, table_id)
-- перезаписывает предыдущую.
-- ===========================================================================

CREATE TABLE sc_rows (
    id          BIGSERIAL   PRIMARY KEY,
    instance_id BIGINT      NOT NULL
                REFERENCES sc_instances(id) ON DELETE CASCADE,
    table_id    BIGINT      NOT NULL
                REFERENCES sc_tables(id) ON DELETE CASCADE,
    row_hash    TEXT        NOT NULL,
    rec_hash    TEXT        NOT NULL,
    row_count   BIGINT      NOT NULL,
    rows_json   TEXT,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_sc_rows UNIQUE (instance_id, table_id)
);

CREATE INDEX ix_sc_rows_instance ON sc_rows(instance_id);
CREATE INDEX ix_sc_rows_table   ON sc_rows(table_id);

COMMENT ON TABLE sc_rows IS
    'Эталон содержимого таблицы (снапшот строк) для контроля drift по данным.'
