package com.example.dbagg.model;

import java.util.List;

/**
 * Таблица-источник, сопоставленная инстансу и записи каталога.
 *
 * @param instanceId идентификатор инстанса
 * @param instanceName название инстанса из запроса
 * @param tableId идентификатор таблицы в каталоге (sc_tables)
 * @param schemaName фактическое имя схемы
 * @param tableName фактическое имя таблицы
 * @param columns имена колонок таблицы (из каталога)
 */
public record ResolvedTable(
    long instanceId,
    String instanceName,
    long tableId,
    String schemaName,
    String tableName,
    List<String> columns) {
  public ResolvedTable {
    columns = List.copyOf(columns);
  }
}
