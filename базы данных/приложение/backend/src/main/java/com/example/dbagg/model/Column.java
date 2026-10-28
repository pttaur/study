package com.example.dbagg.model;

import java.util.Objects;

public record Column(
    long id,
    long tableId,
    String columnName,
    int ordinalPosition,
    String dataType,
    boolean nullable,
    String defaultValue,
    boolean generated) {
  public Column {
    Objects.requireNonNull(columnName, "columnName");
    Objects.requireNonNull(dataType, "dataType");
    if (ordinalPosition <= 0) {
      throw new IllegalArgumentException("ordinalPosition must be positive");
    }
  }
}
