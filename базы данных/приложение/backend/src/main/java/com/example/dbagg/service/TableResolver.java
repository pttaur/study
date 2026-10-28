package com.example.dbagg.service;

import com.example.dbagg.model.Catalog;
import com.example.dbagg.model.Column;
import com.example.dbagg.model.ResolvedTable;
import com.example.dbagg.model.Table;
import com.example.dbagg.model.TableRef;
import com.example.dbagg.repository.CatalogRepository;
import com.example.dbagg.repository.InstanceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;

/**
 * Сопоставляет {@link TableRef} из федеративного запроса инстансу и таблице каталога.
 *
 * <p>Алиас инстанса обязателен; если схема в запросе не указана — таблица ищется по всем схемам
 * инстанса.
 */
@ApplicationScoped
public class TableResolver {
  private final InstanceRepository instanceRepository;
  private final CatalogRepository catalogRepository;

  @Inject
  public TableResolver(InstanceRepository instanceRepository, CatalogRepository catalogRepository) {
    this.instanceRepository = instanceRepository;
    this.catalogRepository = catalogRepository;
  }

  public ResolvedTable resolve(TableRef ref) {
    if (ref.instanceName() == null || ref.instanceName().isBlank()) {
      throw new IllegalArgumentException("Instance name is required for table: " + ref.tableName());
    }
    var instance =
        instanceRepository
            .findInstanceByLabel(ref.instanceName())
            .orElseThrow(
                () -> new IllegalArgumentException("Unknown instance: " + ref.instanceName()));
    var catalog = catalogRepository.loadCatalog(instance.id());
    var found =
        findTable(catalog, ref.schemaName(), ref.tableName())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Table not found in catalog: "
                            + instance.label()
                            + "."
                            + (ref.schemaName() == null ? "*" : ref.schemaName())
                            + "."
                            + ref.tableName()));
    var columns = found.table().columns().stream().map(Column::columnName).toList();
    return new ResolvedTable(
        instance.id(),
        instance.label(),
        found.table().id(),
        found.schemaName(),
        found.table().tableName(),
        columns);
  }

  /** Ищет таблицу в каталоге; при {@code schemaName == null} — по всем схемам. */
  static Optional<FoundTable> findTable(Catalog catalog, String schemaName, String tableName) {
    return catalog.schemas().stream()
        .filter(schema -> schemaName == null || schema.schemaName().equals(schemaName))
        .flatMap(
            schema ->
                schema.tables().stream().map(table -> new FoundTable(schema.schemaName(), table)))
        .filter(found -> found.table().tableName().equals(tableName))
        .findFirst();
  }

  record FoundTable(String schemaName, Table table) {}
}
