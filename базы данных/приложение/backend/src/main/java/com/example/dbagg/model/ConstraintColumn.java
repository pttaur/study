package com.example.dbagg.model;

import java.util.Objects;

/**
 * Колонка ограничения (составной ключ) с порядком.
 *
 * @param columnName имя колонки в таблице ограничения
 * @param position порядок в ключе (1-based)
 * @param refColumnName целевая колонка для FK (иначе {@code null})
 */
public record ConstraintColumn(String columnName, int position, String refColumnName) {
  public ConstraintColumn {
    Objects.requireNonNull(columnName, "columnName");
    if (position <= 0) {
      throw new IllegalArgumentException("position must be > 0");
    }
  }
}
