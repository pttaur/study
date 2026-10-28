package com.example.dbagg.service;

import com.example.dbagg.model.FederationQuery;
import com.example.dbagg.model.TableRef;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.PlainSelect;

/**
 * Разбирает федеративный SQL в {@link FederationQuery}. Использует JSqlParser только для построения
 * AST; исполнение и планирование — в других классах.
 *
 * <p>Поддерживается ровно один {@code SELECT}-запрос. DDL/DML, несколько стейтментов, подзапросы в
 * FROM и CTE отклоняются.
 */
@ApplicationScoped
public class QueryParser {
  public FederationQuery parse(String sql) {
    if (sql == null || sql.isBlank()) {
      throw new IllegalArgumentException("SQL must not be blank");
    }
    var statements = parseStatements(sql);
    if (statements.size() != 1) {
      throw new IllegalArgumentException("Only a single statement is allowed");
    }
    if (!(statements.get(0) instanceof PlainSelect select)) {
      throw new IllegalArgumentException("Only a single SELECT query is supported");
    }
    if (select.getWithItemsList() != null && !select.getWithItemsList().isEmpty()) {
      throw new IllegalArgumentException("CTE (WITH) is not supported yet");
    }

    var tables = new ArrayList<TableRef>();
    addTable(select.getFromItem(), tables);
    var joins = select.getJoins();
    if (joins != null) {
      for (var join : joins) {
        addTable(join.getRightItem(), tables);
      }
    }

    return new FederationQuery(
        sql.strip(),
        tables,
        joins == null ? 0 : joins.size(),
        select.getWhere() != null,
        select.getGroupBy() != null,
        select.getOrderByElements() != null && !select.getOrderByElements().isEmpty(),
        limitOf(select));
  }

  private List<Statement> parseStatements(String sql) {
    try {
      return CCJSqlParserUtil.parseStatements(sql).getStatements();
    } catch (JSQLParserException e) {
      throw new IllegalArgumentException("Failed to parse SQL: " + e.getMessage(), e);
    }
  }

  private void addTable(FromItem item, List<TableRef> tables) {
    if (!(item instanceof Table table)) {
      throw new IllegalArgumentException("Subqueries and expressions in FROM are not supported");
    }
    tables.add(toTableRef(table));
  }

  /** Извлекает {@link TableRef} из AST-таблицы JSqlParser. */
  public static TableRef toTableRef(Table table) {
    // JSqlParser возвращает части имени в порядке от таблицы к схеме/инстансу:
    // "instance.schema.table" -> [table, schema, instance].
    var parts = table.getNameParts();
    if (parts.isEmpty() || parts.size() > 3) {
      throw new IllegalArgumentException("Invalid table name: " + table.getFullyQualifiedName());
    }
    var tableName = parts.get(0);
    var schemaName = parts.size() >= 2 ? parts.get(1) : null;
    var instanceName = parts.size() == 3 ? parts.get(2) : null;
    var tableAlias = table.getAlias() == null ? null : table.getAlias().getName();
    return new TableRef(
        unquote(instanceName), unquote(schemaName), unquote(tableName), unquote(tableAlias));
  }

  private Long limitOf(PlainSelect select) {
    var limit = select.getLimit();
    if (limit == null || !(limit.getRowCount() instanceof LongValue value)) {
      return null;
    }
    return value.getValue();
  }

  private static String unquote(String value) {
    if (value == null) {
      return null;
    }
    var trimmed = value.trim();
    if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
      return trimmed.substring(1, trimmed.length() - 1).replace("\"\"", "\"");
    }
    if (trimmed.length() >= 2 && trimmed.startsWith("`") && trimmed.endsWith("`")) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
  }
}
