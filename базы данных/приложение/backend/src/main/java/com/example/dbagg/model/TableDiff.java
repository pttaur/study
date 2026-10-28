package com.example.dbagg.model;

import java.util.List;
import java.util.Objects;

/** Отличие таблицы: изменены сама таблица и/или её колонки, ограничения, индексы. */
public record TableDiff(
    String name,
    DiffType type,
    List<LeafDiff> columns,
    List<LeafDiff> constraints,
    List<LeafDiff> indexes) {
  public TableDiff {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    columns = List.copyOf(columns);
    constraints = List.copyOf(constraints);
    indexes = List.copyOf(indexes);
  }
}
