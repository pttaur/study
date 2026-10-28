package com.example.dbagg.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dbagg.model.DbKind;
import org.junit.jupiter.api.Test;

class MariaDbConnectorTest {
  private final MariaDbConnector connector = new MariaDbConnector();

  @Test
  void kindIsMariadb() {
    assertThat(connector.kind()).isEqualTo(DbKind.MARIADB);
  }

  @Test
  void mapsMariaDbTypesToPostgres() {
    assertThat(connector.mapTypeName("tinyint")).isEqualTo("smallint");
    assertThat(connector.mapTypeName("smallint unsigned")).isEqualTo("smallint");
    assertThat(connector.mapTypeName("int")).isEqualTo("integer");
    assertThat(connector.mapTypeName("bigint")).isEqualTo("bigint");
    assertThat(connector.mapTypeName("decimal(10,2)")).isEqualTo("numeric");
    assertThat(connector.mapTypeName("double precision")).isEqualTo("double precision");
    assertThat(connector.mapTypeName("bit(1)")).isEqualTo("boolean");
    assertThat(connector.mapTypeName("varchar(255)")).isEqualTo("varchar");
    assertThat(connector.mapTypeName("text")).isEqualTo("text");
    assertThat(connector.mapTypeName("longtext")).isEqualTo("text");
    assertThat(connector.mapTypeName("enum('a','b')")).isEqualTo("text");
    assertThat(connector.mapTypeName("json")).isEqualTo("text");
    assertThat(connector.mapTypeName("blob")).isEqualTo("bytea");
    assertThat(connector.mapTypeName("varbinary(16)")).isEqualTo("bytea");
    assertThat(connector.mapTypeName("datetime")).isEqualTo("timestamp");
    assertThat(connector.mapTypeName("timestamp")).isEqualTo("timestamp");
    assertThat(connector.mapTypeName(null)).isEqualTo("text");
    assertThat(connector.mapTypeName("something_odd")).isEqualTo("text");
  }

  @Test
  void buildsTableReferenceWithBackticks() {
    assertThat(connector.tableReference("shop", "orders", "o")).isEqualTo("`shop`.`orders` AS `o`");
    assertThat(connector.tableReference("shop", "orders", null)).isEqualTo("`shop`.`orders`");
    assertThat(connector.tableReference("shop", "or`ders", null)).isEqualTo("`shop`.`or``ders`");
  }

  @Test
  void postgresDialectUsesDoubleQuotes() {
    var postgres = new PostgresConnector();
    assertThat(postgres.tableReference("public", "users", "u"))
        .isEqualTo("\"public\".\"users\" AS \"u\"");
    assertThat(postgres.mapTypeName("int4")).isEqualTo("int4");
  }
}
