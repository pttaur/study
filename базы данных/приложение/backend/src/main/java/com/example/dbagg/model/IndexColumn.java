package com.example.dbagg.model;

import java.util.Objects;

/**
 * Колонка индекса с порядком.
 *
 * @param columnName имя колонки
 * @param position порядок в индексе (1-based)
 */
public record IndexColumn(String columnName, int position) {
  public IndexColumn {
    Objects.requireNonNull(columnName, "columnName");
    if (position <= 0) {
      throw new IllegalArgumentException("position must be > 0");
    }
  }
}
