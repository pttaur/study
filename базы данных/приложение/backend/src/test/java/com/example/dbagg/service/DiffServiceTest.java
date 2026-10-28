package com.example.dbagg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.dbagg.model.Catalog;
import com.example.dbagg.model.Column;
import com.example.dbagg.model.Constraint;
import com.example.dbagg.model.ConstraintColumn;
import com.example.dbagg.model.ConstraintType;
import com.example.dbagg.model.DiffType;
import com.example.dbagg.model.Index;
import com.example.dbagg.model.IndexColumn;
import com.example.dbagg.model.IndexKind;
import com.example.dbagg.model.Schema;
import com.example.dbagg.model.Table;
import com.example.dbagg.model.TableKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiffServiceTest {
  @Test
  void compareThroughCacheUsesStoredCatalogs() {
    var cache = new CacheProvider();
    var service = new DiffService(cache);
    cache.put(1L, catalog("base", "orders"));
    cache.put(2L, catalog("compare", "products"));

    var result = service.compare(1L, 2L);

    assertThat(result.baseInstanceId()).isEqualTo(1L);
    assertThat(result.compareInstanceId()).isEqualTo(2L);
    assertThat(result.schemas()).extracting(s -> s.name()).containsExactly("public");
    assertThat(result.schemas().get(0).type()).isEqualTo(DiffType.MODIFIED);
    assertThat(result.schemas().get(0).tables())
        .extracting(t -> t.name())
        .containsExactlyInAnyOrder("orders", "products");
  }

  @Test
  void compareThrowsWhenCatalogNotCached() {
    var service = new DiffService(new CacheProvider());

    assertThatThrownBy(() -> service.compare(1L, 2L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not cached");
  }

  @Test
  void identicalCatalogsProduceNoDiffs() {
    var base = catalog("base", "orders");
    var compare = catalog("base", "orders");

    var result = new DiffService(new CacheProvider()).compare(base, compare);

    assertThat(result.schemas()).isEmpty();
  }

  @Test
  void emptyCatalogsProduceNoDiffs() {
    var empty = new Catalog(1L, List.of());

    var result = new DiffService(new CacheProvider()).compare(empty, empty);

    assertThat(result.schemas()).isEmpty();
  }

  @Test
  void addedTableIsReportedAtTableLevel() {
    var base = new Catalog(1L, List.of());
    var compare =
        new Catalog(
            2L,
            List.of(
                new Schema(0, 2, "public", "postgres", null, List.of(table("orders", "bigint")))));

    var result = new DiffService(new CacheProvider()).compare(base, compare);

    var rowDiff = result.schemas().get(0).tables().get(0);
    assertThat(rowDiff.type()).isEqualTo(DiffType.ADDED);
    assertThat(rowDiff.columns()).isEmpty();
  }

  @Test
  void removedSchemaReportsItsTablesAsRemoved() {
    var base =
        new Catalog(
            1L,
            List.of(
                new Schema(0, 1, "public", "postgres", null, List.of(table("orders", "bigint")))));
    var compare = new Catalog(2L, List.of());

    var result = new DiffService(new CacheProvider()).compare(base, compare);

    assertThat(result.schemas()).hasSize(1);
    assertThat(result.schemas().get(0).type()).isEqualTo(DiffType.REMOVED);
    assertThat(result.schemas().get(0).tables())
        .extracting(t -> t.name())
        .containsExactly("orders");
    assertThat(result.schemas().get(0).tables().get(0).type()).isEqualTo(DiffType.REMOVED);
  }

  @Test
  void removedColumnIsReported() {
    var base =
        new Catalog(
            1L,
            List.of(
                new Schema(0, 1, "public", "postgres", null, List.of(table("orders", "bigint")))));
    var compare =
        new Catalog(
            2L,
            List.of(
                new Schema(
                    0,
                    2,
                    "public",
                    "postgres",
                    null,
                    List.of(
                        new Table(
                            0, 0, "orders", TableKind.TABLE, List.of(), List.of(), List.of())))));

    var result = new DiffService(new CacheProvider()).compare(base, compare);

    var tableDiff = result.schemas().get(0).tables().get(0);
    assertThat(tableDiff.type()).isEqualTo(DiffType.MODIFIED);
    assertThat(tableDiff.columns()).extracting(c -> c.name()).containsExactly("id");
    assertThat(tableDiff.columns().get(0).type()).isEqualTo(DiffType.REMOVED);
  }

  @Test
  void identicalTablesButDifferentSchemaOwnerReported() {
    var base =
        new Catalog(
            1L,
            List.of(new Schema(0, 1, "public", "alice", null, List.of(table("orders", "bigint")))));
    var compare =
        new Catalog(
            2L,
            List.of(new Schema(0, 2, "public", "bob", null, List.of(table("orders", "bigint")))));

    var result = new DiffService(new CacheProvider()).compare(base, compare);

    assertThat(result.schemas()).hasSize(1);
    assertThat(result.schemas().get(0).type()).isEqualTo(DiffType.MODIFIED);
    assertThat(result.schemas().get(0).tables()).isEmpty();
  }

  @Test
  void tableKindChangeIsReported() {
    var base =
        new Catalog(
            1L,
            List.of(
                new Schema(
                    0,
                    1,
                    "public",
                    "postgres",
                    null,
                    List.of(
                        new Table(
                            0, 0, "orders", TableKind.TABLE, List.of(), List.of(), List.of())))));
    var compare =
        new Catalog(
            2L,
            List.of(
                new Schema(
                    0,
                    2,
                    "public",
                    "postgres",
                    null,
                    List.of(
                        new Table(
                            0, 0, "orders", TableKind.VIEW, List.of(), List.of(), List.of())))));

    var result = new DiffService(new CacheProvider()).compare(base, compare);

    var tableDiff = result.schemas().get(0).tables().get(0);
    assertThat(tableDiff.type()).isEqualTo(DiffType.MODIFIED);
  }

  @Test
  void modifiedColumnIsReported() {
    var base =
        new Catalog(
            1L,
            List.of(
                new Schema(0, 1, "public", "postgres", null, List.of(table("orders", "bigint")))));
    var compare =
        new Catalog(
            2L,
            List.of(
                new Schema(0, 2, "public", "postgres", null, List.of(table("orders", "text")))));

    var result = new DiffService(new CacheProvider()).compare(base, compare);

    var tableDiff = result.schemas().get(0).tables().get(0);
    assertThat(tableDiff.type()).isEqualTo(DiffType.MODIFIED);
    assertThat(tableDiff.columns()).extracting(c -> c.name()).containsExactly("id");
    assertThat(tableDiff.columns().get(0).type()).isEqualTo(DiffType.MODIFIED);
  }

  private Catalog catalog(String schemaComment, String tableName) {
    return new Catalog(
        0L,
        List.of(
            new Schema(
                0, 0, "public", "postgres", schemaComment, List.of(table(tableName, "bigint")))));
  }

  private Table table(String name, String dataType) {
    return new Table(
        0,
        0,
        name,
        TableKind.TABLE,
        List.of(new Column(0, 0, "id", 1, dataType, false, "nextval('seq')", false)),
        List.of(
            new Constraint(
                0,
                0,
                name + "_pkey",
                ConstraintType.PRIMARY_KEY,
                null,
                null,
                null,
                null,
                List.of(new ConstraintColumn("id", 1, null)))),
        List.of(
            new Index(
                0,
                0,
                name + "_pkey",
                true,
                true,
                IndexKind.BTREE,
                "CREATE UNIQUE INDEX ...",
                List.of(new IndexColumn("id", 1)))));
  }
}
