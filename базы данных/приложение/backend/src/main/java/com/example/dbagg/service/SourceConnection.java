package com.example.dbagg.service;

import com.example.dbagg.model.Instance;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Единая точка открытия соединения к целевой БД в режиме «только чтение».
 *
 * <p>Помимо {@link Connection#setReadOnly(boolean)} выставляется явный {@code
 * default_transaction_read_only} на уровне сессии PostgreSQL/SQL Server; для диалектов без
 * поддержки настройка игнорируется, а {@code setReadOnly} остаётся сигналом драйверу.
 */
final class SourceConnection {
  private SourceConnection() {}

  static Connection openReadOnly(Instance instance, String password) throws SQLException {
    var conn = DriverManager.getConnection(instance.connectionUrl(), instance.username(), password);
    conn.setReadOnly(true);
    try (var statement = conn.createStatement()) {
      statement.execute("SET default_transaction_read_only = on");
    } catch (SQLException ignored) {
      // Диалект без такой настройки (напр. MariaDB): достаточно setReadOnly.
    }
    return conn;
  }
}
