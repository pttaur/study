package com.example.dbagg.model;

import jakarta.validation.constraints.Positive;

/** Запрос на сравнение двух собранных каталогов. */
public record CompareRequest(@Positive long baseInstanceId, @Positive long compareInstanceId) {}
