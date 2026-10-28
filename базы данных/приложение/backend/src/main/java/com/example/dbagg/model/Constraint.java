package com.example.dbagg.model;

import java.util.List;
import java.util.Objects;

/**
 * Ограничение таблицы в нормализованном виде.
 *
 * @param refTable для FK — целевая таблица в виде {@code schema.table} (иначе {@code null})
 * @param expression тело CHECK/EXCLUDE (иначе {@code null})
 * @param onDelete/onUpdate правила ссылочной целостности FK
 * @param columns состав ключа с порядком (для KEY-ограничений)
 */
public record Constraint(
    long id,
    long tableId,
    String constraintName,
    ConstraintType type,
    String expression,
    String refTable,
    String onDelete,
    String onUpdate,
    List<ConstraintColumn> columns) {
  public Constraint {
    Objects.requireNonNull(constraintName, "constraintName");
    Objects.requireNonNull(type, "type");
    columns = columns == null ? List.of() : List.copyOf(columns);
    if (type == ConstraintType.FOREIGN_KEY) {
      Objects.requireNonNull(refTable, "refTable");
      onDelete = onDelete == null ? "NO_ACTION" : onDelete;
      onUpdate = onUpdate == null ? "NO_ACTION" : onUpdate;
      expression = null;
    } else {
      refTable = null;
      onDelete = null;
      onUpdate = null;
    }
    if ((type == ConstraintType.CHECK || type == ConstraintType.EXCLUDE) && expression == null) {
      throw new IllegalArgumentException("expression is required for " + type);
    }
  }
}
