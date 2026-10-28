package com.example.dbagg.model;

import java.util.List;
import java.util.Objects;

/** Отличие схемы: изменены сама схема и/или её таблицы. */
public record SchemaDiff(String name, DiffType type, List<TableDiff> tables) {
  public SchemaDiff {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
    tables = List.copyOf(tables);
  }
}
