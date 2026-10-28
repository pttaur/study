package com.example.dbagg.model;

import java.util.Objects;

/**
 * Подключение к целевой БД.
 *
 * @param id идентификатор
 * @param label отображаемое имя (используется в ссылках {@code label.schema.table})
 * @param connectionUrl JDBC URL
 * @param username пользователь
 * @param kind тип СУБД (по умолчанию {@code POSTGRES})
 */
public record Instance(long id, String label, String connectionUrl, String username, String kind) {
  public static final String DEFAULT_KIND = "POSTGRES";

  public Instance {
    Objects.requireNonNull(label, "label");
    Objects.requireNonNull(connectionUrl, "connectionUrl");
    Objects.requireNonNull(username, "username");
    if (kind == null || kind.isBlank()) {
      kind = DEFAULT_KIND;
    }
  }

  public Instance(long id, String label, String connectionUrl, String username) {
    this(id, label, connectionUrl, username, DEFAULT_KIND);
  }
}
