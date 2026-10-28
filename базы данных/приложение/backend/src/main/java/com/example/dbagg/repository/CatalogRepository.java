package com.example.dbagg.repository;

import com.example.dbagg.model.Catalog;
import com.example.dbagg.model.Column;
import com.example.dbagg.model.Constraint;
import com.example.dbagg.model.ConstraintColumn;
import com.example.dbagg.model.ConstraintType;
import com.example.dbagg.model.Index;
import com.example.dbagg.model.IndexColumn;
import com.example.dbagg.model.IndexKind;
import com.example.dbagg.model.Schema;
import com.example.dbagg.model.Table;
import com.example.dbagg.model.TableKind;
import com.example.dbagg.util.SqlQueries;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Хранилище собранных каталогов схем (таблицы {@code sc_schemas}, {@code sc_tables}, {@code
 * sc_columns}, {@code sc_constraints}/{@code sc_constraint_columns}, {@code sc_indexes}/{@code
 * sc_index_columns}).
 */
@ApplicationScoped
public class CatalogRepository {
  private static final Logger log = LoggerFactory.getLogger(CatalogRepository.class);

  private final javax.sql.DataSource storageDataSource;
  private final DSLContext jooq;

  @Inject
  public CatalogRepository(@DataSource("storage") javax.sql.DataSource storageDataSource) {
    this.storageDataSource = storageDataSource;
    this.jooq = DSL.using(storageDataSource, SQLDialect.POSTGRES);
  }

