package com.example.dbagg.repository;

import com.example.dbagg.model.QueryHistoryEntry;
import com.example.dbagg.util.SqlQueries;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

/** История федеративных запросов (таблица {@code sc_query_history}). */
@ApplicationScoped
public class QueryHistoryRepository {
  private final DSLContext jooq;

  @Inject
  public QueryHistoryRepository(@DataSource("storage") javax.sql.DataSource storageDataSource) {
    this.jooq = DSL.using(storageDataSource, SQLDialect.POSTGRES);
  }

  public void saveQueryHistory(
      String sql, String status, Long rowCount, long durationMs, String error) {
    jooq.execute(
        DSL.sql(SqlQueries.INSERT_QUERY_HISTORY, sql, status, rowCount, durationMs, error));
  }

  public List<QueryHistoryEntry> findQueryHistory(int limit) {
    var rows = jooq.fetch(DSL.sql(SqlQueries.FIND_QUERY_HISTORY, limit));
    var history = new ArrayList<QueryHistoryEntry>(rows.size());
    for (var row : rows) {
      history.add(
          new QueryHistoryEntry(
              row.get("id", Long.class),
              row.get("sql_text", String.class),
              row.get("status", String.class),
              row.get("row_count", Long.class),
              row.get("duration_ms", Long.class),
              row.get("error", String.class),
              String.valueOf(row.get("created_at", OffsetDateTime.class))));
    }
    return history;
  }
}
