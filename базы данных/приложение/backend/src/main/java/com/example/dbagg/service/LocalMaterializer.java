package com.example.dbagg.service;

import com.example.dbagg.model.ColumnInfo;
import com.example.dbagg.model.SourceData;
import com.example.dbagg.util.RowsUtil;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Загружает прочитанные данные источника во временную таблицу внутреннего PostgreSQL, чтобы
 * выполнить джойн силами нашего хранилища.
 */
@ApplicationScoped
public class LocalMaterializer {
  private static final int BATCH_SIZE = 1000;

  public void materialize(Connection conn, String tempName, SourceData data) throws SQLException {
    if (data.columns().isEmpty()) {
      throw new IllegalArgumentException("Source table has no columns: " + tempName);
    }
    createTempTable(conn, tempName, data.columns());
    insertRows(conn, tempName, data.columns(), data.rows());
  }

  private void createTempTable(Connection conn, String tempName, List<ColumnInfo> columns)
      throws SQLException {
    var sql =
        new StringBuilder("CREATE TEMP TABLE ").append(RowsUtil.quoteIdent(tempName)).append(" (");
    for (int i = 0; i < columns.size(); i++) {
      if (i > 0) {
        sql.append(", ");
      }
      var column = columns.get(i);
      sql.append(RowsUtil.quoteIdent(column.name())).append(' ').append(column.type());
    }
    sql.append(") ON COMMIT PRESERVE ROWS");
    try (var statement = conn.createStatement()) {
      statement.execute(sql.toString());
    }
  }

  private void insertRows(
      Connection conn, String tempName, List<ColumnInfo> columns, List<List<Object>> rows)
      throws SQLException {
    if (rows.isEmpty()) {
      return;
    }
    var sql = new StringBuilder("INSERT INTO ").append(RowsUtil.quoteIdent(tempName)).append(" (");
    for (int i = 0; i < columns.size(); i++) {
      if (i > 0) {
        sql.append(", ");
      }
      sql.append(RowsUtil.quoteIdent(columns.get(i).name()));
    }
    sql.append(") VALUES (");
    for (int i = 0; i < columns.size(); i++) {
      if (i > 0) {
        sql.append(", ");
      }
      sql.append('?');
    }
    sql.append(')');

    try (var statement = conn.prepareStatement(sql.toString())) {
      var batched = 0;
      for (var row : rows) {
        for (int i = 0; i < columns.size(); i++) {
          statement.setObject(i + 1, row.get(i));
        }
        statement.addBatch();
        if (++batched % BATCH_SIZE == 0) {
          statement.executeBatch();
        }
      }
      statement.executeBatch();
    }
  }
}
