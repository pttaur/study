package com.example.dbagg.model;

import java.util.Objects;

/**
 * Условие соединения: {@code leftTable.leftColumn = rightTable.rightColumn}.
 *
 * @param leftTable ссылка на таблицу вида {@code instance.schema.table}
 * @param rightTable ссылка на таблицу вида {@code instance.schema.table}
 */
public record JoinCondition(
    String leftTable, String leftColumn, String rightTable, String rightColumn) {
  public JoinCondition {
    Objects.requireNonNull(leftTable, "leftTable");
    Objects.requireNonNull(leftColumn, "leftColumn");
    Objects.requireNonNull(rightTable, "rightTable");
    Objects.requireNonNull(rightColumn, "rightColumn");
  }
}
