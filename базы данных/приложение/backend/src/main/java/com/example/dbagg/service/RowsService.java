package com.example.dbagg.service;

import com.example.dbagg.model.RowGrid;
import com.example.dbagg.repository.InstanceRepository;
import com.example.dbagg.util.RowsUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Сервис строк целевой БД: живая постраничная выборка для DataGrid-эксплорера. */
@ApplicationScoped
public class RowsService {
  private static final Logger log = LoggerFactory.getLogger(RowsService.class);

  private static final int DEFAULT_PAGE_LIMIT = 100;
  private static final int MAX_PAGE_LIMIT = 200;

  private final InstanceRepository instanceRepository;
  private final InstanceLookup instanceLookup;

  @Inject
  public RowsService(InstanceRepository instanceRepository, InstanceLookup instanceLookup) {
    this.instanceRepository = instanceRepository;
    this.instanceLookup = instanceLookup;
  }

  /**
   * Живая выборка строк таблицы из целевой БД для DataGrid.
   *
   * @param instanceId идентификатор инстанса
   * @param schemaName схема
   * @param tableName таблица
   * @param limit размер страницы (≤0 → 100)
   * @param offset смещение
   * @return сетка строк {@link RowGrid}
   */
  public RowGrid liveRows(
      long instanceId, String schemaName, String tableName, int limit, int offset) {
    var instance = instanceLookup.require(instanceId);
    var password = instanceRepository.decryptPassword(instanceId);
    var effectiveLimit = bounded(limit, DEFAULT_PAGE_LIMIT);
    var columns = new ArrayList<String>();
    var rows = new ArrayList<List<Object>>();
    var table = RowsUtil.quotedTable(schemaName, tableName);
    long total;

    try (var conn = SourceConnection.openReadOnly(instance, password)) {
      var orderBy = primaryKeyOrderBy(conn, schemaName, tableName);
      try (var st = conn.createStatement()) {
        st.setFetchSize(Math.min(effectiveLimit, MAX_PAGE_LIMIT));
        try (var rs =
            st.executeQuery(
                "SELECT * FROM "
                    + table
                    + orderBy
                    + " LIMIT "
                    + effectiveLimit
                    + " OFFSET "
                    + offset)) {
          var md = rs.getMetaData();
          for (int c = 1; c <= md.getColumnCount(); c++) {
            columns.add(md.getColumnLabel(c));
          }
          while (rs.next()) {
            var row = new ArrayList<Object>(columns.size());
            for (int c = 1; c <= columns.size(); c++) {
              row.add(rs.getObject(c));
            }
            rows.add(Collections.unmodifiableList(row));
          }
        }
        try (var rs = st.executeQuery("SELECT count(*) FROM " + table)) {
          rs.next();
          total = rs.getLong(1);
        }
      }
    } catch (SQLException e) {
      log.warn("Failed to read live rows for {} (instance {})", table, instanceId);
      throw new IllegalStateException("Failed to read live rows for " + table, e);
    }

    return new RowGrid(List.copyOf(columns), List.copyOf(rows), total);
  }

  /**
   * Детерминированный {@code ORDER BY} по первичному ключу целевой таблицы, чтобы страницы не
   * пересекались и не пропускали строки. Если PK нет — порядок задаёт БД.
   */
  private String primaryKeyOrderBy(Connection conn, String schemaName, String tableName)
      throws SQLException {
    var columns = new TreeMap<Short, String>();
    try (var rs = conn.getMetaData().getPrimaryKeys(null, schemaName, tableName)) {
      while (rs.next()) {
        columns.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
      }
    }
    if (columns.isEmpty()) {
      return "";
    }
    return " ORDER BY "
        + columns.values().stream().map(RowsUtil::quoteIdent).collect(Collectors.joining(", "));
  }

  private int bounded(int limit, int fallback) {
    if (limit <= 0) {
      return fallback;
    }
    return Math.min(limit, MAX_PAGE_LIMIT);
  }
}
