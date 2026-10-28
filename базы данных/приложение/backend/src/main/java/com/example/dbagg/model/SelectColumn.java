package com.example.dbagg.model;

import java.util.Objects;

/**
 * Колонка результата.
 *
 * @param table ссылка на таблицу вида {@code instance.schema.table}
 * @param column имя колонки
 */
public record SelectColumn(String table, String column) {
  public SelectColumn {
    Objects.requireNonNull(table, "table");
    Objects.requireNonNull(column, "column");
  }
}
