package com.example.dbagg.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dbagg.model.Catalog;
import com.example.dbagg.model.Schema;
import com.example.dbagg.model.Table;
import com.example.dbagg.model.TableKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class TableResolverTest {
  private final Catalog catalog =
      new Catalog(
          7L,
          List.of(
              new Schema(1L, 7L, "public", null, null, List.of(table(11L, 1L, "users"))),
              new Schema(2L, 7L, "audit", null, null, List.of(table(21L, 2L, "events")))));

  @Test
  void findsTableBySchemaAndName() {
    var found = TableResolver.findTable(catalog, "public", "users");

    assertThat(found).isPresent();
    assertThat(found.get().table().id()).isEqualTo(11L);
    assertThat(found.get().schemaName()).isEqualTo("public");
  }

  @Test
  void searchesAllSchemasWhenSchemaOmitted() {
    var found = TableResolver.findTable(catalog, null, "events");

    assertThat(found).isPresent();
    assertThat(found.get().schemaName()).isEqualTo("audit");
  }

  @Test
  void returnsEmptyForUnknownTable() {
    assertThat(TableResolver.findTable(catalog, "public", "missing")).isEmpty();
  }

  @Test
  void returnsEmptyForUnknownSchema() {
    assertThat(TableResolver.findTable(catalog, "nope", "users")).isEmpty();
  }

  private static Table table(long id, long schemaId, String name) {
    return new Table(id, schemaId, name, TableKind.TABLE, List.of(), List.of(), List.of());
  }
}
