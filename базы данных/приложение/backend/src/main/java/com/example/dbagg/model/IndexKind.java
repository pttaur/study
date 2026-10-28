package com.example.dbagg.model;

public enum IndexKind {
  BTREE("btree"),
  HASH("hash"),
  GIN("gin"),
  GIST("gist"),
  SPGIST("spgist"),
  BRIN("brin");

  private final String amname;

  IndexKind(String amname) {
    this.amname = amname;
  }

  public String amname() {
    return amname;
  }

  public static IndexKind fromAmname(String amname) {
    for (var kind : values()) {
      if (kind.amname.equals(amname)) {
        return kind;
      }
    }
    throw new IllegalArgumentException("Unknown pg_catalog amname: " + amname);
  }
}
