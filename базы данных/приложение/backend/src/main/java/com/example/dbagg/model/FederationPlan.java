package com.example.dbagg.model;

import java.util.List;

/**
 * Полный план федеративного запроса.
 *
 * @param outerSql SQL для внутреннего PostgreSQL (имена таблиц заменены на временные, продавленные
 *     предикаты исключены)
 * @param sources источники для чтения и материализации
 */
public record FederationPlan(String outerSql, List<SourcePlan> sources) {}
