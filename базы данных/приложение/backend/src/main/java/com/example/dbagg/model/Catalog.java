package com.example.dbagg.model;

import java.util.List;
import java.util.Optional;

public record Catalog(long instanceId, List<Schema> schemas) {
  public Catalog {
    schemas = List.copyOf(schemas);
  }

  public Optional<Schema> findSchema(String name) {
    return schemas.stream().filter(s -> s.schemaName().equals(name)).findFirst();
  }
}
