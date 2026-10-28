package com.example.dbagg.util;

/**
 * Центральное хранилище всех SQL-запросов сервиса.
 *
 * <p>Запросы к целевой БД идут напрямую в pg_catalog (без information_schema). Запросы к
 * собственному хранилищу работают с таблицами sc_*. Все строки — чистый SQL, выполняется через JOOQ
 * как тонкую обёртку над JDBC (PreparedStatement-параметры только через {@code ?}).
 */
public final class SqlQueries {
  private SqlQueries() {}

  // -------------------------------------------------------------------------
  // Целевая БД (information_schema — первичный источник; pg_catalog только там,
  // где information_schema физически не содержит данные: индексы, определения
  // ограничений, точный relkind таблиц)
  // -------------------------------------------------------------------------

  public static final String FIND_SCHEMAS =
      """
      SELECT schema_name, schema_owner AS owner
      FROM information_schema.schemata
      WHERE schema_name NOT IN ('pg_catalog', 'information_schema')
        AND schema_name NOT LIKE 'pg\\_temp\\_%'
        AND schema_name NOT LIKE 'pg\\_toast\\_%'
      ORDER BY schema_name
      """;

  public static final String FIND_TABLES_WITH_SCHEMA =
      """
      SELECT
        t.table_schema AS schema_name,
        t.table_name AS table_name,
        COALESCE(c.relkind::text, 'r') AS kind
      FROM information_schema.tables t
      LEFT JOIN pg_catalog.pg_class c
             ON c.relname = t.table_name
            AND c.relnamespace = (
              SELECT oid FROM pg_catalog.pg_namespace WHERE nspname = t.table_schema
            )
            AND c.relkind IN ('r', 'p', 'v', 'm', 'f')
      WHERE t.table_schema NOT IN ('pg_catalog', 'information_schema')
        AND t.table_schema NOT LIKE 'pg\\_temp\\_%'
        AND t.table_schema NOT LIKE 'pg\\_toast\\_%'
      ORDER BY t.table_schema, t.table_name
      """;

  public static final String FIND_COLUMNS_ALL =
      """
      SELECT
        c.table_schema AS schema_name,
        c.table_name AS table_name,
        c.ordinal_position,
        c.column_name,
        c.data_type,
        (c.is_nullable = 'YES') AS nullable,
        c.column_default AS default_value,
        (c.is_generated = 'ALWAYS') AS generated
      FROM information_schema.columns c
      WHERE c.table_schema NOT IN ('pg_catalog', 'information_schema')
        AND c.table_schema NOT LIKE 'pg\\_temp\\_%'
        AND c.table_schema NOT LIKE 'pg\\_toast\\_%'
      ORDER BY c.table_schema, c.table_name, c.ordinal_position
      """;

  public static final String FIND_CONSTRAINTS_ALL =
      """
      SELECT
        n.nspname AS schema_name,
        c.relname AS table_name,
        con.conname AS constraint_name,
        con.contype::text AS type,
        pg_catalog.pg_get_constraintdef(con.oid, true) AS definition,
        rn.nspname AS ref_schema_name,
        rc.relname AS ref_table_name,
        con.confdeltype::text AS on_delete,
        con.confupdtype::text AS on_update,
        con.conkey::text AS key_attrs,
        con.confkey::text AS ref_attrs
      FROM pg_catalog.pg_constraint con
      JOIN pg_catalog.pg_class c ON c.oid = con.conrelid
      JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
      LEFT JOIN pg_catalog.pg_class rc ON rc.oid = con.confrelid
      LEFT JOIN pg_catalog.pg_namespace rn ON rn.oid = rc.relnamespace
      WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
        AND n.nspname NOT LIKE 'pg\\_temp\\_%'
        AND n.nspname NOT LIKE 'pg\\_toast\\_%'
      ORDER BY n.nspname, c.relname, con.conname
      """;

  public static final String FIND_ATTRIBUTES_ALL =
      """
      SELECT
        n.nspname AS schema_name,
        c.relname AS table_name,
        a.attnum AS attnum,
        a.attname AS column_name
      FROM pg_catalog.pg_attribute a
      JOIN pg_catalog.pg_class c ON c.oid = a.attrelid
      JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
      WHERE a.attnum > 0 AND NOT a.attisdropped
        AND n.nspname NOT IN ('pg_catalog', 'information_schema')
        AND n.nspname NOT LIKE 'pg\\_temp\\_%'
        AND n.nspname NOT LIKE 'pg\\_toast\\_%'
      """;

