package com.example.dbagg.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Запрос из SQL-консоли.
 *
 * @param sql федеративный SQL
 * @param maxRows ограничение числа строк результата (≤0 → 1000)
 * @param offset смещение первой строки результата (≤0 → 0)
 * @param timeoutMs таймаут выполнения в мс (≤0 → 30000)
 */
public record QueryRequest(
    @NotBlank String sql, Integer maxRows, Integer offset, Integer timeoutMs) {}
