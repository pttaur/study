package com.example.dbagg.model;

import java.util.Locale;

/** Поддерживаемые типы СУБД-источников. */
public enum DbKind {
  POSTGRES,
  MARIADB;

  public static DbKind fromCode(String code) {
    if (code == null || code.isBlank()) {
      return POSTGRES;
    }
    try {
      return valueOf(code.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unsupported DB kind: " + code, e);
    }
  }
}
