package com.example.dbagg.model;

import java.util.Objects;

/**
 * Ссылка на таблицу-источник в федеративном запросе вида {@code instance.schema.table}.
 *
 * @param instanceName название инстанса (может быть {@code null}, если не указан)
 * @param schemaName имя схемы (может быть {@code null})
 * @param tableName имя таблицы
 * @param tableAlias SQL-алиас таблицы в запросе (может быть {@code null})
 */
public record TableRef(
    String instanceName, String schemaName, String tableName, String tableAlias) {
  public TableRef {
    Objects.requireNonNull(tableName, "tableName");
    if (tableName.isBlank()) {
      throw new IllegalArgumentException("tableName must not be blank");
    }
  }

  /** Канонический ключ {@code instance.schema.table} для сопоставления с AST. */
  public String canonical() {
    return canonical(instanceName, schemaName, tableName);
  }

  public static String canonical(String instanceName, String schemaName, String tableName) {
    return nullToEmpty(instanceName) + "." + nullToEmpty(schemaName) + "." + tableName;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
