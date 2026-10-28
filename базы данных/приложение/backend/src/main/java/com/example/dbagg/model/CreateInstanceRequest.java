package com.example.dbagg.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Запрос на создание инстанса. Пароль передаётся открытым текстом и хранится зашифрованным.
 *
 * <p>Название инстанса ({@code label}) уникально и используется в федеративных ссылках {@code
 * label.schema.table}.
 */
public record CreateInstanceRequest(
    @NotBlank String label,
    @NotBlank
        @Pattern(
            regexp = "^jdbc:(postgresql|mariadb):.+",
            message = "Connection URL must be a JDBC URL for PostgreSQL or MariaDB")
        String connectionUrl,
    @NotBlank String username,
    @NotBlank String password,
    @Pattern(
            regexp = "(?i)(POSTGRES|MARIADB)",
            message = "Unsupported DB kind; supported: POSTGRES, MARIADB")
        String kind) {
  public CreateInstanceRequest {
    if (label != null) {
      label = label.trim();
    }
    if (connectionUrl != null) {
      connectionUrl = connectionUrl.trim();
    }
    if (username != null) {
      username = username.trim();
    }
    if (kind != null) {
      kind = kind.trim();
    }
  }
}
