package com.example.dbagg.model;

import java.util.List;
import java.util.Objects;

/**
 * Разобранный федеративный запрос: исходный SQL и извлечённые из него источники и признаки (наличие
 * WHERE/GROUP BY/ORDER BY/LIMIT).
 *
 * @param sql нормализованный текст запроса
 * @param tables таблицы-источники в порядке появления
 * @param joinCount число JOIN-ов (без учёта неявных соединений через запятую)
 * @param hasWhere присутствует ли WHERE
 * @param hasGroupBy присутствует ли GROUP BY
 * @param hasOrderBy присутствует ли ORDER BY
 * @param limit значение LIMIT, если оно задано константой
 */
public record FederationQuery(
    String sql,
    List<TableRef> tables,
    int joinCount,
    boolean hasWhere,
    boolean hasGroupBy,
    boolean hasOrderBy,
    Long limit) {
  public FederationQuery {
    Objects.requireNonNull(sql, "sql");
    tables = List.copyOf(tables);
  }
}
