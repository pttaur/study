package com.example.dbagg.util;

/**
 * Чистые утилиты для работы со строками таблиц: экранирование идентификаторов SQL. Не зависят от БД
 * и легко тестируются.
 */
public final class RowsUtil {
  private RowsUtil() {}

  /** Экранирует идентификатор для вставки в SQL в двойных кавычках. */
  public static String quoteIdent(String ident) {
    return "\"" + ident.replace("\"", "\"\"") + "\"";
  }

  /** Полное имя таблицы {@code "schema"."table"}. */
  public static String quotedTable(String schemaName, String tableName) {
    return quoteIdent(schemaName) + "." + quoteIdent(tableName);
  }
}
