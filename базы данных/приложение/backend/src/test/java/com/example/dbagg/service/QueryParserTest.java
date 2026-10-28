package com.example.dbagg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QueryParserTest {
  private final QueryParser parser = new QueryParser();

  @Test
  void parsesSingleTableWithInstanceName() {
    var query = parser.parse("SELECT * FROM prod_pg.public.users");

    assertThat(query.tables()).hasSize(1);
    var ref = query.tables().get(0);
    assertThat(ref.instanceName()).isEqualTo("prod_pg");
    assertThat(ref.schemaName()).isEqualTo("public");
    assertThat(ref.tableName()).isEqualTo("users");
    assertThat(query.joinCount()).isZero();
  }

  @Test
  void parsesJoinAcrossInstances() {
    var query =
        parser.parse(
            "SELECT u.id, o.total FROM prod.public.users u "
                + "JOIN sales.public.orders o ON o.user_id = u.id WHERE u.active = true");

    assertThat(query.tables()).hasSize(2);
    assertThat(query.tables())
        .extracting(t -> t.instanceName() + "." + t.schemaName() + "." + t.tableName())
        .containsExactly("prod.public.users", "sales.public.orders");
    assertThat(query.tables().get(0).tableAlias()).isEqualTo("u");
    assertThat(query.joinCount()).isEqualTo(1);
    assertThat(query.hasWhere()).isTrue();
  }

  @Test
  void capturesLimitGroupByAndOrderBy() {
    var query =
        parser.parse("SELECT u.id FROM prod.public.users u GROUP BY u.id ORDER BY u.id LIMIT 50");

    assertThat(query.hasGroupBy()).isTrue();
    assertThat(query.hasOrderBy()).isTrue();
    assertThat(query.limit()).isEqualTo(50L);
  }

  @Test
  void acceptsSchemaQualifiedAndPlainNames() {
    assertThat(parser.parse("SELECT * FROM public.users").tables().get(0).schemaName())
        .isEqualTo("public");
    assertThat(parser.parse("SELECT * FROM users").tables().get(0).instanceName()).isNull();
    assertThat(parser.parse("SELECT * FROM users").tables().get(0).tableName()).isEqualTo("users");
  }

  @Test
  void rejectsNonSelectStatements() {
    assertThatThrownBy(() -> parser.parse("INSERT INTO t (id) VALUES (1)"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SELECT");
  }

  @Test
  void rejectsMultipleStatements() {
    assertThatThrownBy(() -> parser.parse("SELECT * FROM a; SELECT * FROM b"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("single statement");
  }

  @Test
  void rejectsSubqueryInFrom() {
    assertThatThrownBy(() -> parser.parse("SELECT * FROM (SELECT 1) t"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Subqueries");
  }

  @Test
  void rejectsCte() {
    assertThatThrownBy(() -> parser.parse("WITH x AS (SELECT 1) SELECT * FROM x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CTE");
  }

  @Test
  void rejectsBlankSql() {
    assertThatThrownBy(() -> parser.parse("   "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blank");
  }
}
