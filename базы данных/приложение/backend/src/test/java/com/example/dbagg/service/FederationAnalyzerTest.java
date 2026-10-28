package com.example.dbagg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.dbagg.model.FederationPlan;
import com.example.dbagg.model.ResolvedTable;
import com.example.dbagg.model.SourcePlan;
import com.example.dbagg.model.TableRef;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class FederationAnalyzerTest {
  private final FederationAnalyzer analyzer = new FederationAnalyzer();

  private final Function<TableRef, ResolvedTable> resolver =
      ref -> {
        if (ref.tableName().equals("users")) {
          return new ResolvedTable(
              1L,
              ref.instanceName(),
              11L,
              ref.schemaName(),
              "users",
              List.of("id", "active", "email"));
        }
        if (ref.tableName().equals("orders")) {
          return new ResolvedTable(
              2L,
              ref.instanceName(),
              22L,
              ref.schemaName(),
              "orders",
              List.of("user_id", "total", "status"));
        }
        throw new IllegalArgumentException("Unknown table: " + ref.tableName());
      };

  private static SourcePlan source(FederationPlan plan, String table) {
    return plan.sources().stream()
        .filter(s -> s.ref().tableName().equals(table))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void pushesSingleTablePredicateAndPrunesColumns() {
    var sql =
        "SELECT u.id, o.total FROM prod.public.users u "
            + "JOIN sales.public.orders o ON o.user_id = u.id WHERE u.active = true";
    var plan = analyzer.analyze(sql, "t", resolver);

    var users = source(plan, "users");
    assertThat(users.columns()).containsExactly("id");
    assertThat(users.predicate()).isEqualTo("u.active = true");

    var orders = source(plan, "orders");
    assertThat(orders.columns()).containsExactlyInAnyOrder("user_id", "total");
    assertThat(orders.predicate()).isNull();

    assertThat(plan.outerSql()).doesNotContain("active");
    assertThat(plan.outerSql()).contains("o.user_id = u.id");
  }

  @Test
  void doesNotPushCrossTablePredicate() {
    var sql =
        "SELECT u.id FROM prod.public.users u "
            + "JOIN sales.public.orders o ON o.user_id = u.id WHERE u.id = o.user_id";
    var plan = analyzer.analyze(sql, "t", resolver);

    assertThat(source(plan, "users").predicate()).isNull();
    assertThat(plan.outerSql()).contains("u.id = o.user_id");
  }

  @Test
  void selectsAllColumnsForStar() {
    var plan = analyzer.analyze("SELECT * FROM prod.public.users u", "t", resolver);
    assertThat(source(plan, "users").columns()).isEmpty();
  }

  @Test
  void rejectsSubqueryReferencingNonSourceTable() {
    var sql =
        "SELECT u.id FROM prod.public.users u WHERE u.id IN "
            + "(SELECT o.user_id FROM sales.public.orders o)";

    assertThatThrownBy(() -> analyzer.analyze(sql, "t", resolver))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-source tables");
  }

  @Test
  void rejectsScalarSubqueryAgainstInternalStorage() {
    var sql =
        "SELECT (SELECT password_encrypted FROM public.sc_instances LIMIT 1) "
            + "FROM prod.public.users u";

    assertThatThrownBy(() -> analyzer.analyze(sql, "t", resolver))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("non-source tables");
  }

  @Test
  void doesNotPushPredicateWithSubqueryOnSourceTable() {
    var sql =
        "SELECT u.id FROM prod.public.users u WHERE u.id IN "
            + "(SELECT u2.id FROM prod.public.users u2)";
    var plan = analyzer.analyze(sql, "t", resolver);
    assertThat(source(plan, "users").predicate()).isNull();
  }
}
