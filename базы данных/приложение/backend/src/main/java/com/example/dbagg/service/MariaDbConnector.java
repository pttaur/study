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
import com.example.dbagg.util.SqlQueries;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Коннектор MariaDB/MySQL: каталог через {@code information_schema}, идентификаторы в бэктиках,
 * табличный алиас через {@code AS}. Чтение — чистый JDBC.
 */
@ApplicationScoped
public class MariaDbConnector implements SourceConnector {

  @Override
  public DbKind kind() {
    return DbKind.MARIADB;
  }

  @Override
  public Catalog readCatalog(Instance instance, String password) {
    try (var conn = SourceConnection.openReadOnly(instance, password)) {
      return new Catalog(instance.id(), loadSchemas(conn, instance.id()));
    } catch (SQLException e) {
      throw new IllegalStateException(
          "Failed to connect to MariaDB for instance " + instance.id(), e);
    }
  }

  @Override
  public String tableReference(String schema, String table, String alias) {
    var reference = quote(schema) + "." + quote(table);
    return alias == null ? reference : reference + " AS " + quote(alias);
  }

  @Override
  public String mapTypeName(String sourceType) {
    if (sourceType == null) {
      return "text";
    }
    var base = sourceType.trim().toLowerCase(Locale.ROOT);
    var paren = base.indexOf('(');
    if (paren >= 0) {
      base = base.substring(0, paren).trim();
    }
    base = base.replace("unsigned", "").trim();
    return switch (base) {
      case "tinyint", "smallint", "year" -> "smallint";
      case "mediumint", "int", "integer" -> "integer";
      case "bigint" -> "bigint";
      case "decimal", "numeric", "dec", "fixed" -> "numeric";
      case "float" -> "real";
      case "double", "double precision", "real" -> "double precision";
      case "bit", "boolean", "bool" -> "boolean";
      case "char", "nchar" -> "char";
      case "varchar", "nvarchar", "varchar2" -> "varchar";
      case "tinytext", "text", "mediumtext", "longtext", "enum", "set", "json" -> "text";
      case "binary", "varbinary", "tinyblob", "blob", "mediumblob", "longblob" -> "bytea";
      case "date" -> "date";
      case "time" -> "time";
      case "datetime", "timestamp" -> "timestamp";
      default -> "text";
    };
  }

  private static String quote(String ident) {
    return "`" + ident.replace("`", "``") + "`";
  }

  private List<Schema> loadSchemas(Connection conn, long instanceId) throws SQLException {
    var schemaNames = new LinkedHashSet<String>();
    var ownerByName = new HashMap<String, String>();
    try (var st = conn.createStatement();
        var rs = st.executeQuery(SqlQueries.MARIADB_FIND_SCHEMAS)) {
      while (rs.next()) {
        schemaNames.add(rs.getString("schema_name"));
      }
    }

    var tablesBySchema = new HashMap<String, List<CatalogAssembler.TableEntry>>();
    var tableTypes = new HashMap<String, String>();
    try (var st = conn.createStatement();
        var rs = st.executeQuery(SqlQueries.MARIADB_FIND_TABLES)) {
      while (rs.next()) {
        var schemaName = rs.getString("schema_name");
        var tableName = rs.getString("table_name");
        tablesBySchema
            .computeIfAbsent(schemaName, k -> new ArrayList<>())
            .add(CatalogAssembler.TableEntry.of(schemaName, tableName));
        tableTypes.put(key(schemaName, tableName), rs.getString("table_type"));
      }
    }

    var columnsByKey = new HashMap<String, List<Column>>();
    try (var st = conn.createStatement();
        var rs = st.executeQuery(SqlQueries.MARIADB_FIND_COLUMNS)) {
      while (rs.next()) {
        var k = key(rs.getString("schema_name"), rs.getString("table_name"));
        columnsByKey
            .computeIfAbsent(k, kk -> new ArrayList<>())
            .add(
                new Column(
                    0,
                    0,
                    rs.getString("column_name"),
                    rs.getInt("ordinal_position"),
                    rs.getString("data_type"),
                    rs.getBoolean("nullable"),
                    rs.getString("default_value"),
                    rs.getBoolean("generated")));
      }
    }

    return CatalogAssembler.assemble(
        instanceId,
        schemaNames,
        ownerByName,
        tablesBySchema,
        entry -> tableKind(tableTypes.get(entry.key())),
        columnsByKey,
        loadConstraints(conn),
        loadIndexes(conn));
  }

