package com.example.dbagg.service;

import com.example.dbagg.model.FederationPlan;
import com.example.dbagg.model.ResolvedTable;
import com.example.dbagg.model.SourcePlan;
import com.example.dbagg.model.TableRef;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * Анализирует федеративный запрос: вычисляет проекцию колонок и однотабличные предикаты для
 * пушдауна в источники, выдаёт SQL для внутреннего PostgreSQL.
 *
 * <p>Консервативно: если колонку нельзя однозначно отнести к таблице (или в запросе есть {@code
 * *}), для таблиц запрашиваются все колонки. Предикаты с подзапросами и неквалифицированными
 * колонками не продавливаются.
 */
@ApplicationScoped
public class FederationAnalyzer {
  public FederationPlan analyze(
      String sql, String runId, Function<TableRef, ResolvedTable> resolver) {
    var select = parse(sql);
    var tableNodes = collectTables(select);
    if (tableNodes.isEmpty()) {
      throw new IllegalArgumentException("Query must reference at least one table");
    }
    assertDeclaredRelations(select, tableNodes);

    var infos = new LinkedHashMap<String, TableInfo>();
    for (var table : tableNodes) {
      var ref = QueryParser.toTableRef(table);
      infos.computeIfAbsent(ref.canonical(), key -> new TableInfo(table, ref, resolver.apply(ref)));
    }

    var qualifierToKey = new HashMap<String, String>();
    for (var info : infos.values()) {
      var ref = info.ref();
      var qualifier = ref.tableAlias() != null ? ref.tableAlias() : ref.tableName();
      qualifierToKey.put(qualifier, ref.canonical());
    }

    var pushed = analyzeWhere(select, qualifierToKey);
    var needed = computeNeeded(select, qualifierToKey, infos);

    var sources = new ArrayList<SourcePlan>();
    var index = 0;
    for (var info : infos.values()) {
      var key = info.ref().canonical();
      var tempName = "fed_" + runId + "_" + index++;
      var columns = needed.get(key);
      sources.add(
          new SourcePlan(
              info.ref(),
              info.resolved(),
              tempName,
              columns == null ? List.of() : columns,
              renderPredicate(pushed.get(key))));
      // setSchemaName(null) не очищает каталог 3-частного имени — заменяем имя целиком
      info.table().setNameParts(List.of(tempName));
    }
    return new FederationPlan(select.toString(), List.copyOf(sources));
  }

  private Map<String, List<Expression>> analyzeWhere(
      PlainSelect select, Map<String, String> qualifierToKey) {
    var where = select.getWhere();
    if (where == null) {
      return Map.of();
    }
    var conjuncts = new ArrayList<Expression>();
    flatten(where, conjuncts);

    var pushed = new LinkedHashMap<String, List<Expression>>();
    var residual = new ArrayList<Expression>();
    for (var conjunct : conjuncts) {
      var key = singleTableKey(conjunct, qualifierToKey);
      if (key != null) {
        pushed.computeIfAbsent(key, ignored -> new ArrayList<>()).add(conjunct);
      } else {
        residual.add(conjunct);
      }
    }
    select.setWhere(combine(residual));
    return pushed;
  }

  private void flatten(Expression expression, List<Expression> out) {
    if (expression instanceof Parenthesis parenthesis) {
      flatten(parenthesis.getExpression(), out);
    } else if (expression instanceof AndExpression and) {
      flatten(and.getLeftExpression(), out);
      flatten(and.getRightExpression(), out);
    } else {
      out.add(expression);
    }
  }

  private Expression combine(List<Expression> parts) {
    Expression result = null;
    for (var part : parts) {
      result = result == null ? part : new AndExpression(result, part);
    }
    return result;
  }

  private String singleTableKey(Expression conjunct, Map<String, String> qualifierToKey) {
    if (conjunct.toString().toLowerCase(Locale.ROOT).contains("select")) {
      return null;
    }
    var columns = columnsOf(conjunct);
    if (columns.isEmpty()) {
      return null;
    }
    var keys = new LinkedHashSet<String>();
    for (var column : columns) {
      var qualifier = column.getTable() == null ? null : unquote(column.getTable().getName());
      if (qualifier == null) {
        return null;
      }
      var key = qualifierToKey.get(qualifier);
      if (key == null) {
        return null;
      }
      keys.add(key);
    }
    return keys.size() == 1 ? keys.iterator().next() : null;
  }

