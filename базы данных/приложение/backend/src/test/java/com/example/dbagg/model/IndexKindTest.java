package com.example.dbagg.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IndexKindTest {
  @ParameterizedTest
  @CsvSource({"btree,BTREE", "hash,HASH", "gin,GIN", "gist,GIST", "spgist,SPGIST", "brin,BRIN"})
  void mapsPgCatalogAmname(String amname, IndexKind expected) {
    assertThat(IndexKind.fromAmname(amname)).isEqualTo(expected);
  }

  @Test
  void roundTripsToAmname() {
    for (var kind : IndexKind.values()) {
      assertThat(IndexKind.fromAmname(kind.amname())).isEqualTo(kind);
    }
  }

  @Test
  void rejectsUnknownAmname() {
    assertThatThrownBy(() -> IndexKind.fromAmname("bloom"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("bloom");
  }
}