  private Map<String, List<Constraint>> loadConstraints(Connection conn) throws SQLException {
    var builders = new LinkedHashMap<String, ConstraintBuilder>();
    try (var st = conn.createStatement();
        var rs = st.executeQuery(SqlQueries.MARIADB_FIND_CONSTRAINTS)) {
      while (rs.next()) {
        var schemaName = rs.getString("schema_name");
        var tableName = rs.getString("table_name");
        var name = rs.getString("constraint_name");
        var type = constraintType(rs.getString("constraint_type"));
        var builder =
            builders.computeIfAbsent(
                key(schemaName, tableName) + "|" + name,
                k -> new ConstraintBuilder(schemaName, tableName, name, type));
        var columnName = rs.getString("column_name");
        var position = rs.getInt("position");
        if (type == ConstraintType.FOREIGN_KEY) {
          var refSchema = rs.getString("ref_schema_name");
          var refTable = rs.getString("ref_table_name");
          builder.refTable = refSchema + "." + refTable;
          builder.onDelete = mapAction(rs.getString("delete_rule"));
          builder.onUpdate = mapAction(rs.getString("update_rule"));
          if (columnName != null) {
            builder.columns.add(
                new ConstraintColumn(columnName, position, rs.getString("ref_column_name")));
          }
        } else if (type == ConstraintType.CHECK) {
          builder.expression = rs.getString("check_clause");
        } else if (columnName != null) {
          builder.columns.add(new ConstraintColumn(columnName, position, null));
        }
      }
    }
    var byTable = new HashMap<String, List<Constraint>>();
    for (var builder : builders.values()) {
      byTable
          .computeIfAbsent(key(builder.schemaName, builder.tableName), k -> new ArrayList<>())
          .add(builder.build());
    }
    return byTable;
  }

  private Map<String, List<Index>> loadIndexes(Connection conn) throws SQLException {
    var builders = new LinkedHashMap<String, IndexBuilder>();
    try (var st = conn.createStatement();
        var rs = st.executeQuery(SqlQueries.MARIADB_FIND_INDEXES)) {
      while (rs.next()) {
        var schemaName = rs.getString("schema_name");
        var tableName = rs.getString("table_name");
        var name = rs.getString("index_name");
        var nonUnique = rs.getInt("non_unique");
        var indexType = rs.getString("index_type");
        var builder =
            builders.computeIfAbsent(
                key(schemaName, tableName) + "|" + name,
                k ->
                    new IndexBuilder(
                        schemaName,
                        tableName,
                        name,
                        nonUnique == 0,
                        "PRIMARY".equalsIgnoreCase(name),
                        indexKind(indexType)));
        var columnName = rs.getString("column_name");
        if (columnName != null) {
          builder.columns.add(new IndexColumn(columnName, rs.getInt("position")));
        }
      }
    }
    var byTable = new HashMap<String, List<Index>>();
    for (var builder : builders.values()) {
      byTable
          .computeIfAbsent(key(builder.schemaName, builder.tableName), k -> new ArrayList<>())
          .add(builder.build());
    }
    return byTable;
  }

  private static String key(String schemaName, String tableName) {
    return schemaName + "." + tableName;
  }

  private static ConstraintType constraintType(String value) {
    if (value == null) {
      return ConstraintType.CHECK;
    }
    return switch (value.trim().toUpperCase(Locale.ROOT)) {
      case "PRIMARY KEY" -> ConstraintType.PRIMARY_KEY;
      case "UNIQUE" -> ConstraintType.UNIQUE;
      case "FOREIGN KEY" -> ConstraintType.FOREIGN_KEY;
      default -> ConstraintType.CHECK;
    };
  }

  private static String mapAction(String rule) {
    if (rule == null) {
      return "NO_ACTION";
    }
    return switch (rule.trim().toUpperCase(Locale.ROOT)) {
      case "CASCADE" -> "CASCADE";
      case "SET NULL" -> "SET_NULL";
      case "SET DEFAULT" -> "SET_DEFAULT";
      case "RESTRICT" -> "RESTRICT";
      default -> "NO_ACTION";
    };
  }

  private static IndexKind indexKind(String indexType) {
    return switch (indexType == null ? "" : indexType.trim().toUpperCase(Locale.ROOT)) {
      case "HASH" -> IndexKind.HASH;
      default -> IndexKind.BTREE;
    };
  }

  private static TableKind tableKind(String tableType) {
    return tableType != null && tableType.toUpperCase(Locale.ROOT).contains("VIEW")
        ? TableKind.VIEW
        : TableKind.TABLE;
  }

  private static final class ConstraintBuilder {
    private final String schemaName;
    private final String tableName;
    private final String name;
    private final ConstraintType type;
    private final List<ConstraintColumn> columns = new ArrayList<>();
    private String expression;
    private String refTable;
    private String onDelete;
    private String onUpdate;

    ConstraintBuilder(String schemaName, String tableName, String name, ConstraintType type) {
      this.schemaName = schemaName;
      this.tableName = tableName;
      this.name = name;
      this.type = type;
    }

    Constraint build() {
      return new Constraint(
          0, 0, name, type, expression, refTable, onDelete, onUpdate, List.copyOf(columns));
    }
  }

  private static final class IndexBuilder {
    private final String schemaName;
    private final String tableName;
    private final String name;
    private final boolean unique;
    private final boolean primary;
    private final IndexKind kind;
    private final List<IndexColumn> columns = new ArrayList<>();

    IndexBuilder(
        String schemaName,
        String tableName,
        String name,
        boolean unique,
        boolean primary,
        IndexKind kind) {
      this.schemaName = schemaName;
      this.tableName = tableName;
      this.name = name;
      this.unique = unique;
      this.primary = primary;
      this.kind = kind;
    }

    Index build() {
      return new Index(0, 0, name, unique, primary, kind, null, List.copyOf(columns));
    }
  }
}
