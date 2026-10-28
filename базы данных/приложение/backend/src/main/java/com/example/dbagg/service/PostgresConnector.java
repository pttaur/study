package com.example.dbagg.service;

import com.example.dbagg.model.Catalog;
import com.example.dbagg.model.Column;
import com.example.dbagg.model.Constraint;
import com.example.dbagg.model.ConstraintColumn;
import com.example.dbagg.model.ConstraintType;
import com.example.dbagg.model.DbKind;
import com.example.dbagg.model.Index;
import com.example.dbagg.model.IndexColumn;
import com.example.dbagg.model.IndexKind;
import com.example.dbagg.model.Instance;
import com.example.dbagg.model.Schema;
import com.example.dbagg.model.TableKind;
import com.example.dbagg.util.RowsUtil;
import com.example.dbagg.util.SqlQueries;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

/** Коннектор PostgreSQL: каталог через {@code pg_catalog}, идентификаторы в двойных кавычках. */
@ApplicationScoped
public class PostgresConnector implements SourceConnector {
  private static final Pattern ATTNUMS = Pattern.compile("\\d+");

  @Override
  public DbKind kind() {
    return DbKind.POSTGRES;
  }

  @Override
  public Catalog readCatalog(Instance instance, String password) {
    try (var conn = SourceConnection.openReadOnly(instance, password)) {
      return new Catalog(
          instance.id(), loadSchemas(DSL.using(conn, SQLDialect.POSTGRES), instance.id()));
    } catch (SQLException e) {
      throw new IllegalStateException(
          "Failed to connect to target database for instance " + instance.id(), e);
    }
  }

  @Override
  public String tableReference(String schema, String table, String alias) {
    var reference = RowsUtil.quoteIdent(schema) + "." + RowsUtil.quoteIdent(table);
    return alias == null ? reference : reference + " AS " + RowsUtil.quoteIdent(alias);
  }

  @Override
  public String mapTypeName(String sourceType) {
    if (sourceType == null) {
      return "text";
    }
    var type = sourceType.trim();
    return type.matches("[A-Za-z][A-Za-z0-9_ (),]*") ? type : "text";
  }

  private List<Schema> loadSchemas(DSLContext dsl, long instanceId) {
    var schemaNames = new LinkedHashSet<String>();
    var ownerByName = new HashMap<String, String>();
    for (var row : dsl.fetch(DSL.sql(SqlQueries.FIND_SCHEMAS))) {
      var name = row.get("schema_name", String.class);
      schemaNames.add(name);
      ownerByName.put(name, row.get("owner", String.class));
    }

    var tableRowsByName = new HashMap<String, List<Record>>();
    var tableKinds = new HashMap<String, String>();
    for (var row : dsl.fetch(DSL.sql(SqlQueries.FIND_TABLES_WITH_SCHEMA))) {
      var schemaName = row.get("schema_name", String.class);
      var tableName = row.get("table_name", String.class);
      tableRowsByName.computeIfAbsent(schemaName, k -> new ArrayList<>()).add(row);
      tableKinds.put(tableKey(schemaName, tableName), row.get("kind", String.class));
    }

    var attrsByTable = new HashMap<String, Map<Integer, String>>();
    for (var row : dsl.fetch(DSL.sql(SqlQueries.FIND_ATTRIBUTES_ALL))) {
      var key = tableKey(row.get("schema_name", String.class), row.get("table_name", String.class));
      attrsByTable
          .computeIfAbsent(key, k -> new HashMap<>())
          .put(row.get("attnum", Integer.class), row.get("column_name", String.class));
    }

    var columnsByTableKey = new HashMap<String, List<Column>>();
    for (var row : dsl.fetch(DSL.sql(SqlQueries.FIND_COLUMNS_ALL))) {
      var key = tableKey(row.get("schema_name", String.class), row.get("table_name", String.class));
      columnsByTableKey.computeIfAbsent(key, k -> new ArrayList<>()).add(column(row));
    }

    var constraintsByTableKey = new HashMap<String, List<Constraint>>();
    for (var row : dsl.fetch(DSL.sql(SqlQueries.FIND_CONSTRAINTS_ALL))) {
      var key = tableKey(row.get("schema_name", String.class), row.get("table_name", String.class));
      constraintsByTableKey
          .computeIfAbsent(key, k -> new ArrayList<>())
          .add(constraint(row, attrsByTable));
    }

    var indexesByTableKey = new HashMap<String, List<Index>>();
    for (var row : dsl.fetch(DSL.sql(SqlQueries.FIND_INDEXES_ALL))) {
      var key = tableKey(row.get("schema_name", String.class), row.get("table_name", String.class));
      indexesByTableKey.computeIfAbsent(key, k -> new ArrayList<>()).add(index(row, attrsByTable));
    }

    var tableEntries = new HashMap<String, List<CatalogAssembler.TableEntry>>();
    tableRowsByName.forEach(
        (schemaName, rows) -> {
          var entries = new ArrayList<CatalogAssembler.TableEntry>();
          for (var row : rows) {
            entries.add(
                CatalogAssembler.TableEntry.of(schemaName, row.get("table_name", String.class)));
          }
          tableEntries.put(schemaName, entries);
        });
    return CatalogAssembler.assemble(
        instanceId,
        schemaNames,
        ownerByName,
        tableEntries,
        entry -> tableKind(tableKinds.get(entry.key()), entry.schemaName(), entry.tableName()),
        columnsByTableKey,
        constraintsByTableKey,
        indexesByTableKey);
  }