  private Map<String, List<String>> computeNeeded(
      PlainSelect select, Map<String, String> qualifierToKey, Map<String, TableInfo> infos) {
    var forceAll = new boolean[] {false};
    var sets = new LinkedHashMap<String, Set<String>>();
    for (var info : infos.values()) {
      sets.put(info.ref().canonical(), new LinkedHashSet<>());
    }

    for (var item : select.getSelectItems()) {
      var expression = item.getExpression();
      if (expression instanceof AllColumns || expression instanceof AllTableColumns) {
        forceAll[0] = true;
        continue;
      }
      attribute(columnsOf(expression), qualifierToKey, infos, sets, forceAll);
    }
    attribute(columnsOf(select.getWhere()), qualifierToKey, infos, sets, forceAll);
    attribute(columnsOf(select.getHaving()), qualifierToKey, infos, sets, forceAll);

    var groupBy = select.getGroupBy();
    if (groupBy != null && groupBy.getGroupByExpressionList() != null) {
      for (var expression : groupBy.getGroupByExpressionList().getExpressions()) {
        attribute(columnsOf(expression), qualifierToKey, infos, sets, forceAll);
      }
    }
    if (select.getOrderByElements() != null) {
      for (var element : select.getOrderByElements()) {
        attribute(columnsOf(element.getExpression()), qualifierToKey, infos, sets, forceAll);
      }
    }
    var joins = select.getJoins();
    if (joins != null) {
      for (var join : joins) {
        if (join.getOnExpressions() != null) {
          for (var expression : join.getOnExpressions()) {
            attribute(columnsOf(expression), qualifierToKey, infos, sets, forceAll);
          }
        }
        if (join.getUsingColumns() != null) {
          attribute(join.getUsingColumns(), qualifierToKey, infos, sets, forceAll);
        }
      }
    }

    var result = new LinkedHashMap<String, List<String>>();
    var all = forceAll[0];
    if (!all) {
      for (var value : sets.values()) {
        if (value.isEmpty()) {
          all = true;
          break;
        }
      }
    }
    for (var entry : sets.entrySet()) {
      result.put(entry.getKey(), all ? null : List.copyOf(entry.getValue()));
    }
    return result;
  }

  private void attribute(
      List<Column> columns,
      Map<String, String> qualifierToKey,
      Map<String, TableInfo> infos,
      Map<String, Set<String>> sets,
      boolean[] forceAll) {
    for (var column : columns) {
      var qualifier = column.getTable() == null ? null : unquote(column.getTable().getName());
      var columnName = unquote(column.getColumnName());
      String key;
      if (qualifier != null) {
        key = qualifierToKey.get(qualifier);
        if (key == null) {
          forceAll[0] = true;
          continue;
        }
      } else {
        key = uniqueTableForColumn(columnName, infos);
        if (key == null) {
          forceAll[0] = true;
          continue;
        }
      }
      sets.get(key).add(columnName);
    }
  }

  private String uniqueTableForColumn(String columnName, Map<String, TableInfo> infos) {
    String found = null;
    for (var info : infos.values()) {
      if (info.resolved().columns().contains(columnName)) {
        var key = info.ref().canonical();
        if (found != null && !found.equals(key)) {
          return null;
        }
        found = key;
      }
    }
    return found;
  }

  private String renderPredicate(List<Expression> predicates) {
    if (predicates == null || predicates.isEmpty()) {
      return null;
    }
    var builder = new StringBuilder();
    for (int i = 0; i < predicates.size(); i++) {
      if (i > 0) {
        builder.append(" AND ");
      }
      builder.append(predicates.get(i));
    }
    return builder.toString();
  }

  private List<Table> collectTables(PlainSelect select) {
    var tables = new ArrayList<Table>();
    if (select.getFromItem() instanceof Table table) {
      tables.add(table);
    } else {
      throw new IllegalArgumentException("Subqueries and expressions in FROM are not supported");
    }
    var joins = select.getJoins();
    if (joins != null) {
      for (var join : joins) {
        if (join.getRightItem() instanceof Table joinTable) {
          tables.add(joinTable);
        } else {
          throw new IllegalArgumentException(
              "Subqueries and expressions in FROM are not supported");
        }
      }
    }
    return tables;
  }

  private List<Column> columnsOf(Expression expression) {
    if (expression == null) {
      return List.of();
    }
    var collector = new ColumnCollector();
    expression.accept(collector);
    return collector.columns;
  }

  private PlainSelect parse(String sql) {
    try {
      var statement = CCJSqlParserUtil.parse(sql);
      if (!(statement instanceof PlainSelect select)) {
        throw new IllegalArgumentException("Only a single SELECT query is supported");
      }
      return select;
    } catch (JSQLParserException e) {
      throw new IllegalArgumentException("Failed to parse SQL: " + e.getMessage(), e);
    }
  }

  /**
   * Не допускает обращения к отношениям, которые не были переписаны на временные таблицы (в первую
   * очередь подзапросы к внутреннему хранилищу {@code sc_*}).
   */
  private void assertDeclaredRelations(PlainSelect select, List<Table> tables) {
    var allowed = new LinkedHashSet<String>();
    for (var table : tables) {
      allowed.add(normalizeName(table.getFullyQualifiedName()));
    }
    for (var name : new TablesNamesFinder<>().getTables((Statement) select)) {
      if (!allowed.contains(normalizeName(name))) {
        throw new IllegalArgumentException(
            "Subqueries and references to non-source tables are not supported: " + name);
      }
    }
  }

  private static String normalizeName(String name) {
    if (name == null) {
      return "";
    }
    return name.replace("\"", "").replace("`", "").trim().toLowerCase(Locale.ROOT);
  }

  /** Снимает кавычки с идентификатора из AST ({@code "col"} / {@code `col`} → {@code col}). */
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

  private record TableInfo(Table table, TableRef ref, ResolvedTable resolved) {}

  private static final class ColumnCollector extends ExpressionVisitorAdapter<Void> {
    private final List<Column> columns = new ArrayList<>();

    @Override
    public <S> Void visit(Column column, S context) {
      columns.add(column);
      return super.visit(column, context);
    }
  }
}
