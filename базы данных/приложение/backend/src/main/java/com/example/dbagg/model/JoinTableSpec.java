package com.example.dbagg.model;

import java.util.List;
import java.util.Objects;

/**
 * Таблица в конструкторе джойнов.
 *
 * @param ref ссылка вида {@code instance.schema.table}
 * @param type тип соединения ({@code null} для базовой таблицы FROM)
 * @param on условия соединения (для не-базовых таблиц)
 */
public record JoinTableSpec(String ref, JoinType type, List<JoinCondition> on) {
  public JoinTableSpec {
    Objects.requireNonNull(ref, "ref");
    on = on == null ? List.of() : List.copyOf(on);
  }
}
