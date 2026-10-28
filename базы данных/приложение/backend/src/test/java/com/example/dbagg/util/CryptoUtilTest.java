package com.example.dbagg.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.dbagg.model.Instance;
import com.example.dbagg.repository.InstanceRepository;
import com.example.dbagg.repository.StoragePostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@QuarkusTestResource(StoragePostgresResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CryptoUtilTest {
  @Inject InstanceRepository instanceRepository;

  @Inject CryptoUtil cryptoUtil;

  @Test
  @Order(1)
  void decryptReturnsPlainTextPassword() {
    var id =
        instanceRepository.saveInstance(
            new Instance(0, "crypto-test", "jdbc:postgresql://db:5432/app1", "u"), "p@ssw0rd");

    assertThat(cryptoUtil.decryptPassword(id)).isEqualTo("p@ssw0rd");
  }

  @Test
  @Order(2)
  void storePasswordUpdatesPassword() {
    var id =
        instanceRepository.saveInstance(
            new Instance(0, "crypto-rotate", "jdbc:postgresql://db:5432/app2", "u"), "old");

    cryptoUtil.storePassword(id, "new");

    assertThat(cryptoUtil.decryptPassword(id)).isEqualTo("new");
  }

  @Test
  @Order(3)
  void decryptThrowsWhenInstanceNotFound() {
    assertThatThrownBy(() -> cryptoUtil.decryptPassword(999_999))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("999999");
  }
}
