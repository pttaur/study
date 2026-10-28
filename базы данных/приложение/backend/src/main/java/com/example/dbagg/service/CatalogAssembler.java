package com.example.dbagg.service;

import com.example.dbagg.model.Column;
import com.example.dbagg.model.Constraint;
import com.example.dbagg.model.Index;
import com.example.dbagg.model.Schema;
import com.example.dbagg.model.Table;
import com.example.dbagg.model.TableKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Общая сборка каталога из уже сгруппированных строк словаря данных источника. Убирает дублирование
 * между коннекторами: каждый из них лишь поставляет сгруппированные строки в своём диалекте.
 */
final class CatalogAssembler {
  private CatalogAssembler() {}

  record TableEntry(String schemaName, String tableName, String key) {
    static TableEntry of(String schemaName, String tableName) {
      return new TableEntry(schemaName, tableName, schemaName + "." + tableName);
    }
  }

  static List<Schema> assemble(
      long instanceId,
      Set<String> schemaNames,
      Map<String, String> ownerByName,
      Map<String, List<TableEntry>> tablesBySchema,
      Function<TableEntry, TableKind> kindOf,
      Map<String, List<Column>> columnsByKey,
      Map<String, List<Constraint>> constraintsByKey,
      Map<String, List<Index>> indexesByKey) {
    long schemaId = 0;
    long tableId = 0;
    var schemas = new ArrayList<Schema>(schemaNames.size());
    for (var schemaName : schemaNames) {
      var schemaTables = new ArrayList<Table>();
      for (var entry : tablesBySchema.getOrDefault(schemaName, List.of())) {
        schemaTables.add(
            new Table(
                ++tableId,
                0,
                entry.tableName(),
                kindOf.apply(entry),
                columnsByKey.getOrDefault(entry.key(), List.of()),
                constraintsByKey.getOrDefault(entry.key(), List.of()),
                indexesByKey.getOrDefault(entry.key(), List.of())));
      }
      schemaTables.sort(Comparator.comparing(Table::tableName));
      schemas.add(
          new Schema(
              ++schemaId, instanceId, schemaName, ownerByName.get(schemaName), null, schemaTables));
    }
    return schemas;
  }
}
