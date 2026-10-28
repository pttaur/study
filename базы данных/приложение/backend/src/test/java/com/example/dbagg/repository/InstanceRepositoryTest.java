package com.example.dbagg.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.dbagg.model.Instance;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@QuarkusTestResource(StoragePostgresResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InstanceRepositoryTest {
  @Inject InstanceRepository instanceRepository;

  @Test
  @Order(1)
  void encryptsAndDecryptsPasswordRoundTrip() {
    var id =
        instanceRepository.saveInstance(
            new Instance(0, "test-instance", "jdbc:postgresql://localhost:5432/db", "dbuser"),
            "s3cr3t-p@ss");

    assertThat(id).isPositive();
    assertThat(instanceRepository.decryptPassword(id)).isEqualTo("s3cr3t-p@ss");
    var raw = fetchPasswordEncrypted(id);
    assertThat(raw).isNotEmpty();
    assertThat(raw).doesNotContain("s3cr3t");
  }

  @Test
  @Order(2)
  void findsStoredInstances() {
    var id =
        instanceRepository.saveInstance(
            new Instance(0, "list-me", "jdbc:postgresql://db1:5432/app", "user1"), "pw1");
    var found = instanceRepository.findInstanceById(id);
    assertThat(found).isPresent();
    assertThat(found.get().label()).isEqualTo("list-me");
    assertThat(found.get().connectionUrl()).isEqualTo("jdbc:postgresql://db1:5432/app");
    assertThat(found.get().username()).isEqualTo("user1");

    assertThat(instanceRepository.findAllInstances())
        .extracting(Instance::label)
        .contains("list-me");
  }

  @Test
  @Order(3)
  void updatesEncryptedPassword() {
    var id =
        instanceRepository.saveInstance(
            new Instance(0, "rotate", "jdbc:postgresql://db2:5432/app", "user2"), "old-pw");
    instanceRepository.storeEncryptedPassword(id, "new-pw");

    assertThat(instanceRepository.decryptPassword(id)).isEqualTo("new-pw");
  }

  @Test
  @Order(4)
  void decryptPasswordThrowsForMissingInstance() {
    assertThatThrownBy(() -> instanceRepository.decryptPassword(999_999))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("999999");
  }

  private List<String> fetchPasswordEncrypted(long id) {
    try (Connection conn =
            DriverManager.getConnection(
                StoragePostgresResource.jdbcUrl(),
                StoragePostgresResource.username(),
                StoragePostgresResource.password());
        Statement stmt = conn.createStatement()) {
      var rows = new ArrayList<String>();
      try (var rs =
          stmt.executeQuery(
              "SELECT encode(password_encrypted, 'hex') FROM sc_instances WHERE id = " + id)) {
        while (rs.next()) {
          rows.add(rs.getString(1));
        }
      }
      return rows;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
