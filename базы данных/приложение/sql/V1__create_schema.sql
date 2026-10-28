-- ===========================================================================
-- SCHEMA CATALOG — хранилище снапшотов схем PostgreSQL
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Инстансы
-- ---------------------------------------------------------------------------
CREATE TABLE sc_instances (
    id             BIGSERIAL   PRIMARY KEY,
    label          TEXT        NOT NULL,
    connection_url TEXT        NOT NULL,
    username       TEXT        NOT NULL,
    password       TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_sc_instances_label UNIQUE (label),
    CONSTRAINT uq_sc_instances_url   UNIQUE (connection_url),
    CONSTRAINT ck_sc_instances_url
        CHECK (connection_url ~ '^jdbc:postgresql://')
);

-- ---------------------------------------------------------------------------
-- Схемы
-- ---------------------------------------------------------------------------
CREATE TABLE sc_schemas (
    id          BIGSERIAL PRIMARY KEY,
    instance_id BIGINT    NOT NULL
                REFERENCES sc_instances(id) ON DELETE CASCADE,
    schema_name TEXT      NOT NULL,
    owner       TEXT,
    comment     TEXT,
    CONSTRAINT uq_sc_schemas UNIQUE (instance_id, schema_name)
);

CREATE INDEX ix_sc_schemas_instance ON sc_schemas(instance_id);

-- ---------------------------------------------------------------------------
-- Таблицы / вьюхи / матвьюхи / foreign tables
-- ---------------------------------------------------------------------------
CREATE TABLE sc_tables (
    id         BIGSERIAL   PRIMARY KEY,
    schema_id  BIGINT      NOT NULL
               REFERENCES sc_schemas(id) ON DELETE CASCADE,
    table_name TEXT        NOT NULL,
    kind       VARCHAR(30) NOT NULL DEFAULT 'TABLE',
    CONSTRAINT uq_sc_tables UNIQUE (schema_id, table_name),
    CONSTRAINT ck_sc_tables_kind
        CHECK (kind IN ('TABLE',
                        'PARTITIONED_TABLE',
                        'VIEW',
                        'MATERIALIZED_VIEW',
                        'FOREIGN_TABLE'))
);

CREATE INDEX ix_sc_tables_schema ON sc_tables(schema_id);

-- ---------------------------------------------------------------------------
-- Колонки
-- ---------------------------------------------------------------------------
CREATE TABLE sc_columns (
    id               BIGSERIAL PRIMARY KEY,
    table_id         BIGINT    NOT NULL
                     REFERENCES sc_tables(id) ON DELETE CASCADE,
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
-- Ограничения
-- ---------------------------------------------------------------------------
CREATE TABLE sc_constraints (
    id              BIGSERIAL   PRIMARY KEY,
    table_id        BIGINT      NOT NULL
                    REFERENCES sc_tables(id) ON DELETE CASCADE,
    constraint_name TEXT        NOT NULL,
    type            VARCHAR(20) NOT NULL,
    definition      TEXT,
    CONSTRAINT uq_sc_constraints UNIQUE (table_id, constraint_name),
    CONSTRAINT ck_sc_constraints_type
        CHECK (type IN ('PRIMARY_KEY',
                        'FOREIGN_KEY',
                        'UNIQUE',
                        'CHECK',
                        'EXCLUDE'))
);

CREATE INDEX ix_sc_constraints_table ON sc_constraints(table_id);

-- ---------------------------------------------------------------------------
-- Индексы
-- ---------------------------------------------------------------------------
CREATE TABLE sc_indexes (
    id         BIGSERIAL   PRIMARY KEY,
    table_id   BIGINT      NOT NULL
               REFERENCES sc_tables(id) ON DELETE CASCADE,
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