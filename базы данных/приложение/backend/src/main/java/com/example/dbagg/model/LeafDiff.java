package com.example.dbagg.model;

import java.util.Objects;

/** Отличие одного листового элемента (колонка/ограничение/индекс). */
public record LeafDiff(String name, DiffType type) {
  public LeafDiff {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(type, "type");
  }
}
