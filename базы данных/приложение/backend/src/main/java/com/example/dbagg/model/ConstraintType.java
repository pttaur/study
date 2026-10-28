package com.example.dbagg.model;

public enum ConstraintType {
  PRIMARY_KEY("p"),
  FOREIGN_KEY("f"),
  UNIQUE("u"),
  CHECK("c"),
  EXCLUDE("x");

  private final String contype;

  ConstraintType(String contype) {
    this.contype = contype;
  }

  public String contype() {
    return contype;
  }

  public static ConstraintType fromContype(String contype) {
    for (var type : values()) {
      if (type.contype.equals(contype)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown pg_catalog contype: " + contype);
  }
}
