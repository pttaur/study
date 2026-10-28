package com.example.dbagg.model;

import java.util.List;
import java.util.Objects;

/** Результат сравнения двух каталогов схем. */
public record DiffResult(long baseInstanceId, long compareInstanceId, List<SchemaDiff> schemas) {
  public DiffResult {
    Objects.requireNonNull(schemas, "schemas");
    schemas = List.copyOf(schemas);
  }
}