  public void saveCatalog(Catalog catalog) {
    var instanceId = catalog.instanceId();
    try (var conn = storageDataSource.getConnection()) {
      conn.setAutoCommit(false);
      var dsl = DSL.using(conn, SQLDialect.POSTGRES);
      try {
        deleteInstanceTree(dsl, instanceId);
        var tableIdByRef = new HashMap<String, Long>();
        for (var schema : catalog.schemas()) {
          var schemaId = insertSchema(dsl, instanceId, schema);
          for (var table : schema.tables()) {
            var tableId = insertTable(dsl, schemaId, table);
            tableIdByRef.put(tableRef(schema.schemaName(), table.tableName()), tableId);
            insertColumns(dsl, tableId, table.columns());
            insertIndexes(dsl, tableId, table.indexes());
          }
        }
        for (var schema : catalog.schemas()) {
          for (var table : schema.tables()) {
            var tableId = tableIdByRef.get(tableRef(schema.schemaName(), table.tableName()));
            insertConstraints(dsl, tableId, table.constraints(), tableIdByRef);
          }
        }
        conn.commit();
      } catch (Exception e) {
        conn.rollback();
        throw e;
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to save catalog for instance " + instanceId, e);
    }
  }

  public Catalog loadCatalog(long instanceId) {
    var schemaNameById = new HashMap<Long, String>();
    var schemaRows = jooq.fetch(DSL.sql(SqlQueries.FIND_SCHEMAS_BY_INSTANCE, instanceId));
    for (var schemaRow : schemaRows) {
      schemaNameById.put(
          schemaRow.get("id", Long.class), schemaRow.get("schema_name", String.class));
    }

    var tableRows = jooq.fetch(DSL.sql(SqlQueries.FIND_TABLES_BY_INSTANCE, instanceId));
    var tableRefById = new HashMap<Long, String>();
    for (var tableRow : tableRows) {
      var schemaId = tableRow.get("schema_id", Long.class);
      tableRefById.put(
          tableRow.get("id", Long.class),
          tableRef(schemaNameById.get(schemaId), tableRow.get("table_name", String.class)));
    }

    var columnsByTable = loadColumnsByTable(instanceId);
    var constraintsByTable = loadConstraintsByTable(instanceId, tableRefById);
    var indexesByTable = loadIndexesByTable(instanceId);

    var tablesBySchema = new HashMap<Long, List<Table>>();
    for (var tableRow : tableRows) {
      var tableId = tableRow.get("id", Long.class);
      var schemaId = tableRow.get("schema_id", Long.class);
      tablesBySchema
          .computeIfAbsent(schemaId, k -> new ArrayList<>())
          .add(
              new Table(
                  tableId,
                  schemaId,
                  tableRow.get("table_name", String.class),
                  tableKind(tableRow, "kind"),
                  columnsByTable.getOrDefault(tableId, List.of()),
                  constraintsByTable.getOrDefault(tableId, List.of()),
                  indexesByTable.getOrDefault(tableId, List.of())));
    }

    var schemas = new ArrayList<Schema>();
    for (var schemaRow : schemaRows) {
      var schemaId = schemaRow.get("id", Long.class);
      schemas.add(
          new Schema(
              schemaId,
              instanceId,
              schemaRow.get("schema_name", String.class),
              schemaRow.get("owner", String.class),
              schemaRow.get("comment", String.class),
              List.copyOf(tablesBySchema.getOrDefault(schemaId, List.of()))));
    }
    return new Catalog(instanceId, schemas);
  }

  private Map<Long, List<Column>> loadColumnsByTable(long instanceId) {
    var byTable = new HashMap<Long, List<Column>>();
    var rows = jooq.fetch(DSL.sql(SqlQueries.FIND_COLUMNS_BY_INSTANCE, instanceId));
    for (var row : rows) {
      byTable
          .computeIfAbsent(row.get("table_id", Long.class), k -> new ArrayList<>())
          .add(
              new Column(
                  row.get("id", Long.class),
                  row.get("table_id", Long.class),
                  row.get("column_name", String.class),
                  row.get("ordinal_position", Integer.class),
                  row.get("data_type", String.class),
                  row.get("is_nullable", Boolean.class),
                  row.get("default_value", String.class),
                  row.get("is_generated", Boolean.class)));
    }
    return byTable;
  }

  private Map<Long, List<Constraint>> loadConstraintsByTable(
      long instanceId, Map<Long, String> tableRefById) {
    var columnsByConstraint = new LinkedHashMap<Long, List<ConstraintColumn>>();
    var columnRows =
        jooq.fetch(DSL.sql(SqlQueries.FIND_CONSTRAINT_COLUMNS_BY_INSTANCE, instanceId));
    for (var row : columnRows) {
      columnsByConstraint
          .computeIfAbsent(row.get("constraint_id", Long.class), k -> new ArrayList<>())
          .add(
              new ConstraintColumn(
                  row.get("column_name", String.class),
                  row.get("position", Integer.class),
                  row.get("ref_column_name", String.class)));
    }

    var byTable = new HashMap<Long, List<Constraint>>();
    var rows = jooq.fetch(DSL.sql(SqlQueries.FIND_CONSTRAINTS_BY_INSTANCE, instanceId));
    for (var row : rows) {
      var id = row.get("id", Long.class);
      var refTableId = row.get("ref_table_id", Long.class);
      byTable
          .computeIfAbsent(row.get("table_id", Long.class), k -> new ArrayList<>())
          .add(
              new Constraint(
                  id,
                  row.get("table_id", Long.class),
                  row.get("constraint_name", String.class),
                  constraintType(row, "type"),
                  row.get("expression", String.class),
                  refTableId == null ? null : tableRefById.get(refTableId),
                  row.get("on_delete", String.class),
                  row.get("on_update", String.class),
                  columnsByConstraint.getOrDefault(id, List.of())));
    }
    return byTable;
  }

  private Map<Long, List<Index>> loadIndexesByTable(long instanceId) {
    var columnsByIndex = new LinkedHashMap<Long, List<IndexColumn>>();
    var columnRows = jooq.fetch(DSL.sql(SqlQueries.FIND_INDEX_COLUMNS_BY_INSTANCE, instanceId));
    for (var row : columnRows) {
      columnsByIndex
          .computeIfAbsent(row.get("index_id", Long.class), k -> new ArrayList<>())
          .add(
              new IndexColumn(
                  row.get("column_name", String.class), row.get("position", Integer.class)));
    }

    var byTable = new HashMap<Long, List<Index>>();
    var rows = jooq.fetch(DSL.sql(SqlQueries.FIND_INDEXES_BY_INSTANCE, instanceId));
    for (var row : rows) {
      var id = row.get("id", Long.class);
      byTable
          .computeIfAbsent(row.get("table_id", Long.class), k -> new ArrayList<>())
          .add(
              new Index(
                  id,
                  row.get("table_id", Long.class),
                  row.get("index_name", String.class),
                  row.get("is_unique", Boolean.class),
                  row.get("is_primary", Boolean.class),
                  indexKind(row, "kind"),
                  row.get("definition", String.class),
                  columnsByIndex.getOrDefault(id, List.of())));
    }
    return byTable;
  }

  private TableKind tableKind(Record row, String column) {
    var raw = row.get(column, String.class);
    try {
      return TableKind.valueOf(raw);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "Unknown table kind '%s' in storage row id=%s".formatted(raw, row.get("id")), e);
    }
  }

