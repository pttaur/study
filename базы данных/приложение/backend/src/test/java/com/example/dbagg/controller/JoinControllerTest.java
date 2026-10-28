package com.example.dbagg.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.dbagg.model.Instance;
import com.example.dbagg.repository.InstanceRepository;
import com.example.dbagg.repository.StoragePostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@QuarkusTest
@QuarkusTestResource(StoragePostgresResource.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JoinControllerTest {
  private static final String SOURCE_DB = "join_src";

  @Inject InstanceRepository instanceRepository;

  private Long instanceId;

  @BeforeAll
  void configureRestAssured() {
    RestAssured.basePath = "/api";
  }

  @Test
  void joinsTablesThroughTheBuilder() throws Exception {
    ensureInstance();

    var body =
        Map.of(
            "tables",
            List.of(
                Map.of("ref", "join-source.public.fed_users"),
                Map.of(
                    "ref", "join-source.public.fed_orders",
                    "type", "INNER",
                    "on",
                        List.of(
                            Map.of(
                                "leftTable", "join-source.public.fed_orders",
                                "leftColumn", "user_id",
                                "rightTable", "join-source.public.fed_users",
                                "rightColumn", "id")))),
            "select",
            List.of(
                Map.of("table", "join-source.public.fed_users", "column", "name"),
                Map.of("table", "join-source.public.fed_orders", "column", "total")),
            "maxRows",
            100);

    var json =
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/join")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath();

    assertThat(json.getString("columns[0].name")).isEqualTo("name");
    assertThat(json.getString("columns[1].name")).isEqualTo("total");
    assertThat(json.getList("rows")).hasSize(2);
    assertThat(json.getList("rows[0]")).isEqualTo(List.of("alice", 100));
    assertThat(json.getList("rows[1]")).isEqualTo(List.of("bob", 250));
  }

  @Test
  void rejectsEmptySelection() {
    var body = Map.of("tables", List.of(), "select", List.of());

    given().contentType(ContentType.JSON).body(body).when().post("/join").then().statusCode(400);
  }

  private long ensureInstance() throws Exception {
    if (instanceId == null) {
      var sourceUrl = createSourceDatabase();
      instanceId =
          instanceRepository.saveInstance(
              new Instance(
                  0, "join-source", sourceUrl, StoragePostgresResource.username(), "POSTGRES"),
              StoragePostgresResource.password());
      given().when().post("/instances/{id}/load", instanceId).then().statusCode(200);
    }
    return instanceId;
  }

  /** Создаёт в контейнере отдельную БД-источник и две таблицы; возвращает JDBC URL. */
  private static String createSourceDatabase() throws Exception {
    var adminUrl = StoragePostgresResource.jdbcUrl();
    try (var conn =
            DriverManager.getConnection(
                adminUrl, StoragePostgresResource.username(), StoragePostgresResource.password());
        var st = conn.createStatement()) {
      var rs = st.executeQuery("SELECT 1 FROM pg_database WHERE datname = '" + SOURCE_DB + "'");
      if (!rs.next()) {
        st.execute("CREATE DATABASE " + SOURCE_DB);
      }
    }

    var sourceUrl = adminUrl.replaceAll("/[^/?]+(\\?|$)", "/" + SOURCE_DB + "$1");
    try (var conn =
            DriverManager.getConnection(
                sourceUrl, StoragePostgresResource.username(), StoragePostgresResource.password());
        var st = conn.createStatement()) {
      st.execute("DROP TABLE IF EXISTS fed_orders");
      st.execute("DROP TABLE IF EXISTS fed_users");
      st.execute("CREATE TABLE fed_users (id INT PRIMARY KEY, name TEXT, active BOOLEAN)");
      st.execute("CREATE TABLE fed_orders (id INT PRIMARY KEY, user_id INT, total INT)");
      st.execute(
          "INSERT INTO fed_users (id, name, active) VALUES (1, 'alice', true), (2, 'bob', true)");
      st.execute("INSERT INTO fed_orders (id, user_id, total) VALUES (10, 1, 100), (11, 2, 250)");
    }
    return sourceUrl;
  }
}
