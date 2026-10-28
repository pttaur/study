package com.example.dbagg.service;

import com.example.dbagg.model.Catalog;
import com.example.dbagg.model.Column;
import com.example.dbagg.model.Constraint;
import com.example.dbagg.model.ConstraintColumn;
import com.example.dbagg.model.DiffResult;
import com.example.dbagg.model.DiffType;
import com.example.dbagg.model.Index;
import com.example.dbagg.model.IndexColumn;
import com.example.dbagg.model.LeafDiff;
import com.example.dbagg.model.Schema;
import com.example.dbagg.model.SchemaDiff;
import com.example.dbagg.model.Table;
import com.example.dbagg.model.TableDiff;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Сравнение двух каталогов схем и построение иерархического {@link DiffResult}.
 *
 * <p>Сравнение выполняется по именам схем/таблиц/колонок/ограничений/индексов, поэтому результат
 * детерминирован и не зависит от идентификаторов внутри хранилища. Содержимое сравнивается по
 * «сигнатуре» (атрибуты, кроме id) — так элементы с разными id в разных инстансах корректно
 * считаются неизменными. Элементы без изменений в результат не попадают. Инстансы для сравнения
 * берутся из {@link CacheProvider}.
 */
@ApplicationScoped
public class DiffService {
  private final CacheProvider cacheProvider;

  @Inject
  public DiffService(CacheProvider cacheProvider) {
    this.cacheProvider = cacheProvider;
  }

  public DiffResult compare(long baseInstanceId, long compareInstanceId) {
    var base = catalogOrThrow(baseInstanceId);
    var compare = catalogOrThrow(compareInstanceId);
    return compare(baseInstanceId, compareInstanceId, base, compare);
  }

  public DiffResult compare(Catalog base, Catalog compare) {
    return compare(base.instanceId(), compare.instanceId(), base, compare);
  }

  DiffResult compare(long baseInstanceId, long compareInstanceId, Catalog base, Catalog compare) {
    var baseSchemas = indexByName(base.schemas(), Schema::schemaName);
    var compareSchemas = indexByName(compare.schemas(), Schema::schemaName);

    var schemaDiffs = new ArrayList<SchemaDiff>();
    for (var entry : baseSchemas.entrySet()) {
      var compareSchema = compareSchemas.get(entry.getKey());
      if (compareSchema == null) {
        schemaDiffs.add(allTablesDiff(entry.getValue(), DiffType.REMOVED));
      } else {
        var tableDiffs = compareTables(entry.getValue().tables(), compareSchema.tables());
        if (!tableDiffs.isEmpty()
            || !Objects.equals(schemaSignature(entry.getValue()), schemaSignature(compareSchema))) {
          schemaDiffs.add(new SchemaDiff(entry.getKey(), DiffType.MODIFIED, tableDiffs));
        }
      }
    }
    for (var entry : compareSchemas.entrySet()) {
      if (!baseSchemas.containsKey(entry.getKey())) {
        schemaDiffs.add(allTablesDiff(entry.getValue(), DiffType.ADDED));
      }
    }

    schemaDiffs.sort(Comparator.comparing(SchemaDiff::name));
    return new DiffResult(baseInstanceId, compareInstanceId, schemaDiffs);
  }

  private Catalog catalogOrThrow(long instanceId) {
    return cacheProvider
        .get(instanceId)
        .orElseThrow(
            () -> new IllegalArgumentException("Catalog not cached for instance " + instanceId));
  }