  public static final String FIND_INDEXES_ALL =
      """
      SELECT
        n.nspname AS schema_name,
        t.relname AS table_name,
        i.relname AS index_name,
        ix.indisunique AS is_unique,
        ix.indisprimary AS is_primary,
        am.amname AS kind,
        pg_catalog.pg_get_indexdef(ix.indexrelid, 0, true) AS definition,
        ix.indkey::text AS key_attrs,
        ix.indnkeyatts AS key_count
      FROM pg_catalog.pg_index ix
      JOIN pg_catalog.pg_class i ON i.oid = ix.indexrelid
      JOIN pg_catalog.pg_class t ON t.oid = ix.indrelid
      JOIN pg_catalog.pg_namespace n ON n.oid = t.relnamespace
      JOIN pg_catalog.pg_am am ON am.oid = i.relam
      WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
        AND n.nspname NOT LIKE 'pg\\_temp\\_%'
        AND n.nspname NOT LIKE 'pg\\_toast\\_%'
        AND ix.indisvalid
      ORDER BY n.nspname, t.relname, i.relname
      """;

  // -------------------------------------------------------------------------
  // Хранилище (sc_*)
  // -------------------------------------------------------------------------

  public static final String INSERT_INSTANCE =
      """
      INSERT INTO sc_instances (label, connection_url, username, kind, password_encrypted)
      VALUES (?, ?, ?, ?, pgp_sym_encrypt(?, ?))
      RETURNING id
      """;

  public static final String UPDATE_INSTANCE_PASSWORD =
      """
      UPDATE sc_instances
      SET password_encrypted = pgp_sym_encrypt(?, ?),
          updated_at = now()
      WHERE id = ?
      """;

  public static final String FIND_INSTANCES =
      """
      SELECT id, label, connection_url, username, kind
      FROM sc_instances
      ORDER BY id
      """;

  public static final String FIND_INSTANCE_BY_ID =
      """
      SELECT id, label, connection_url, username, kind
      FROM sc_instances
      WHERE id = ?
      """;

  public static final String FIND_INSTANCE_BY_LABEL =
      """
      SELECT id, label, connection_url, username, kind
      FROM sc_instances
      WHERE label = ?
      """;

  public static final String DECRYPT_PASSWORD =
      """
      SELECT pgp_sym_decrypt(password_encrypted, ?)::text AS password
      FROM sc_instances
      WHERE id = ?
      """;

  public static final String DELETE_INDEXES_FOR_INSTANCE =
      """
      DELETE FROM sc_indexes
      WHERE table_id IN (
        SELECT t.id FROM sc_tables t
        JOIN sc_schemas s ON s.id = t.schema_id
        WHERE s.instance_id = ?
      )
      """;

  public static final String DELETE_CONSTRAINTS_FOR_INSTANCE =
      """
      DELETE FROM sc_constraints
      WHERE table_id IN (
        SELECT t.id FROM sc_tables t
        JOIN sc_schemas s ON s.id = t.schema_id
        WHERE s.instance_id = ?
      )
      """;

  public static final String DELETE_COLUMNS_FOR_INSTANCE =
      """
      DELETE FROM sc_columns
      WHERE table_id IN (
        SELECT t.id FROM sc_tables t
        JOIN sc_schemas s ON s.id = t.schema_id
        WHERE s.instance_id = ?
      )
      """;

  public static final String DELETE_TABLES_FOR_INSTANCE =
      """
      DELETE FROM sc_tables
      WHERE schema_id IN (SELECT id FROM sc_schemas WHERE instance_id = ?)
      """;

  public static final String DELETE_SCHEMAS_FOR_INSTANCE =
      """
      DELETE FROM sc_schemas
      WHERE instance_id = ?
      """;

  public static final String INSERT_SCHEMA =
      """
      INSERT INTO sc_schemas (instance_id, schema_name, owner, comment)
      VALUES (?, ?, ?, ?)
      RETURNING id
      """;

