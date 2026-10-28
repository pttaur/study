package com.example.dbagg.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConnectionUrlValidatorTest {
  private static ConnectionUrlValidator validator(String allowedHosts) {
    return new ConnectionUrlValidator(Optional.ofNullable(allowedHosts));
  }

  @Test
  void acceptsMatchingScheme() {
    validator(null).validate("jdbc:postgresql://db:5432/app", "POSTGRES");
    validator(null).validate("jdbc:mariadb://db:3306/app", "MARIADB");
  }

  @Test
  void rejectsMismatchedScheme() {
    assertThatThrownBy(() -> validator(null).validate("jdbc:postgresql://db:5432/app", "MARIADB"))
        .isInstanceOf(jakarta.ws.rs.BadRequestException.class)
        .hasMessageContaining("jdbc:mariadb:");
  }

  @Test
  void enforcesAllowlistWhenConfigured() {
    var validator = validator("db.internal, *.example.com");

    validator.validate("jdbc:postgresql://db.internal:5432/app", "POSTGRES");
    validator.validate("jdbc:postgresql://x.example.com:5432/app", "POSTGRES");

    assertThatThrownBy(() -> validator.validate("jdbc:postgresql://evil.com:5432/app", "POSTGRES"))
        .isInstanceOf(jakarta.ws.rs.BadRequestException.class)
        .hasMessageContaining("not allowed");
  }

  @Test
  void extractsHostFromUrls() {
    assertThat(ConnectionUrlValidator.extractHost("jdbc:postgresql://db:5432/app")).isEqualTo("db");
    assertThat(ConnectionUrlValidator.extractHost("jdbc:mariadb://db:3306/app")).isEqualTo("db");
    assertThat(ConnectionUrlValidator.extractHost("jdbc:mariadb://10.0.0.5:3306/app"))
        .isEqualTo("10.0.0.5");
  }
}
