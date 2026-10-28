package com.example.dbagg.model;

import java.util.List;
import java.util.Objects;

/**
 * Запрос конструктора джойнов: набор таблиц (первая — базовая), выбираемые колонки и лимиты.
 *
 * @param tables таблицы соединения в порядке появления
 * @param select колонки результата
 * @param maxRows размер страницы результата (≤0 → 1000)
 * @param offset смещение страницы (≤0 → 0)
 * @param timeoutMs таймаут выполнения в мс (≤0 → 30000)
 */
public record JoinRequest(
    List<JoinTableSpec> tables,
    List<SelectColumn> select,
    Integer maxRows,
    Integer offset,
    Integer timeoutMs) {
  public JoinRequest {
    Objects.requireNonNull(tables, "tables");
    Objects.requireNonNull(select, "select");
    tables = List.copyOf(tables);
    select = List.copyOf(select);
  }
}