  private ConstraintType constraintType(Record row, String column) {
    var raw = row.get(column, String.class);
    try {
      return ConstraintType.valueOf(raw);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "Unknown constraint type '%s' in storage row id=%s".formatted(raw, row.get("id")), e);
    }
  }

  private IndexKind indexKind(Record row, String column) {
    var raw = row.get(column, String.class);
    try {
      return IndexKind.valueOf(raw);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "Unknown index kind '%s' in storage row id=%s".formatted(raw, row.get("id")), e);
    }
  }

  private long insertSchema(DSLContext dsl, long instanceId, Schema schema) {
    var rows =
        dsl.fetch(
            DSL.sql(
                SqlQueries.INSERT_SCHEMA,
                instanceId,
                schema.schemaName(),
                schema.owner(),
                schema.comment()));
    return insertedId(rows, "INSERT_SCHEMA for schema " + schema.schemaName());
  }

  private long insertTable(DSLContext dsl, long schemaId, Table table) {
    var rows =
        dsl.fetch(
            DSL.sql(SqlQueries.INSERT_TABLE, schemaId, table.tableName(), table.kind().name()));
    return insertedId(rows, "INSERT_TABLE for table " + table.tableName());
  }

  private void insertColumns(DSLContext dsl, long tableId, List<Column> columns) {
    var queries = new ArrayList<Query>(columns.size());
    for (var column : columns) {
      queries.add(
          dsl.query(
              SqlQueries.INSERT_COLUMN,
              tableId,
              column.columnName(),
              column.ordinalPosition(),
              column.dataType(),
              column.nullable(),
              column.defaultValue(),
              column.generated()));
    }
    dsl.batch(queries).execute();
  }

  private void insertConstraints(
      DSLContext dsl, long tableId, List<Constraint> constraints, Map<String, Long> tableIdByRef) {
    for (var constraint : constraints) {
      Long refTableId = null;
      if (constraint.type() == ConstraintType.FOREIGN_KEY) {
        refTableId = tableIdByRef.get(constraint.refTable());
        if (refTableId == null) {
          log.warn(
              "Skipping FK {} on table {}: referenced table {} is not in the catalog",
              constraint.constraintName(),
              tableId,
              constraint.refTable());
          continue;
        }
      }
      var rows =
          dsl.fetch(
              DSL.sql(
                  SqlQueries.INSERT_CONSTRAINT,
                  tableId,
                  constraint.constraintName(),
                  constraint.type().name(),
                  constraint.expression(),
                  refTableId,
                  constraint.onDelete(),
                  constraint.onUpdate()));
      var constraintId = insertedId(rows, "INSERT_CONSTRAINT for " + constraint.constraintName());
      if (constraint.columns().isEmpty()) {
        continue;
      }
      var queries = new ArrayList<Query>(constraint.columns().size());
      for (var column : constraint.columns()) {
        queries.add(
            dsl.query(
                SqlQueries.INSERT_CONSTRAINT_COLUMN,
                constraintId,
                column.columnName(),
                column.position(),
                column.refColumnName()));
      }
      dsl.batch(queries).execute();
    }
  }

  private void insertIndexes(DSLContext dsl, long tableId, List<Index> indexes) {
    for (var index : indexes) {
      var rows =
          dsl.fetch(
              DSL.sql(
                  SqlQueries.INSERT_INDEX,
                  tableId,
                  index.indexName(),
                  index.unique(),
                  index.primary(),
                  index.kind().name(),
                  index.definition()));
      var indexId = insertedId(rows, "INSERT_INDEX for " + index.indexName());
      if (index.columns().isEmpty()) {
        continue;
      }
      var queries = new ArrayList<Query>(index.columns().size());
      for (var column : index.columns()) {
        queries.add(
            dsl.query(
                SqlQueries.INSERT_INDEX_COLUMN, indexId, column.columnName(), column.position()));
      }
      dsl.batch(queries).execute();
    }
  }

  private long insertedId(List<? extends Record> rows, String context) {
    if (rows.isEmpty()) {
      throw new IllegalStateException(context + " returned no id");
    }
    return rows.get(0).get("id", Long.class);
  }

  private static String tableRef(String schemaName, String tableName) {
    return schemaName + "." + tableName;
  }

  private void deleteInstanceTree(DSLContext dsl, long instanceId) {
    dsl.execute(DSL.sql(SqlQueries.DELETE_INDEXES_FOR_INSTANCE, instanceId));
    dsl.execute(DSL.sql(SqlQueries.DELETE_CONSTRAINTS_FOR_INSTANCE, instanceId));
    dsl.execute(DSL.sql(SqlQueries.DELETE_COLUMNS_FOR_INSTANCE, instanceId));
    dsl.execute(DSL.sql(SqlQueries.DELETE_TABLES_FOR_INSTANCE, instanceId));
    dsl.execute(DSL.sql(SqlQueries.DELETE_SCHEMAS_FOR_INSTANCE, instanceId));
  }
}
