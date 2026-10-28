package com.example.dbagg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.dbagg.model.JoinCondition;
import com.example.dbagg.model.JoinRequest;
import com.example.dbagg.model.JoinTableSpec;
import com.example.dbagg.model.JoinType;
import com.example.dbagg.model.SelectColumn;
import java.util.List;
import org.junit.jupiter.api.Test;

class JoinSqlBuilderTest {
  private final JoinSqlBuilder builder = new JoinSqlBuilder();

  private static JoinTableSpec base(String ref) {
    return new JoinTableSpec(ref, null, List.of());
  }

  private static JoinTableSpec join(String ref, JoinType type, JoinCondition... on) {
    return new JoinTableSpec(ref, type, List.of(on));
  }

  @Test
  void buildsInnerJoinWithQuotedIdentifiers() {
    var request =
        new JoinRequest(
            List.of(
                base("ops.public.employee"),
                join(
                    "ops.public.organization",
                    JoinType.INNER,
                    new JoinCondition(
                        "ops.public.organization",
                        "id",
                        "ops.public.employee",
                        "organization_id"))),
            List.of(
                new SelectColumn("ops.public.employee", "full_name"),
                new SelectColumn("ops.public.organization", "name")),
            null,
            null,
            null);

    assertThat(builder.build(request))
        .isEqualTo(
            "SELECT t0.\"full_name\" AS \"full_name\", t1.\"name\" AS \"name\" "
                + "FROM \"ops\".\"public\".\"employee\" t0 "
                + "INNER JOIN \"ops\".\"public\".\"organization\" t1 "
                + "ON t1.\"id\" = t0.\"organization_id\" "
                + "ORDER BY t0.\"full_name\", t1.\"name\"");
  }

  @Test
  void supportsAllJoinTypesAndMultipleConditions() {
    var request =
        new JoinRequest(
            List.of(
                base("a.s.t1"),
                join(
                    "b.s.t2",
                    JoinType.FULL,
                    new JoinCondition("b.s.t2", "k", "a.s.t1", "k"),
                    new JoinCondition("b.s.t2", "n", "a.s.t1", "n"))),
            List.of(new SelectColumn("a.s.t1", "k")),
            null,
            null,
            null);

    var sql = builder.build(request);
    assertThat(sql).contains("FULL JOIN \"b\".\"s\".\"t2\" t1");
    assertThat(sql).contains("ON t1.\"k\" = t0.\"k\" AND t1.\"n\" = t0.\"n\"");
  }

  @Test
  void prefixesDuplicateColumnLabels() {
    var request =
        new JoinRequest(
            List.of(
                base("a.s.t1"),
                join("b.s.t2", JoinType.LEFT, new JoinCondition("b.s.t2", "id", "a.s.t1", "id"))),
            List.of(new SelectColumn("a.s.t1", "id"), new SelectColumn("b.s.t2", "id")),
            null,
            null,
            null);

    var sql = builder.build(request);
    assertThat(sql).contains("t0.\"id\" AS \"t0_id\"");
    assertThat(sql).contains("t1.\"id\" AS \"t1_id\"");
  }

  @Test
  void rejectsEmptyRequest() {
    assertThatThrownBy(() -> builder.build(new JoinRequest(List.of(), List.of(), null, null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("At least one table");
  }

  @Test
  void rejectsMissingSelect() {
    assertThatThrownBy(
            () ->
                builder.build(
                    new JoinRequest(List.of(base("a.s.t1")), List.of(), null, null, null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Select at least one column");
  }

  @Test
  void rejectsJoinWithoutTypeOrConditions() {
    assertThatThrownBy(
            () ->
                builder.build(
                    new JoinRequest(
                        List.of(base("a.s.t1"), base("b.s.t2")),
                        List.of(new SelectColumn("a.s.t1", "k")),
                        null,
                        null,
                        null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Join type is required");
  }

  @Test
  void rejectsUnknownTableInSelect() {
    assertThatThrownBy(
            () ->
                builder.build(
                    new JoinRequest(
                        List.of(base("a.s.t1")),
                        List.of(new SelectColumn("x.s.t9", "k")),
                        null,
                        null,
                        null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown table in select");
  }

  @Test
  void rejectsDuplicateTable() {
    assertThatThrownBy(
            () ->
                builder.build(
                    new JoinRequest(
                        List.of(base("a.s.t1"), base("a.s.t1")),
                        List.of(new SelectColumn("a.s.t1", "k")),
                        null,
                        null,
                        null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Duplicate table");
  }

  @Test
  void rejectsMalformedReference() {
    assertThatThrownBy(
            () ->
                builder.build(
                    new JoinRequest(
                        List.of(base("public.t1")),
                        List.of(new SelectColumn("public.t1", "k")),
                        null,
                        null,
                        null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("instance.schema.table");
  }
}
