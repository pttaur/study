package com.example.dbagg.model;

import java.util.List;

/**
 * План работы с одним источником.
 *
 * @param ref ссылка из запроса
 * @param resolved сопоставление с инстансом и таблицей каталога
 * @param tempName имя временной таблицы во внутреннем PostgreSQL
 * @param columns проецируемые колонки (пусто — все)
 * @param predicate продавливаемый в источник предикат (может быть {@code null})
 */
public record SourcePlan(
    TableRef ref, ResolvedTable resolved, String tempName, List<String> columns, String predicate) {
  public SourcePlan {
    columns = List.copyOf(columns);
  }
}
