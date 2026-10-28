package com.example.dbagg.model;

public enum TableKind {
  TABLE("r"),
  PARTITIONED_TABLE("p"),
  VIEW("v"),
  MATERIALIZED_VIEW("m"),
  FOREIGN_TABLE("f");

  private final String relkind;

  TableKind(String relkind) {
    this.relkind = relkind;
  }

  public String relkind() {
    return relkind;
  }

  public static TableKind fromRelkind(String relkind) {
    for (var kind : values()) {
      if (kind.relkind.equals(relkind)) {
        return kind;
      }
    }
    throw new IllegalArgumentException("Unknown pg_catalog relkind: " + relkind);
  }
}
