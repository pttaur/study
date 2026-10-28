package com.example.dbagg.model;

import java.util.List;

/**
 * Прочитанные из источника данные одной таблицы.
 *
 * @param columns колонки
 * @param rows строки
 */
public record SourceData(List<ColumnInfo> columns, List<List<Object>> rows) {}
