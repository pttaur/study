package com.example.dbagg.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ConstraintTypeTest {
  @ParameterizedTest
  @CsvSource({"p,PRIMARY_KEY", "f,FOREIGN_KEY", "u,UNIQUE", "c,CHECK", "x,EXCLUDE"})
  void mapsPgCatalogContype(String contype, ConstraintType expected) {
    assertThat(ConstraintType.fromContype(contype)).isEqualTo(expected);
  }

  @Test
  void roundTripsToContype() {
    for (var type : ConstraintType.values()) {
      assertThat(ConstraintType.fromContype(type.contype())).isEqualTo(type);
    }
  }

  @Test
  void rejectsUnknownContype() {
    assertThatThrownBy(() -> ConstraintType.fromContype("t"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("t");
  }
}
