package com.example.dbagg.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TableKindTest {
  @ParameterizedTest
  @CsvSource({"r,TABLE", "p,PARTITIONED_TABLE", "v,VIEW", "m,MATERIALIZED_VIEW", "f,FOREIGN_TABLE"})
  void mapsPgCatalogRelkind(String relkind, TableKind expected) {
    assertThat(TableKind.fromRelkind(relkind)).isEqualTo(expected);
  }

  @Test
  void roundTripsToRelkind() {
    for (var kind : TableKind.values()) {
      assertThat(TableKind.fromRelkind(kind.relkind())).isEqualTo(kind);
    }
  }

  @Test
  void rejectsUnknownRelkind() {
    assertThatThrownBy(() -> TableKind.fromRelkind("s"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("s");
  }
}