  private String tableKey(String schemaName, String tableName) {
    return schemaName + "." + tableName;
  }

  private Column column(Record row) {
    return new Column(
        0,
        0,
        row.get("column_name", String.class),
        row.get("ordinal_position", Integer.class),
        row.get("data_type", String.class),
        row.get("nullable", Boolean.class),
        row.get("default_value", String.class),
        row.get("generated", Boolean.class));
  }

  private Constraint constraint(Record row, Map<String, Map<Integer, String>> attrsByTable) {
    var schemaName = row.get("schema_name", String.class);
    var tableName = row.get("table_name", String.class);
    var ownAttrs = attrsByTable.getOrDefault(tableKey(schemaName, tableName), Map.of());
    var type = ConstraintType.fromContype(row.get("type", String.class));
    var name = row.get("constraint_name", String.class);
    var definition = row.get("definition", String.class);

    String expression = null;
    String refTable = null;
    String onDelete = null;
    String onUpdate = null;
    var columns = new ArrayList<ConstraintColumn>();

    if (type == ConstraintType.CHECK || type == ConstraintType.EXCLUDE) {
      expression = definition;
    } else {
      var keyAttnums = parseAttnums(row.get("key_attrs", String.class));
      if (type == ConstraintType.FOREIGN_KEY) {
        var refSchema = row.get("ref_schema_name", String.class);
        var refTableName = row.get("ref_table_name", String.class);
        refTable = refSchema + "." + refTableName;
        onDelete = mapAction(row.get("on_delete", String.class));
        onUpdate = mapAction(row.get("on_update", String.class));
        var refAttrs = attrsByTable.getOrDefault(refTable, Map.of());
        var refAttnums = parseAttnums(row.get("ref_attrs", String.class));
        for (int i = 0; i < keyAttnums.size(); i++) {
          var ownName = ownAttrs.get(keyAttnums.get(i));
          if (ownName == null) {
            continue;
          }
          var refName = i < refAttnums.size() ? refAttrs.get(refAttnums.get(i)) : null;
          columns.add(new ConstraintColumn(ownName, i + 1, refName));
        }
      } else {
        for (int i = 0; i < keyAttnums.size(); i++) {
          var ownName = ownAttrs.get(keyAttnums.get(i));
          if (ownName != null) {
            columns.add(new ConstraintColumn(ownName, i + 1, null));
          }
        }
      }
    }
    return new Constraint(0, 0, name, type, expression, refTable, onDelete, onUpdate, columns);
  }

  private Index index(Record row, Map<String, Map<Integer, String>> attrsByTable) {
    var ownAttrs =
        attrsByTable.getOrDefault(
            tableKey(row.get("schema_name", String.class), row.get("table_name", String.class)),
            Map.of());
    var attnums = parseAttnums(row.get("key_attrs", String.class));
    var keyCount = row.get("key_count", Integer.class);
    var limit = keyCount == null ? attnums.size() : Math.min(keyCount, attnums.size());
    var columns = new ArrayList<IndexColumn>();
    for (int i = 0; i < limit; i++) {
      var name = ownAttrs.get(attnums.get(i));
      if (name != null) {
        columns.add(new IndexColumn(name, i + 1));
      }
    }
    return new Index(
        0,
        0,
        row.get("index_name", String.class),
        row.get("is_unique", Boolean.class),
        row.get("is_primary", Boolean.class),
        IndexKind.fromAmname(row.get("kind", String.class)),
        row.get("definition", String.class),
        columns);
  }

  private static List<Integer> parseAttnums(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    var result = new ArrayList<Integer>();
    var matcher = ATTNUMS.matcher(value);
    while (matcher.find()) {
      result.add(Integer.parseInt(matcher.group()));
    }
    return result;
  }

  private static String mapAction(String code) {
    if (code == null) {
      return "NO_ACTION";
    }
    return switch (code.trim()) {
      case "c" -> "CASCADE";
      case "r" -> "RESTRICT";
      case "n" -> "SET_NULL";
      case "d" -> "SET_DEFAULT";
      default -> "NO_ACTION";
    };
  }

  private TableKind tableKind(String relkind, String schemaName, String tableName) {
    try {
      return TableKind.fromRelkind(relkind);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(
          "Unknown table kind '%s' for table %s.%s".formatted(relkind, schemaName, tableName), e);
    }
  }
}
