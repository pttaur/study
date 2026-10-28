package com.example.dbagg.service;

import com.example.dbagg.model.JoinRequest;
import com.example.dbagg.model.JoinTableSpec;
import com.example.dbagg.model.JoinType;
import com.example.dbagg.model.SelectColumn;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Компилирует модель конструктора джойнов ({@link JoinRequest}) в федеративный SQL.
 *
 * <p>Идентификаторы квотируются, поэтому имена не могут инъецировать SQL; сам запрос затем проходит
 * обычный пайплайн {@link FederationService} (резолв, пушдаун, материализация).
 */
@ApplicationScoped
public class JoinSqlBuilder {
  public String build(JoinRequest request) {
    var tables = request.tables();
    if (tables.isEmpty()) {
      throw new IllegalArgumentException("At least one table is required");
    }
    if (request.select().isEmpty()) {
      throw new IllegalArgumentException("Select at least one column");
    }

    var aliasByRef = new HashMap<String, String>();
    for (int i = 0; i < tables.size(); i++) {
      var ref = tables.get(i).ref();
      validateRef(ref);
      if (aliasByRef.putIfAbsent(ref, "t" + i) != null) {
        throw new IllegalArgumentException("Duplicate table in join: " + ref);
      }
    }

    var sql = new StringBuilder("SELECT ");
    appendSelect(sql, request.select(), aliasByRef);
    sql.append(" FROM ")
        .append(tableRef(tables.get(0).ref()))
        .append(' ')
        .append(aliasByRef.get(tables.get(0).ref()));
    appendJoins(sql, tables, aliasByRef);
    appendOrderBy(sql, request.select(), aliasByRef);
    return sql.toString();
  }

  private void appendSelect(
      StringBuilder sql, List<SelectColumn> select, Map<String, String> aliasByRef) {
    var counts = new HashMap<String, Integer>();
    for (var column : select) {
      counts.merge(column.column(), 1, Integer::sum);
    }
    for (int i = 0; i < select.size(); i++) {
      var column = select.get(i);
      var alias = requireAlias(aliasByRef, column.table(), "select");
      var label = counts.get(column.column()) > 1 ? alias + "_" + column.column() : column.column();
      if (i > 0) {
        sql.append(", ");
      }
      sql.append(alias)
          .append('.')
          .append(quote(column.column()))
          .append(" AS ")
          .append(quote(label));
    }
  }

  private void appendJoins(
      StringBuilder sql, List<JoinTableSpec> tables, Map<String, String> aliasByRef) {
    for (int i = 1; i < tables.size(); i++) {
      var table = tables.get(i);
      if (table.type() == null) {
        throw new IllegalArgumentException("Join type is required for " + table.ref());
      }
      if (table.on().isEmpty()) {
        throw new IllegalArgumentException("Join conditions are required for " + table.ref());
      }
      sql.append(' ')
          .append(joinKeyword(table.type()))
          .append(' ')
          .append(tableRef(table.ref()))
          .append(' ')
          .append(aliasByRef.get(table.ref()))
          .append(" ON ");
      for (int c = 0; c < table.on().size(); c++) {
        var condition = table.on().get(c);
        var left = requireAlias(aliasByRef, condition.leftTable(), "condition");
        var right = requireAlias(aliasByRef, condition.rightTable(), "condition");
        if (c > 0) {
          sql.append(" AND ");
        }
        sql.append(left)
            .append('.')
            .append(quote(condition.leftColumn()))
            .append(" = ")
            .append(right)
            .append('.')
            .append(quote(condition.rightColumn()));
      }
    }
  }

  private void appendOrderBy(
      StringBuilder sql, List<SelectColumn> select, Map<String, String> aliasByRef) {
    var expressions = new ArrayList<String>();
    var seen = new HashSet<String>();
    for (var column : select) {
      var expression =
          requireAlias(aliasByRef, column.table(), "select") + "." + quote(column.column());
      if (seen.add(expression)) {
        expressions.add(expression);
      }
    }
    if (!expressions.isEmpty()) {
      sql.append(" ORDER BY ").append(String.join(", ", expressions));
    }
  }

  private static String requireAlias(Map<String, String> aliasByRef, String ref, String where) {
    var alias = aliasByRef.get(ref);
    if (alias == null) {
      throw new IllegalArgumentException("Unknown table in " + where + ": " + ref);
    }
    return alias;
  }

  private static void validateRef(String ref) {
    var parts = splitRef(ref);
    if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
      throw new IllegalArgumentException("Table reference must be instance.schema.table: " + ref);
    }
  }

  private static String[] splitRef(String ref) {
    if (ref == null) {
      throw new IllegalArgumentException("Table reference is required");
    }
    return ref.split("\\.", -1);
  }

  private static String tableRef(String ref) {
    var parts = splitRef(ref);
    return quote(parts[0]) + "." + quote(parts[1]) + "." + quote(parts[2]);
  }

  private static String joinKeyword(JoinType type) {
    return switch (type) {
      case INNER -> "INNER JOIN";
      case LEFT -> "LEFT JOIN";
      case RIGHT -> "RIGHT JOIN";
      case FULL -> "FULL JOIN";
    };
  }

  private static String quote(String ident) {
    return "\"" + ident.replace("\"", "\"\"") + "\"";
  }
}
