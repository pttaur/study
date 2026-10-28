package com.example.dbagg.service;

import com.example.dbagg.model.ColumnInfo;
import com.example.dbagg.model.QueryHistoryEntry;
import com.example.dbagg.model.QueryRequest;
import com.example.dbagg.model.QueryResult;
import com.example.dbagg.repository.QueryHistoryRepository;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Оркестрация федеративного запроса: анализ и план → чтение источников → материализация во
 * временные таблицы внутреннего PostgreSQL → выполнение джойна.
 */
@ApplicationScoped
public class FederationService {
  private static final Logger log = LoggerFactory.getLogger(FederationService.class);

  private static final int DEFAULT_MAX_ROWS = 1000;
  private static final int MAX_MAX_ROWS = 100_000;
  private static final int DEFAULT_TIMEOUT_MS = 30_000;
  private static final int MAX_TIMEOUT_MS = 300_000;

  private final QueryParser parser;
  private final FederationAnalyzer analyzer;
  private final TableResolver tableResolver;
  private final SourceReader sourceReader;
  private final LocalMaterializer materializer;
  private final QueryHistoryRepository queryHistoryRepository;
  private final javax.sql.DataSource storageDataSource;

  @Inject
  public FederationService(
      QueryParser parser,
      FederationAnalyzer analyzer,
      TableResolver tableResolver,
      SourceReader sourceReader,
      LocalMaterializer materializer,
      QueryHistoryRepository queryHistoryRepository,
      @DataSource("storage") javax.sql.DataSource storageDataSource) {
    this.parser = parser;
    this.analyzer = analyzer;
    this.tableResolver = tableResolver;
    this.sourceReader = sourceReader;
    this.materializer = materializer;
    this.queryHistoryRepository = queryHistoryRepository;
    this.storageDataSource = storageDataSource;
  }

  public QueryResult execute(QueryRequest request) {
    var started = System.nanoTime();
    try {
      var result = doExecute(request);
      var durationMs = elapsedMs(started);
      recordHistory(request.sql(), "OK", result.totalRows(), durationMs, null);
      return new QueryResult(
          result.columns(),
          result.rows(),
          result.truncated(),
          result.offset(),
          result.totalRows(),
          durationMs);
    } catch (RuntimeException e) {
      recordHistory(request.sql(), "ERROR", null, elapsedMs(started), e.getMessage());
      throw e;
    }
  }

  public List<QueryHistoryEntry> history() {
    return queryHistoryRepository.findQueryHistory(50);
  }

  private QueryResult doExecute(QueryRequest request) {
    var maxRows = bounded(request.maxRows(), DEFAULT_MAX_ROWS, MAX_MAX_ROWS);
    var offset = Math.max(0, request.offset() == null ? 0 : request.offset());
    var timeoutMs = bounded(request.timeoutMs(), DEFAULT_TIMEOUT_MS, MAX_TIMEOUT_MS);
    var query = parser.parse(request.sql());
    if (query.tables().isEmpty()) {
      throw new IllegalArgumentException("Query must reference at least one table");
    }
    var plan =
        analyzer.analyze(
            request.sql(), Long.toUnsignedString(System.nanoTime(), 36), tableResolver::resolve);

    try (var conn = storageDataSource.getConnection()) {
      applyTimeout(conn, timeoutMs);
      for (var source : plan.sources()) {
        var data = sourceReader.read(source);
        materializer.materialize(conn, source.tempName(), data);
      }
      var outer = plan.outerSql();
      var total = countRows(conn, outer);
      return run(
          conn,
          "SELECT * FROM (" + outer + ") AS fed_result LIMIT " + maxRows + " OFFSET " + offset,
          maxRows,
          offset,
          total);
    } catch (SQLException e) {
      throw new IllegalStateException("Federated query failed: " + e.getMessage(), e);
    }
  }

  private long countRows(Connection conn, String outerSql) throws SQLException {
    try (var statement = conn.createStatement();
        var rs = statement.executeQuery("SELECT count(*) FROM (" + outerSql + ") AS fed_count")) {
      rs.next();
      return rs.getLong(1);
    }
  }

  private QueryResult run(Connection conn, String sql, int maxRows, int offset, long totalRows)
      throws SQLException {
    try (var statement = conn.createStatement();
        var rs = statement.executeQuery(sql)) {
      var meta = rs.getMetaData();
      var columns = new ArrayList<ColumnInfo>(meta.getColumnCount());
      for (int c = 1; c <= meta.getColumnCount(); c++) {
        columns.add(new ColumnInfo(meta.getColumnLabel(c), meta.getColumnTypeName(c)));
      }
      var rows = new ArrayList<List<Object>>();
      while (rs.next() && rows.size() < maxRows) {
        var row = new ArrayList<Object>(columns.size());
        for (int c = 1; c <= columns.size(); c++) {
          row.add(rs.getObject(c));
        }
        rows.add(Collections.unmodifiableList(row));
      }
      var truncated = offset + rows.size() < totalRows;
      return new QueryResult(List.copyOf(columns), rows, truncated, offset, totalRows, 0);
    }
  }

  private static void applyTimeout(Connection conn, int timeoutMs) throws SQLException {
    try (var statement = conn.createStatement()) {
      statement.execute("SET statement_timeout = " + timeoutMs);
    }
  }

  private void recordHistory(
      String sql, String status, Long rowCount, long durationMs, String error) {
    try {
      queryHistoryRepository.saveQueryHistory(sql, status, rowCount, durationMs, error);
    } catch (RuntimeException e) {
      log.warn("Failed to record query history: {}", e.getMessage());
    }
  }

  private static long elapsedMs(long startedNanos) {
    return (System.nanoTime() - startedNanos) / 1_000_000;
  }

  private static int bounded(Integer value, int fallback, int max) {
    if (value == null || value <= 0) {
      return fallback;
    }
    return Math.min(value, max);
  }
}
