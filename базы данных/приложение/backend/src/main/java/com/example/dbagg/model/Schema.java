package com.example.dbagg.model;

import java.util.List;
import java.util.Objects;

public record Schema(
    long id, long instanceId, String schemaName, String owner, String comment, List<Table> tables) {
  public Schema {
    Objects.requireNonNull(schemaName, "schemaName");
    tables = List.copyOf(tables);
  }
}
