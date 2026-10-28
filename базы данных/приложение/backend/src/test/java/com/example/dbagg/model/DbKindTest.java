package com.example.dbagg.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DbKindTest {
  @Test
  void parsesCaseInsensitively() {
    assertThat(DbKind.fromCode("mariadb")).isEqualTo(DbKind.MARIADB);
    assertThat(DbKind.fromCode("MariaDB")).isEqualTo(DbKind.MARIADB);
    assertThat(DbKind.fromCode("POSTGRES")).isEqualTo(DbKind.POSTGRES);
  }

  @Test
  void defaultsToPostgres() {
    assertThat(DbKind.fromCode(null)).isEqualTo(DbKind.POSTGRES);
    assertThat(DbKind.fromCode("  ")).isEqualTo(DbKind.POSTGRES);
  }

  @Test
  void rejectsUnsupportedKind() {
    assertThatThrownBy(() -> DbKind.fromCode("MYSQL"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MYSQL");
  }
}
