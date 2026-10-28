package com.example.dbagg.repository;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.testcontainers.containers.PostgreSQLContainer;

public class StoragePostgresResource implements QuarkusTestResourceLifecycleManager {
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("scagg_test")
          .withUsername("scagg")
          .withPassword("scagg");

  public static String jdbcUrl() {
    return POSTGRES.getJdbcUrl();
  }

  public static String username() {
    return POSTGRES.getUsername();
  }

  public static String password() {
    return POSTGRES.getPassword();
  }

  @Override
  public Map<String, String> start() {
    POSTGRES.start();
    migrate();
    return Map.of(
        "quarkus.datasource.storage.jdbc.url", POSTGRES.getJdbcUrl(),
        "quarkus.datasource.storage.username", POSTGRES.getUsername(),
        "quarkus.datasource.storage.password", POSTGRES.getPassword(),
        "quarkus.datasource.storage.db-kind", "postgresql",
        "app.encrypt.key", "test-encryption-key");
  }

  private static void migrate() {
    try (var conn =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var stmt = conn.createStatement()) {
      for (var file :
          List.of(
              "/V1__create_schema.sql",
              "/V2__encrypt_password.sql",
              "/V3__snapshot_rows.sql",
              "/V4__federation.sql",
              "/V5__constraints_and_triggers.sql",
              "/V6__normalize_catalog.sql",
              "/V7__drop_sc_rows.sql",
              "/V8__remove_instance_alias.sql")) {
        var sql = readResource(file);
        for (var statement : splitStatements(sql)) {
          stmt.execute(statement);
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException("Failed to run storage migrations", e);
    }
  }

  private static String readResource(String name) {
    try (InputStream in = StoragePostgresResource.class.getResourceAsStream(name)) {
      if (in == null) {
        throw new IllegalStateException("Migration resource not found: " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to read migration resource: " + name, e);
    }
  }

  private static List<String> splitStatements(String sql) {
    var statements = new ArrayList<String>();
    var current = new StringBuilder();
    var inString = false;
    String dollarTag = null;
    for (var i = 0; i < sql.length(); i++) {
      var c = sql.charAt(i);
      if (dollarTag != null) {
        if (sql.startsWith(dollarTag, i)) {
          current.append(dollarTag);
          i += dollarTag.length() - 1;
          dollarTag = null;
        } else {
          current.append(c);
        }
      } else if (inString) {
        current.append(c);
        if (c == '\'') {
          if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
            current.append('\'');
            i++;
          } else {
            inString = false;
          }
        }
      } else if (c == '\'') {
        inString = true;
        current.append(c);
      } else if (c == '$') {
        var tag = readDollarTag(sql, i);
        if (tag != null) {
          dollarTag = tag;
          current.append(tag);
          i += tag.length() - 1;
        } else {
          current.append(c);
        }
      } else if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
        while (i < sql.length() && sql.charAt(i) != '\n') {
          i++;
        }
        current.append('\n');
      } else if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
        i += 2;
        while (i + 1 < sql.length() && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
          i++;
        }
        i++;
      } else if (c == ';') {
        var statement = current.toString().trim();
        if (!statement.isEmpty()) {
          statements.add(statement);
        }
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    var tail = current.toString().trim();
    if (!tail.isEmpty()) {
      statements.add(tail);
    }
    return statements;
  }

  /** Возвращает dollar-quote тег (например {@code $$} или {@code $body$}) или {@code null}. */
  private static String readDollarTag(String sql, int start) {
    var end = sql.indexOf('$', start + 1);
    if (end < 0) {
      return null;
    }
    var inner = sql.substring(start + 1, end);
    if (!inner.isEmpty() && !inner.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      return null;
    }
    return sql.substring(start, end + 1);
  }

  @Override
  public void stop() {
    POSTGRES.stop();
  }
}