  public static final String INSERT_TABLE =
      """
      INSERT INTO sc_tables (schema_id, table_name, kind)
      VALUES (?, ?, ?)
      RETURNING id
      """;

  public static final String INSERT_COLUMN =
      """
      INSERT INTO sc_columns
        (table_id, column_name, ordinal_position, data_type, is_nullable, default_value, is_generated)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      """;

  public static final String INSERT_CONSTRAINT =
      """
      INSERT INTO sc_constraints
        (table_id, constraint_name, type, expression, ref_table_id, on_delete, on_update)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      RETURNING id
      """;

  public static final String INSERT_CONSTRAINT_COLUMN =
      """
      INSERT INTO sc_constraint_columns (constraint_id, column_name, position, ref_column_name)
      VALUES (?, ?, ?, ?)
      """;

  public static final String INSERT_INDEX =
      """
      INSERT INTO sc_indexes (table_id, index_name, is_unique, is_primary, kind, definition)
      VALUES (?, ?, ?, ?, ?, ?)
      RETURNING id
      """;

  public static final String INSERT_INDEX_COLUMN =
      """
      INSERT INTO sc_index_columns (index_id, column_name, position)
      VALUES (?, ?, ?)
      """;

  public static final String FIND_SCHEMAS_BY_INSTANCE =
      """
      SELECT id, instance_id, schema_name, owner, comment
      FROM sc_schemas
      WHERE instance_id = ?
      ORDER BY schema_name
      """;

  public static final String FIND_TABLES_BY_INSTANCE =
      """
      SELECT t.id, t.schema_id, t.table_name, t.kind
      FROM sc_tables t
      JOIN sc_schemas s ON s.id = t.schema_id
      WHERE s.instance_id = ?
      ORDER BY t.table_name
      """;

  public static final String FIND_COLUMNS_BY_INSTANCE =
      """
      SELECT c.id, c.table_id, c.column_name, c.ordinal_position, c.data_type,
             c.is_nullable, c.default_value, c.is_generated
      FROM sc_columns c
      JOIN sc_tables t ON t.id = c.table_id
      JOIN sc_schemas s ON s.id = t.schema_id
      WHERE s.instance_id = ?
      ORDER BY c.ordinal_position
      """;

  public static final String FIND_CONSTRAINTS_BY_INSTANCE =
      """
      SELECT c.id, c.table_id, c.constraint_name, c.type, c.expression,
             c.ref_table_id, c.on_delete, c.on_update
      FROM sc_constraints c
      JOIN sc_tables t ON t.id = c.table_id
      JOIN sc_schemas s ON s.id = t.schema_id
      WHERE s.instance_id = ?
      ORDER BY c.constraint_name
      """;

  public static final String FIND_CONSTRAINT_COLUMNS_BY_INSTANCE =
      """
      SELECT cc.constraint_id, cc.column_name, cc.position, cc.ref_column_name
      FROM sc_constraint_columns cc
      JOIN sc_constraints c ON c.id = cc.constraint_id
      JOIN sc_tables t ON t.id = c.table_id
      JOIN sc_schemas s ON s.id = t.schema_id
      WHERE s.instance_id = ?
      ORDER BY cc.constraint_id, cc.position
      """;

  public static final String FIND_INDEXES_BY_INSTANCE =
      """
      SELECT i.id, i.table_id, i.index_name, i.is_unique, i.is_primary, i.kind, i.definition
      FROM sc_indexes i
      JOIN sc_tables t ON t.id = i.table_id
      JOIN sc_schemas s ON s.id = t.schema_id
      WHERE s.instance_id = ?
      ORDER BY i.index_name
      """;

  public static final String FIND_INDEX_COLUMNS_BY_INSTANCE =
      """
      SELECT ic.index_id, ic.column_name, ic.position
      FROM sc_index_columns ic
      JOIN sc_indexes i ON i.id = ic.index_id
      JOIN sc_tables t ON t.id = i.table_id
      JOIN sc_schemas s ON s.id = t.schema_id
      WHERE s.instance_id = ?
      ORDER BY ic.index_id, ic.position
      """;

  public static final String INSERT_QUERY_HISTORY =
      """
      INSERT INTO sc_query_history (sql_text, status, row_count, duration_ms, error)
      VALUES (?, ?, ?, ?, ?)
      """;

