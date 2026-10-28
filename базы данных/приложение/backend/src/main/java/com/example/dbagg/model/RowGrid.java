package com.example.dbagg.model;

import java.util.List;

/**
 * Сетка строк таблицы для DataGrid-эксплорера.
 *
 * @param columns имена колонок
 * @param rows строки (каждая — список значений в порядке {@code columns})
 * @param totalRows общее число строк в таблице
 */
public record RowGrid(List<String> columns, List<List<Object>> rows, long totalRows) {}
