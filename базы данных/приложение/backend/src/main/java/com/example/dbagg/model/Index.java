package com.example.dbagg.model;

import java.util.List;
import java.util.Objects;

/**
 * Индекс таблицы.
 *
 * @param definition полное определение индекса (в т.ч. выражения/условие partial)
 * @param columns колонки индекса с порядком
 */
public record Index(
    long id,
    long tableId,
    String indexName,
    boolean unique,
    boolean primary,
    IndexKind kind,
    String definition,
    List<IndexColumn> columns) {
  public Index {
    Objects.requireNonNull(indexName, "indexName");
    Objects.requireNonNull(kind, "kind");
    columns = columns == null ? List.of() : List.copyOf(columns);
  }
}
