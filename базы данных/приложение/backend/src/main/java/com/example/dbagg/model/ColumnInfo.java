package com.example.dbagg.model;

/**
 * Описание колонки результата запроса.
 *
 * @param name имя колонки
 * @param type тип (SQL-имя)
 */
public record ColumnInfo(String name, String type) {}
