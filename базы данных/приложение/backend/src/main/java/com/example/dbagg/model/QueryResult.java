package com.example.dbagg.model;

import java.util.List;
import java.util.Objects;

/**
 * Страница результата федеративного запроса.
 *
 * @param columns описание колонок
 * @param rows строки значений страницы
 * @param truncated true, если в таблице есть строки за пределами страницы
 * @param offset смещение первой строки страницы
 * @param totalRows общее число строк результата
 * @param durationMs время выполнения в мс
 */
public record QueryResult(
    List<ColumnInfo> columns,
    List<List<Object>> rows,
    boolean truncated,
    int offset,
    long totalRows,
    long durationMs) {
  public QueryResult {
    Objects.requireNonNull(columns, "columns");
    rows = List.copyOf(rows);
  }
}
