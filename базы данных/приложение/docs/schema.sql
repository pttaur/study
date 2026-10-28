-- ===========================================================================
-- Schema Catalog Aggregator — актуальная схема БД хранилища (итог V1..V8)
-- Источник истины: sql/V1__create_schema.sql .. sql/V6__normalize_catalog.sql.
-- Этот файл — сводный DDL текущего состояния (для справки).
-- ===========================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- sc_instances — источники СУБД
-- ---------------------------------------------------------------------------
CREATE TABLE sc_instances (
    id                 BIGSERIAL    PRIMARY KEY,
    label              TEXT         NOT NULL,
    connection_url     VARCHAR(255) NOT NULL,
    username           TEXT         NOT NULL,
    password_encrypted BYTEA        NOT NULL,   -- pgp_sym_encrypt(password, key)
    kind               VARCHAR(20)  NOT NULL DEFAULT 'POSTGRES',
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_sc_instances_label UNIQUE (label),
    CONSTRAINT uq_sc_instances_url   UNIQUE (connection_url),
    CONSTRAINT ck_sc_instances_url_len CHECK (length(connection_url) <= 255),
    CONSTRAINT ck_sc_instances_kind
        CHECK (kind IN ('POSTGRES','MARIADB','MYSQL','SQLITE','MSSQL','OTHER'))
);

COMMENT ON COLUMN sc_instances.password_encrypted IS
    'Пароль зашифрован через pgp_sym_encrypt; ключ — SCHEMA_ENCRYPT_KEY.';

-- ---------------------------------------------------------------------------
-- sc_schemas — схемы (namespace)
-- ---------------------------------------------------------------------------
CREATE TABLE sc_schemas (
    id          BIGSERIAL PRIMARY KEY,
    instance_id BIGINT    NOT NULL REFERENCES sc_instances(id) ON DELETE CASCADE,
    schema_name TEXT      NOT NULL,
    owner       TEXT,
    comment     TEXT,
    CONSTRAINT uq_sc_schemas UNIQUE (instance_id, schema_name)
);
CREATE INDEX ix_sc_schemas_instance ON sc_schemas(instance_id);

-- ---------------------------------------------------------------------------
-- sc_tables — таблицы / вьюхи / матвьюхи / foreign tables
-- ---------------------------------------------------------------------------
CREATE TABLE sc_tables (
    id         BIGSERIAL   PRIMARY KEY,
    schema_id  BIGINT      NOT NULL REFERENCES sc_schemas(id) ON DELETE CASCADE,
    table_name TEXT        NOT NULL,
    kind       VARCHAR(30) NOT NULL DEFAULT 'TABLE',
    CONSTRAINT uq_sc_tables UNIQUE (schema_id, table_name),
    CONSTRAINT ck_sc_tables_kind
        CHECK (kind IN ('TABLE','PARTITIONED_TABLE','VIEW',
                        'MATERIALIZED_VIEW','FOREIGN_TABLE'))
);
CREATE INDEX ix_sc_tables_schema ON sc_tables(schema_id);

-- ---------------------------------------------------------------------------
-- sc_columns — колонки
-- ---------------------------------------------------------------------------
CREATE TABLE sc_columns (
    id               BIGSERIAL PRIMARY KEY,
    table_id         BIGINT    NOT NULL REFERENCES sc_tables(id) ON DELETE CASCADE,
    column_name      TEXT      NOT NULL,
    ordinal_position INT       NOT NULL,
    data_type        TEXT      NOT NULL,
    is_nullable      BOOLEAN   NOT NULL,
    default_value    TEXT,
    is_generated     BOOLEAN   NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_sc_columns     UNIQUE (table_id, column_name),
    CONSTRAINT uq_sc_columns_pos UNIQUE (table_id, ordinal_position),
    CONSTRAINT ck_sc_columns_pos CHECK (ordinal_position > 0)
);
CREATE INDEX ix_sc_columns_table ON sc_columns(table_id);