  public static final String FIND_QUERY_HISTORY =
      """
      SELECT id, sql_text, status, row_count, duration_ms, error, created_at
      FROM sc_query_history
      ORDER BY id DESC
      LIMIT ?
      """;

  // -------------------------------------------------------------------------
  // MariaDB/MySQL: словарь information_schema (источник)
  // -------------------------------------------------------------------------

  private static final String MARIADB_SYSTEM_SCHEMAS =
      "'mysql', 'information_schema', 'performance_schema', 'sys'";

  public static final String MARIADB_FIND_SCHEMAS =
      """
      SELECT SCHEMA_NAME AS schema_name
      FROM information_schema.SCHEMATA
      WHERE SCHEMA_NAME NOT IN (%s)
      ORDER BY SCHEMA_NAME
      """
          .formatted(MARIADB_SYSTEM_SCHEMAS);

  public static final String MARIADB_FIND_TABLES =
      """
      SELECT TABLE_SCHEMA AS schema_name, TABLE_NAME AS table_name, TABLE_TYPE AS table_type
      FROM information_schema.TABLES
      WHERE TABLE_SCHEMA NOT IN (%s)
      ORDER BY TABLE_SCHEMA, TABLE_NAME
      """
          .formatted(MARIADB_SYSTEM_SCHEMAS);

  public static final String MARIADB_FIND_COLUMNS =
      """
      SELECT TABLE_SCHEMA AS schema_name, TABLE_NAME AS table_name, COLUMN_NAME AS column_name,
             ORDINAL_POSITION AS ordinal_position, DATA_TYPE AS data_type,
             (IS_NULLABLE = 'YES') AS nullable, COLUMN_DEFAULT AS default_value,
             (EXTRA LIKE '%%GENERATED%%') AS generated
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA NOT IN (%s)
      ORDER BY TABLE_SCHEMA, TABLE_NAME, ORDINAL_POSITION
      """
          .formatted(MARIADB_SYSTEM_SCHEMAS);

  public static final String MARIADB_FIND_CONSTRAINTS =
      """
      SELECT tc.TABLE_SCHEMA AS schema_name, tc.TABLE_NAME AS table_name,
             tc.CONSTRAINT_NAME AS constraint_name, tc.CONSTRAINT_TYPE AS constraint_type,
             kcu.COLUMN_NAME AS column_name, kcu.ORDINAL_POSITION AS position,
             kcu.REFERENCED_TABLE_SCHEMA AS ref_schema_name,
             kcu.REFERENCED_TABLE_NAME AS ref_table_name,
             kcu.REFERENCED_COLUMN_NAME AS ref_column_name,
             rc.DELETE_RULE AS delete_rule, rc.UPDATE_RULE AS update_rule,
             cc.CHECK_CLAUSE AS check_clause
      FROM information_schema.TABLE_CONSTRAINTS tc
      LEFT JOIN information_schema.KEY_COLUMN_USAGE kcu
             ON kcu.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
            AND kcu.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
            AND kcu.TABLE_SCHEMA = tc.TABLE_SCHEMA
            AND kcu.TABLE_NAME = tc.TABLE_NAME
      LEFT JOIN information_schema.REFERENTIAL_CONSTRAINTS rc
             ON rc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
            AND rc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
      LEFT JOIN information_schema.CHECK_CONSTRAINTS cc
             ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
            AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
      WHERE tc.TABLE_SCHEMA NOT IN (%s)
      ORDER BY tc.TABLE_SCHEMA, tc.TABLE_NAME, tc.CONSTRAINT_NAME, kcu.ORDINAL_POSITION
      """
          .formatted(MARIADB_SYSTEM_SCHEMAS);

  public static final String MARIADB_FIND_INDEXES =
      """
      SELECT TABLE_SCHEMA AS schema_name, TABLE_NAME AS table_name,
             INDEX_NAME AS index_name, NON_UNIQUE AS non_unique, INDEX_TYPE AS index_type,
             SEQ_IN_INDEX AS position, COLUMN_NAME AS column_name
      FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA NOT IN (%s)
      ORDER BY TABLE_SCHEMA, TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
      """
          .formatted(MARIADB_SYSTEM_SCHEMAS);
}
