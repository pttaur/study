package com.example.dbagg.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.dbagg.model.Instance;
import com.example.dbagg.repository.InstanceRepository;
import com.example.dbagg.repository.StoragePostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import java.sql.DriverManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(StoragePostgresResource.class)
class RowsControllerTest {
  private static final String SCHEMA = "public";
  private static final String TABLE = "rows_controller_test";

  @Inject InstanceRepository instanceRepository;

  @BeforeAll
  static void configureRestAssured() {
    RestAssured.basePath = "/api";
  }

  @Test
  void readsRowsPage() throws Exception {
    execute("DROP TABLE IF EXISTS " + TABLE);
    execute("CREATE TABLE " + TABLE + " (id INT PRIMARY KEY, name TEXT)");
    execute("INSERT INTO " + TABLE + " (id, name) VALUES (1, 'a'), (2, 'b')");

    var id =
        instanceRepository.saveInstance(
            new Instance(
                0,
                "rows-target",
                StoragePostgresResource.jdbcUrl(),
                StoragePostgresResource.username()),
            StoragePostgresResource.password());

    given().when().post("/instances/{id}/load", id).then().statusCode(200);

    var total =
        given()
            .when()
            .get("/instances/{id}/tables/{schema}/{table}/rows", id, SCHEMA, TABLE)
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getLong("totalRows");
    assertThat(total).isEqualTo(2);
  }

  @Test
  void missingInstanceReturns404() {
    given()
        .when()
        .get("/instances/{id}/tables/{schema}/{table}/rows", 999_999, SCHEMA, TABLE)
        .then()
        .statusCode(404);
  }

  private static void execute(String sql) throws Exception {
    try (var conn =
            DriverManager.getConnection(
                StoragePostgresResource.jdbcUrl(),
                StoragePostgresResource.username(),
                StoragePostgresResource.password());
        var st = conn.createStatement()) {
      st.execute(sql);
    }
  }
}