-- ---------------------------------------------------------------------------
-- sc_constraints — ограничения (нормализованные, V6)
-- ---------------------------------------------------------------------------
CREATE TABLE sc_constraints (
    id              BIGSERIAL   PRIMARY KEY,
    table_id        BIGINT      NOT NULL REFERENCES sc_tables(id) ON DELETE CASCADE,
    constraint_name TEXT        NOT NULL,
    type            VARCHAR(20) NOT NULL,
    expression      TEXT,                      -- CHECK/EXCLUDE, иначе NULL
    ref_table_id    BIGINT      REFERENCES sc_tables(id) ON DELETE CASCADE,  -- FK
    on_delete       VARCHAR(20),
    on_update       VARCHAR(20),

    CONSTRAINT uq_sc_constraints UNIQUE (table_id, constraint_name),
    CONSTRAINT ck_sc_constraints_type
        CHECK (type IN ('PRIMARY_KEY','FOREIGN_KEY','UNIQUE','CHECK','EXCLUDE')),
    CONSTRAINT ck_sc_constraints_fk
        CHECK ((type = 'FOREIGN_KEY' AND ref_table_id IS NOT NULL) OR
               (type <> 'FOREIGN_KEY' AND ref_table_id IS NULL)),
    CONSTRAINT ck_sc_constraints_expr
        CHECK ((type IN ('CHECK','EXCLUDE')     AND expression IS NOT NULL) OR
               (type NOT IN ('CHECK','EXCLUDE') AND expression IS NULL)),
    CONSTRAINT ck_sc_constraints_rules
        CHECK ((type = 'FOREIGN_KEY' AND on_delete IS NOT NULL AND on_update IS NOT NULL) OR
               (type <> 'FOREIGN_KEY' AND on_delete IS NULL     AND on_update IS NULL)),
    CONSTRAINT ck_sc_constraints_on_delete
        CHECK (on_delete IS NULL OR
               on_delete IN ('CASCADE','SET_NULL','SET_DEFAULT','RESTRICT','NO_ACTION')),
    CONSTRAINT ck_sc_constraints_on_update
        CHECK (on_update IS NULL OR
               on_update IN ('CASCADE','SET_NULL','SET_DEFAULT','RESTRICT','NO_ACTION'))
);
CREATE INDEX ix_sc_constraints_table ON sc_constraints(table_id);
CREATE INDEX ix_sc_constraints_type  ON sc_constraints(type);
CREATE INDEX ix_sc_constraints_ref   ON sc_constraints(ref_table_id);

-- ---------------------------------------------------------------------------
-- sc_constraint_columns — состав ключа с порядком (V6)
-- ---------------------------------------------------------------------------
CREATE TABLE sc_constraint_columns (
    id              BIGSERIAL PRIMARY KEY,
    constraint_id   BIGINT NOT NULL REFERENCES sc_constraints(id) ON DELETE CASCADE,
    column_name     TEXT   NOT NULL,
    position        INT    NOT NULL,
    ref_column_name TEXT,                      -- для FK — колонка в целевой таблице
    CONSTRAINT uq_sc_cc_col UNIQUE (constraint_id, column_name),
    CONSTRAINT uq_sc_cc_pos UNIQUE (constraint_id, position),
    CONSTRAINT ck_sc_cc_pos CHECK (position > 0)
);
CREATE INDEX ix_sc_cc_constraint ON sc_constraint_columns(constraint_id);
CREATE INDEX ix_sc_cc_column     ON sc_constraint_columns(column_name);
CREATE INDEX ix_sc_cc_ref_col    ON sc_constraint_columns(ref_column_name);

-- ---------------------------------------------------------------------------
-- sc_indexes — индексы
-- ---------------------------------------------------------------------------
CREATE TABLE sc_indexes (
    id         BIGSERIAL   PRIMARY KEY,
    table_id   BIGINT      NOT NULL REFERENCES sc_tables(id) ON DELETE CASCADE,
    index_name TEXT        NOT NULL,
    is_unique  BOOLEAN     NOT NULL DEFAULT FALSE,
    is_primary BOOLEAN     NOT NULL DEFAULT FALSE,
    kind       VARCHAR(10) NOT NULL DEFAULT 'BTREE',
    definition TEXT,
    CONSTRAINT uq_sc_indexes UNIQUE (table_id, index_name),
    CONSTRAINT ck_sc_indexes_kind
        CHECK (kind IN ('BTREE','HASH','GIN','GIST','SPGIST','BRIN'))
);
CREATE INDEX ix_sc_indexes_table ON sc_indexes(table_id);
CREATE INDEX ix_sc_indexes_kind  ON sc_indexes(kind);

-- ---------------------------------------------------------------------------
-- sc_index_columns — состав индекса с порядком (V6)
-- ---------------------------------------------------------------------------
CREATE TABLE sc_index_columns (
    id          BIGSERIAL PRIMARY KEY,
    index_id    BIGINT NOT NULL REFERENCES sc_indexes(id) ON DELETE CASCADE,
    column_name TEXT   NOT NULL,
    position    INT    NOT NULL,
    CONSTRAINT uq_sc_ic_col UNIQUE (index_id, column_name),
    CONSTRAINT uq_sc_ic_pos UNIQUE (index_id, position),
    CONSTRAINT ck_sc_ic_pos CHECK (position > 0)
);
CREATE INDEX ix_sc_ic_index  ON sc_index_columns(index_id);
CREATE INDEX ix_sc_ic_column ON sc_index_columns(column_name);

-- ---------------------------------------------------------------------------
-- sc_query_history — журнал федеративных запросов
-- ---------------------------------------------------------------------------
CREATE TABLE sc_query_history (
    id          BIGSERIAL   PRIMARY KEY,
    sql_text    TEXT        NOT NULL,
    status      VARCHAR(20) NOT NULL,
    row_count   BIGINT,
    duration_ms BIGINT,
    error       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_sc_query_history_status
        CHECK (status IN ('OK','ERROR','TIMEOUT','CANCELLED'))
);
CREATE INDEX ix_sc_query_history_created_at ON sc_query_history(created_at DESC);

-- ---------------------------------------------------------------------------
-- Триггер автообновления updated_at
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER sc_instances_set_updated_at
    BEFORE UPDATE ON sc_instances
    FOR EACH ROW EXECUTE FUNCTION trg_set_updated_at();
