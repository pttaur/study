-- ===========================================================================
-- Нормализация ограничений/индексов + типы СУБД (MariaDB) + защита снапшотов
-- ---------------------------------------------------------------------------
-- 1. sc_instances.kind: новый набор (Postgres/MariaDB/...). ORACLE убран.
-- 2. sc_rows: CHECK на размер rows_json (<= 1 MB), предварительно обнуляем
--    слишком большие снапшоты (хэши сохраняются).
-- 3. sc_query_history: индекс по created_at DESC.
-- 4. sc_constraints/sc_indexes пересоздаются в нормализованном виде:
--    составные ключи и порядок вынесены в sc_constraint_columns/sc_index_columns.
--    Данные каталога пересобираются через POST /instances/{id}/load.
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- 1. Тип СУБД источника
-- ---------------------------------------------------------------------------
ALTER TABLE sc_instances DROP CONSTRAINT IF EXISTS ck_sc_instances_kind;
ALTER TABLE sc_instances ADD CONSTRAINT ck_sc_instances_kind
    CHECK (kind IN ('POSTGRES','MARIADB','MYSQL','SQLITE','MSSQL','OTHER'));

-- ---------------------------------------------------------------------------
-- 2. Размер снапшота строк
-- ---------------------------------------------------------------------------
UPDATE sc_rows SET rows_json = NULL
WHERE rows_json IS NOT NULL AND length(rows_json) > 1000000;

ALTER TABLE sc_rows DROP CONSTRAINT IF EXISTS ck_sc_rows_json_size;
ALTER TABLE sc_rows ADD CONSTRAINT ck_sc_rows_json_size
    CHECK (rows_json IS NULL OR length(rows_json) <= 1000000);

-- ---------------------------------------------------------------------------
-- 3. История запросов
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS ix_sc_query_history_created_at
    ON sc_query_history (created_at DESC);

-- ---------------------------------------------------------------------------
-- 4. Нормализованные ограничения и индексы
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS sc_constraint_columns CASCADE;
DROP TABLE IF EXISTS sc_index_columns CASCADE;
DROP TABLE IF EXISTS sc_constraints CASCADE;
DROP TABLE IF EXISTS sc_indexes CASCADE;

CREATE TABLE sc_constraints (
    id              BIGSERIAL   PRIMARY KEY,
    table_id        BIGINT      NOT NULL
                    REFERENCES sc_tables(id) ON DELETE CASCADE,
    constraint_name TEXT        NOT NULL,
    type            VARCHAR(20) NOT NULL,

    -- для CHECK и EXCLUDE — тело выражения
    expression      TEXT,

    -- для FOREIGN_KEY — целевая таблица
    ref_table_id    BIGINT      REFERENCES sc_tables(id) ON DELETE CASCADE,

    -- для FOREIGN_KEY — правила ссылочной целостности
    on_delete       VARCHAR(20),
    on_update       VARCHAR(20),

    CONSTRAINT uq_sc_constraints UNIQUE (table_id, constraint_name),
    CONSTRAINT ck_sc_constraints_type
        CHECK (type IN ('PRIMARY_KEY','FOREIGN_KEY','UNIQUE','CHECK','EXCLUDE')),

    CONSTRAINT ck_sc_constraints_fk
        CHECK (
            (type =  'FOREIGN_KEY' AND ref_table_id IS NOT NULL) OR
            (type <> 'FOREIGN_KEY' AND ref_table_id IS NULL)
        ),

    CONSTRAINT ck_sc_constraints_expr
        CHECK (
            (type IN ('CHECK','EXCLUDE')     AND expression IS NOT NULL) OR
            (type NOT IN ('CHECK','EXCLUDE') AND expression IS NULL)
        ),

    CONSTRAINT ck_sc_constraints_rules
        CHECK (
            (type =  'FOREIGN_KEY' AND on_delete IS NOT NULL AND on_update IS NOT NULL) OR
            (type <> 'FOREIGN_KEY' AND on_delete IS NULL     AND on_update IS NULL)
        ),

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

COMMENT ON TABLE  sc_constraints            IS 'Ограничения таблиц (PK, FK, UNIQUE, CHECK, EXCLUDE)';
COMMENT ON COLUMN sc_constraints.expression IS 'Тело CHECK/EXCLUDE (NULL для ключей)';
COMMENT ON COLUMN sc_constraints.ref_table_id IS 'Целевая таблица FK (NULL для остальных типов)';
COMMENT ON COLUMN sc_constraints.on_delete  IS 'ON DELETE для FK: CASCADE / SET_NULL / SET_DEFAULT / RESTRICT / NO_ACTION';
COMMENT ON COLUMN sc_constraints.on_update  IS 'ON UPDATE для FK: те же значения';

CREATE TABLE sc_constraint_columns (
    id              BIGSERIAL PRIMARY KEY,
    constraint_id   BIGINT NOT NULL
                    REFERENCES sc_constraints(id) ON DELETE CASCADE,
    column_name     TEXT   NOT NULL,
    position        INT    NOT NULL,
    ref_column_name TEXT,

    CONSTRAINT uq_sc_cc_col UNIQUE (constraint_id, column_name),
    CONSTRAINT uq_sc_cc_pos UNIQUE (constraint_id, position),
    CONSTRAINT ck_sc_cc_pos CHECK (position > 0)
);

CREATE INDEX ix_sc_cc_constraint ON sc_constraint_columns(constraint_id);
CREATE INDEX ix_sc_cc_column     ON sc_constraint_columns(column_name);
CREATE INDEX ix_sc_cc_ref_col    ON sc_constraint_columns(ref_column_name);

COMMENT ON TABLE  sc_constraint_columns                IS 'Колонки ограничений (состав ключа), с порядком';
COMMENT ON COLUMN sc_constraint_columns.position       IS 'Порядок колонки в ключе (1-based)';
COMMENT ON COLUMN sc_constraint_columns.ref_column_name IS 'Целевая колонка FK (NULL для не-FK)';

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
CREATE INDEX ix_sc_indexes_kind  ON sc_indexes(kind);

COMMENT ON TABLE  sc_indexes IS 'Индексы таблиц';
COMMENT ON COLUMN sc_indexes.kind IS 'Метод индекса';

CREATE TABLE sc_index_columns (
    id            BIGSERIAL PRIMARY KEY,
    index_id      BIGINT NOT NULL
                  REFERENCES sc_indexes(id) ON DELETE CASCADE,
    column_name   TEXT   NOT NULL,
    position      INT    NOT NULL,

    CONSTRAINT uq_sc_ic_col UNIQUE (index_id, column_name),
    CONSTRAINT uq_sc_ic_pos UNIQUE (index_id, position),
    CONSTRAINT ck_sc_ic_pos CHECK (position > 0)
);

CREATE INDEX ix_sc_ic_index  ON sc_index_columns(index_id);
CREATE INDEX ix_sc_ic_column ON sc_index_columns(column_name);

COMMENT ON TABLE sc_index_columns IS 'Колонки индексов (состав индекса)';
