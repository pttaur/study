package com.example.dbagg.model;

import java.util.List;
import java.util.Objects;

public record Table(
    long id,
    long schemaId,
    String tableName,
    TableKind kind,
    List<Column> columns,
    List<Constraint> constraints,
    List<Index> indexes) {
  public Table {
    Objects.requireNonNull(tableName, "tableName");
    Objects.requireNonNull(kind, "kind");
    columns = List.copyOf(columns);
    constraints = List.copyOf(constraints);
    indexes = List.copyOf(indexes);
  }
}