  private List<TableDiff> compareTables(List<Table> baseTables, List<Table> compareTables) {
    var baseIndex = indexByName(baseTables, Table::tableName);
    var compareIndex = indexByName(compareTables, Table::tableName);

    var tableDiffs = new ArrayList<TableDiff>();
    for (var entry : baseIndex.entrySet()) {
      var compareTable = compareIndex.get(entry.getKey());
      if (compareTable == null) {
        tableDiffs.add(
            new TableDiff(entry.getKey(), DiffType.REMOVED, List.of(), List.of(), List.of()));
      } else {
        var columns =
            compareLeaf(
                entry.getValue().columns(),
                compareTable.columns(),
                Column::columnName,
                this::columnSignature);
        var constraints =
            compareLeaf(
                entry.getValue().constraints(),
                compareTable.constraints(),
                Constraint::constraintName,
                this::constraintSignature);
        var indexes =
            compareLeaf(
                entry.getValue().indexes(),
                compareTable.indexes(),
                Index::indexName,
                this::indexSignature);
        if (!columns.isEmpty()
            || !constraints.isEmpty()
            || !indexes.isEmpty()
            || !Objects.equals(tableSignature(entry.getValue()), tableSignature(compareTable))) {
          tableDiffs.add(
              new TableDiff(entry.getKey(), DiffType.MODIFIED, columns, constraints, indexes));
        }
      }
    }
    for (var entry : compareIndex.entrySet()) {
      if (!baseIndex.containsKey(entry.getKey())) {
        tableDiffs.add(
            new TableDiff(entry.getKey(), DiffType.ADDED, List.of(), List.of(), List.of()));
      }
    }

    tableDiffs.sort(Comparator.comparing(TableDiff::name));
    return tableDiffs;
  }

  private <T> List<LeafDiff> compareLeaf(
      List<T> baseItems,
      List<T> compareItems,
      Function<T, String> nameFn,
      Function<T, List<Object>> signatureFn) {
    var baseIndex = indexByName(baseItems, nameFn);
    var compareIndex = indexByName(compareItems, nameFn);

    var diffs = new ArrayList<LeafDiff>();
    for (var entry : baseIndex.entrySet()) {
      var compareItem = compareIndex.get(entry.getKey());
      if (compareItem == null) {
        diffs.add(new LeafDiff(entry.getKey(), DiffType.REMOVED));
      } else if (!signatureFn.apply(entry.getValue()).equals(signatureFn.apply(compareItem))) {
        diffs.add(new LeafDiff(entry.getKey(), DiffType.MODIFIED));
      }
    }
    for (var entry : compareIndex.entrySet()) {
      if (!baseIndex.containsKey(entry.getKey())) {
        diffs.add(new LeafDiff(entry.getKey(), DiffType.ADDED));
      }
    }

    diffs.sort(Comparator.comparing(LeafDiff::name));
    return diffs;
  }

  private List<Object> columnSignature(Column c) {
    return Arrays.asList(
        c.ordinalPosition(), c.dataType(), c.nullable(), c.defaultValue(), c.generated());
  }

  private List<Object> constraintSignature(Constraint c) {
    return Arrays.asList(
        c.type(),
        c.columns().stream().map(ConstraintColumn::columnName).toList(),
        c.columns().stream().map(ConstraintColumn::refColumnName).toList(),
        c.refTable(),
        c.onDelete(),
        c.onUpdate(),
        normalize(c.expression()));
  }

  private List<Object> indexSignature(Index i) {
    return Arrays.asList(
        i.unique(),
        i.primary(),
        i.kind(),
        i.columns().stream().map(IndexColumn::columnName).toList(),
        normalize(i.definition()));
  }

  private static String normalize(String value) {
    return value == null ? null : value.replaceAll("\\s+", " ").trim();
  }

  private List<Object> tableSignature(Table t) {
    return Arrays.asList(t.kind());
  }

  private List<Object> schemaSignature(Schema s) {
    return Arrays.asList(s.owner(), s.comment());
  }

  private SchemaDiff allTablesDiff(Schema schema, DiffType type) {
    return new SchemaDiff(
        schema.schemaName(),
        type,
        schema.tables().stream()
            .map(Table::tableName)
            .sorted()
            .map(name -> new TableDiff(name, type, List.of(), List.of(), List.of()))
            .toList());
  }

  private <T> Map<String, T> indexByName(List<T> items, Function<T, String> nameFn) {
    var index = new LinkedHashMap<String, T>();
    for (var item : items) {
      index.put(nameFn.apply(item), item);
    }
    return index;
  }
}
