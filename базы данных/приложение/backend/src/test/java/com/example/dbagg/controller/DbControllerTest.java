package com.example.dbagg.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.dbagg.repository.InstanceRepository;
import com.example.dbagg.repository.StoragePostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(StoragePostgresResource.class)
class DbControllerTest {
  @Inject InstanceRepository instanceRepository;

  @BeforeAll
  static void configureRestAssured() {
    RestAssured.basePath = "/api";
  }

  @Test
  void createListGetInstanceRoundTrip() {
    var body =
        """
        {"label":"api-instance","connectionUrl":"jdbc:postgresql://db-api:5432/app",\
        "username":"api-user","password":"api-pw"}
        """;

    var id =
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when()
            .post("/instances")
            .then()
            .statusCode(201)
            .extract()
            .jsonPath()
            .getLong("id");
    assertThat(id).isPositive();

    given().when().get("/instances").then().statusCode(200);
    given().when().get("/instances/{id}", id).then().statusCode(200);
  }

  @Test
  void createWithBlankFieldsRejected() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"label\":\"\",\"connectionUrl\":\"x\",\"username\":\"u\",\"password\":\"p\"}")
        .when()
        .post("/instances")
        .then()
        .statusCode(400);
  }

  @Test
  void createWithUnsupportedKindRejected() {
    given()
        .contentType(ContentType.JSON)
        .body(
            "{\"label\":\"mysql\",\"connectionUrl\":\"jdbc:postgresql://db:5432/app\","
                + "\"username\":\"u\",\"password\":\"p\",\"kind\":\"MYSQL\"}")
        .when()
        .post("/instances")
        .then()
        .statusCode(400);
  }

  @Test
  void duplicateConnectionUrlRejected() {
    var url = "jdbc:postgresql://dup-" + System.nanoTime() + ":5432/app";
    var first =
        "{\"label\":\"dup-a-"
            + System.nanoTime()
            + "\",\"connectionUrl\":\""
            + url
            + "\",\"username\":\"u\",\"password\":\"p\"}";
    var second =
        "{\"label\":\"dup-b-"
            + System.nanoTime()
            + "\",\"connectionUrl\":\""
            + url
            + "\",\"username\":\"u\",\"password\":\"p\"}";

    given()
        .contentType(ContentType.JSON)
        .body(first)
        .when()
        .post("/instances")
        .then()
        .statusCode(201);

    given()
        .contentType(ContentType.JSON)
        .body(second)
        .when()
        .post("/instances")
        .then()
        .statusCode(409);
  }

  @Test
  void getMissingInstanceReturns404() {
    given().when().get("/instances/{id}", 999_999).then().statusCode(404);
  }

  @Test
  void updatePasswordRoundTrip() {
    var id = createInstance("rotate-api", "jdbc:postgresql://db-rot:5432/app", "pw-old");

    given()
        .contentType(ContentType.JSON)
        .body("{\"password\":\"pw-new\"}")
        .when()
        .patch("/instances/{id}/password", id)
        .then()
        .statusCode(204);

    var loaded = instanceRepository.decryptPassword(id);
    assertThat(loaded).isEqualTo("pw-new");
  }

  @Test
  void loadStreamsSchemaIntoCacheAndStorage() {
    var id =
        createInstance(
            "loader-api",
            StoragePostgresResource.jdbcUrl() + "&ApplicationName=loader-api",
            StoragePostgresResource.password());

    given()
        .when()
        .post("/instances/{id}/load", id)
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON);

    given()
        .when()
        .get("/instances/{id}/schema", id)
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON);
  }

  @Test
  void compareTwoLoadedInstancesReturnsDiff() {
    var baseId =
        createInstance(
            "base-api",
            StoragePostgresResource.jdbcUrl() + "&ApplicationName=base-api",
            StoragePostgresResource.password());
    var compareId =
        createInstance(
            "compare-api",
            StoragePostgresResource.jdbcUrl() + "&ApplicationName=compare-api",
            StoragePostgresResource.password());
    given().when().post("/instances/{id}/load", baseId).then().statusCode(200);
    given().when().post("/instances/{id}/load", compareId).then().statusCode(200);

    var response =
        given()
            .contentType(ContentType.JSON)
            .body("{\"baseInstanceId\":%d,\"compareInstanceId\":%d}".formatted(baseId, compareId))
            .when()
            .post("/instances/compare")
            .then()
            .statusCode(200)
            .extract()
            .jsonPath();

    assertThat(response.getLong("baseInstanceId")).isEqualTo(baseId);
    assertThat(response.getLong("compareInstanceId")).isEqualTo(compareId);
  }

  @Test
  void compareWithMissingInstanceReturns404() {
    given()
        .contentType(ContentType.JSON)
        .body("{\"baseInstanceId\":1,\"compareInstanceId\":999999}")
        .when()
        .post("/instances/compare")
        .then()
        .statusCode(404);
  }

  @Test
  void sseStreamsLoadedCatalog() throws Exception {
    var id =
        createInstance(
            "sse-api",
            StoragePostgresResource.jdbcUrl() + "&ApplicationName=sse-api",
            StoragePostgresResource.password());

    var url =
        URI.create("http://localhost:%d/api/instances/%d/events".formatted(RestAssured.port, id));
    var client = HttpClient.newHttpClient();
    var request = HttpRequest.newBuilder(url).GET().build();
    var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

    assertThat(response.statusCode()).isEqualTo(200);

    var lineFuture = CompletableFuture.supplyAsync(() -> readFirstSseLine(response));

    given().when().post("/instances/{id}/load", id).then().statusCode(200);

    var line = lineFuture.get(5, TimeUnit.SECONDS);
    assertThat(line).startsWith("data:");
    assertThat(line).contains("\"instanceId\":%d".formatted(id));
  }

  private long createInstance(String label, String connectionUrl, String password) {
    return createInstance(label, connectionUrl, StoragePostgresResource.username(), password);
  }

  private long createInstance(
      String label, String connectionUrl, String username, String password) {
    return given()
        .contentType(ContentType.JSON)
        .body(
            "{\"label\":\"%s\",\"connectionUrl\":\"%s\",\"username\":\"%s\",\"password\":\"%s\"}"
                .formatted(label, connectionUrl, username, password))
        .when()
        .post("/instances")
        .then()
        .statusCode(201)
        .extract()
        .jsonPath()
        .getLong("id");
  }

  private static String readFirstSseLine(HttpResponse<java.io.InputStream> response) {
    try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(response.body()))) {
      return reader.readLine();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
