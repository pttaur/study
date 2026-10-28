package com.example.dbagg.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RowsUtilTest {
  @Test
  void quotesIdentifiers() {
    assertThat(RowsUtil.quoteIdent("users")).isEqualTo("\"users\"");
    assertThat(RowsUtil.quoteIdent("we\"ird")).isEqualTo("\"we\"\"ird\"");
  }

  @Test
  void buildsQualifiedTableName() {
    assertThat(RowsUtil.quotedTable("public", "users")).isEqualTo("\"public\".\"users\"");
  }
}
