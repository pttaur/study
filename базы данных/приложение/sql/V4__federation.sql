-- ---------------------------------------------------------------------------
-- Федеративные запросы: алиасы/тип инстансов и история запросов
-- ---------------------------------------------------------------------------

-- Короткий уникальный алиас инстанса для ссылок вида alias.schema.table
ALTER TABLE sc_instances ADD COLUMN alias TEXT;

-- Тип СУБД источника (пока используется только POSTGRES)
ALTER TABLE sc_instances ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'POSTGRES';

-- Раньше допускались только PostgreSQL-URL — ослабляем ограничение и
-- заменяем его проверкой допустимых типов СУБД
ALTER TABLE sc_instances DROP CONSTRAINT ck_sc_instances_url;
ALTER TABLE sc_instances ADD CONSTRAINT ck_sc_instances_kind
    CHECK (kind IN ('POSTGRES', 'MYSQL', 'SQLITE', 'MSSQL', 'ORACLE', 'OTHER'));

CREATE UNIQUE INDEX uq_sc_instances_alias
    ON sc_instances (alias)
    WHERE alias IS NOT NULL;

-- История федеративных запросов из SQL-консоли
CREATE TABLE sc_query_history (
    id          BIGSERIAL   PRIMARY KEY,
    sql_text    TEXT        NOT NULL,
    status      VARCHAR(20) NOT NULL,
    row_count   BIGINT,
    duration_ms BIGINT,
    error       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
