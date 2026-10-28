package com.example.dbagg.model;

/**
 * Запись истории федеративных запросов.
 *
 * @param id идентификатор
 * @param sql текст запроса
 * @param status статус ({@code OK}/{@code ERROR})
 * @param rowCount число строк результата
 * @param durationMs длительность в мс
 * @param error текст ошибки
 * @param createdAt время выполнения
 */
public record QueryHistoryEntry(
    long id,
    String sql,
    String status,
    Long rowCount,
    Long durationMs,
    String error,
    String createdAt) {}
