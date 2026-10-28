package com.example.dbagg.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.example.dbagg.model.Catalog;
import com.example.dbagg.model.Column;
import com.example.dbagg.model.Constraint;
import com.example.dbagg.model.ConstraintColumn;
import com.example.dbagg.model.ConstraintType;
import com.example.dbagg.model.Index;
import com.example.dbagg.model.IndexColumn;
import com.example.dbagg.model.IndexKind;
import com.example.dbagg.model.Instance;
import com.example.dbagg.model.Schema;
import com.example.dbagg.model.Table;
import com.example.dbagg.model.TableKind;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@QuarkusTestResource(StoragePostgresResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CatalogRepositoryTest {
  @Inject CatalogRepository catalogRepository;

  @Inject InstanceRepository instanceRepository;

  @Test
  @Order(1)
  void saveAndLoadCatalogRoundTrip() {
    var instanceId = saveInstance("catalog", "jdbc:postgresql://db3:5432/app");

    catalogRepository.saveCatalog(sampleCatalog(instanceId));

    var loaded = catalogRepository.loadCatalog(instanceId);
    assertThat(loaded.schemas()).hasSize(1);
    var schema = loaded.schemas().get(0);
    assertThat(schema.schemaName()).isEqualTo("public");
    assertThat(schema.tables()).hasSize(1);
    var table = schema.tables().get(0);
    assertThat(table.tableName()).isEqualTo("users");
    assertThat(table.kind()).isEqualTo(TableKind.TABLE);
    assertThat(table.columns()).extracting(Column::columnName).containsExactly("id", "email");
    assertThat(table.constraints())
        .extracting(c -> c.type())
        .containsExactly(ConstraintType.PRIMARY_KEY);
    assertThat(table.indexes()).extracting(i -> i.kind()).containsExactly(IndexKind.BTREE);
    assertThat(table.indexes().get(0).unique()).isTrue();
  }

  @Test
  @Order(2)
  void saveCatalogReplacesPreviousSnapshot() {
    var instanceId = saveInstance("replace", "jdbc:postgresql://db4:5432/app");

    catalogRepository.saveCatalog(sampleCatalog(instanceId));
    var second =
        new Catalog(
            instanceId,
            List.of(
                new Schema(
                    0,
                    instanceId,
                    "public",
                    "postgres",
                    null,
                    List.of(
                        new Table(
                            0,
                            0,
                            "other_table",
                            TableKind.TABLE,
                            List.of(new Column(0, 0, "name", 1, "text", true, null, false)),
                            List.of(),
                            List.of())))));

    catalogRepository.saveCatalog(second);

    var loaded = catalogRepository.loadCatalog(instanceId);
    assertThat(loaded.schemas()).hasSize(1);
    assertThat(loaded.schemas().get(0).tables())
        .extracting(Table::tableName)
        .containsExactly("other_table");
  }

  @Test
  @Order(4)
  void saveAndLoadCompositeKeysAndForeignKeys() {
    var instanceId = saveInstance("composite", "jdbc:postgresql://db6:5432/app");

    var tenantId = new Column(0, 0, "tenant_id", 1, "bigint", false, null, false);
    var id = new Column(0, 0, "id", 2, "bigint", false, null, false);
    var users =
        new Table(
            0,
            0,
            "users",
            TableKind.TABLE,
            List.of(tenantId, id),
            List.of(
                new Constraint(
                    0,
                    0,
                    "users_pkey",
                    ConstraintType.PRIMARY_KEY,
                    null,
                    null,
                    null,
                    null,
                    List.of(
                        new ConstraintColumn("tenant_id", 1, null),
                        new ConstraintColumn("id", 2, null)))),
            List.of());
    var userTenant = new Column(0, 0, "user_tenant", 1, "bigint", false, null, false);
    var userId = new Column(0, 0, "user_id", 2, "bigint", false, null, false);
    var total = new Column(0, 0, "total", 3, "numeric", true, null, false);
    var orders =
        new Table(
            0,
            0,
            "orders",
            TableKind.TABLE,
            List.of(userTenant, userId, total),
            List.of(
                new Constraint(
                    0,
                    0,
                    "orders_user_fkey",
                    ConstraintType.FOREIGN_KEY,
                    null,
                    "public.users",
                    "CASCADE",
                    "RESTRICT",
                    List.of(
                        new ConstraintColumn("user_tenant", 1, "tenant_id"),
                        new ConstraintColumn("user_id", 2, "id"))),
                new Constraint(
                    0,
                    0,
                    "orders_missing_fkey",
                    ConstraintType.FOREIGN_KEY,
                    null,
                    "public.missing",
                    "NO_ACTION",
                    "NO_ACTION",
                    List.of(new ConstraintColumn("user_id", 1, "id")))),
            List.of(
                new Index(
                    0,
                    0,
                    "orders_user_idx",
                    true,
                    false,
                    IndexKind.BTREE,
                    "CREATE UNIQUE INDEX ...",
                    List.of(new IndexColumn("user_tenant", 1), new IndexColumn("user_id", 2)))));
    var catalog =
        new Catalog(
            instanceId,
            List.of(new Schema(0, instanceId, "public", "postgres", null, List.of(users, orders))));
    catalogRepository.saveCatalog(catalog);

    var loaded = catalogRepository.loadCatalog(instanceId);
    var tables = loaded.schemas().get(0).tables();
    assertThat(tables).extracting(Table::tableName).containsExactly("orders", "users");

    var loadedUsers = tables.get(1);
    var pk = loadedUsers.constraints().get(0);
    assertThat(pk.type()).isEqualTo(ConstraintType.PRIMARY_KEY);
    assertThat(pk.columns())
        .extracting(ConstraintColumn::columnName)
        .containsExactly("tenant_id", "id");
    assertThat(pk.columns()).extracting(ConstraintColumn::position).containsExactly(1, 2);

    var loadedOrders = tables.get(0);
    assertThat(loadedOrders.constraints())
        .extracting(c -> c.constraintName())
        .containsExactly("orders_user_fkey");
    var fk = loadedOrders.constraints().get(0);
    assertThat(fk.refTable()).isEqualTo("public.users");
    assertThat(fk.onDelete()).isEqualTo("CASCADE");
    assertThat(fk.onUpdate()).isEqualTo("RESTRICT");
    assertThat(fk.columns())
        .extracting(ConstraintColumn::columnName, ConstraintColumn::refColumnName)
        .containsExactly(tuple("user_tenant", "tenant_id"), tuple("user_id", "id"));

    var index = loadedOrders.indexes().get(0);
    assertThat(index.unique()).isTrue();
    assertThat(index.columns())
        .extracting(IndexColumn::columnName)
        .containsExactly("user_tenant", "user_id");
  }

  private long saveInstance(String label, String url) {
    return instanceRepository.saveInstance(new Instance(0, label, url, "user"), "pw");
  }

  private Catalog sampleCatalog(long instanceId) {
    var idColumn = new Column(0, 0, "id", 1, "bigint", false, "nextval('users_id_seq')", false);
    var emailColumn = new Column(0, 0, "email", 2, "text", true, null, false);
    var pk =
        new Constraint(
            0,
            0,
            "users_pkey",
            ConstraintType.PRIMARY_KEY,
            null,
            null,
            null,
            null,
            List.of(new ConstraintColumn("id", 1, null)));
    var idx =
        new Index(
            0,
            0,
            "users_pkey",
            true,
            true,
            IndexKind.BTREE,
            "CREATE UNIQUE INDEX ...",
            List.of(new IndexColumn("id", 1)));
    var table =
        new Table(
            0,
            0,
            "users",
            TableKind.TABLE,
            List.of(idColumn, emailColumn),
            List.of(pk),
            List.of(idx));
    var schema = new Schema(0, instanceId, "public", "postgres", null, List.of(table));
    return new Catalog(instanceId, List.of(schema));
  }
}
