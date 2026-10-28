package com.example.dbagg.service;

import com.example.dbagg.model.ColumnInfo;
import com.example.dbagg.model.SourceData;
import com.example.dbagg.model.SourcePlan;
import com.example.dbagg.repository.InstanceRepository;
import com.example.dbagg.util.RowsUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Читает данные одной таблицы-источника напрямую из целевой БД по JDBC, применяя проекцию колонок и
 * продавленный предикат; типы маппятся в типы внутреннего PostgreSQL через коннектор источника.
 */
@ApplicationScoped
public class SourceReader {
  /** Защита от вычитывания слишком больших таблиц. */
  static final int MAX_SOURCE_ROWS = 500_000;

  private final InstanceRepository instanceRepository;
  private final ConnectorRegistry connectorRegistry;
  private final InstanceLookup instanceLookup;

  @Inject
  public SourceReader(
      InstanceRepository instanceRepository,
      ConnectorRegistry connectorRegistry,
      InstanceLookup instanceLookup) {
    this.instanceRepository = instanceRepository;
    this.connectorRegistry = connectorRegistry;
    this.instanceLookup = instanceLookup;
  }

  public SourceData read(SourcePlan plan) {
    var table = plan.resolved();
    var instance = instanceLookup.require(table.instanceId());
    var password = instanceRepository.decryptPassword(table.instanceId());
    var connector = connectorRegistry.forInstance(instance);
    var sql =
        buildSql(
            connector.tableReference(
                table.schemaName(), table.tableName(), plan.ref().tableAlias()),
            plan);

    try (var conn = SourceConnection.openReadOnly(instance, password);
        var statement = conn.createStatement();
        var rs = statement.executeQuery(sql)) {
      var meta = rs.getMetaData();
      var columns = readColumns(meta, connector);
      var rows = new ArrayList<List<Object>>();
      while (rs.next()) {
        if (rows.size() >= MAX_SOURCE_ROWS) {
          throw new IllegalStateException(
              "Source table is too large for a federated query: " + sql);
        }
        var row = new ArrayList<Object>(columns.size());
        for (int c = 1; c <= columns.size(); c++) {
          row.add(normalize(rs.getObject(c)));
        }
        rows.add(Collections.unmodifiableList(row));
      }
      return new SourceData(List.copyOf(columns), List.copyOf(rows));
    } catch (SQLException e) {
      throw new IllegalStateException(
          "Failed to read source table "
              + table.schemaName()
              + "."
              + table.tableName()
              + ": "
              + e.getMessage(),
          e);
    }
  }

  private String buildSql(String tableReference, SourcePlan plan) {
    var selectList =
        plan.columns().isEmpty()
            ? "*"
            : plan.columns().stream().map(RowsUtil::quoteIdent).collect(Collectors.joining(", "));
    var builder =
        new StringBuilder("SELECT ").append(selectList).append(" FROM ").append(tableReference);
    if (plan.predicate() != null) {
      builder.append(" WHERE (").append(plan.predicate()).append(')');
    }
    return builder.toString();
  }

  private List<ColumnInfo> readColumns(ResultSetMetaData meta, SourceConnector connector)
      throws SQLException {
    var columns = new ArrayList<ColumnInfo>(meta.getColumnCount());
    for (int c = 1; c <= meta.getColumnCount(); c++) {
      columns.add(
          new ColumnInfo(meta.getColumnLabel(c), connector.mapTypeName(meta.getColumnTypeName(c))));
    }
    return columns;
  }

  private static Object normalize(Object value) throws SQLException {
    if (value instanceof Clob clob) {
      return clob.getSubString(1, (int) clob.length());
    }
    if (value instanceof Blob blob) {
      return blob.getBytes(1, (int) blob.length());
    }
    if (value instanceof SQLXML sqlxml) {
      return sqlxml.getString();
    }
    return value;
  }
}
