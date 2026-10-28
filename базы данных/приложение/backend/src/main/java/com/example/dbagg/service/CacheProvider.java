package com.example.dbagg.service;

import com.example.dbagg.model.Catalog;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кэш собранных каталогов схем в памяти.
 *
 * <p>Инстанс-БД подключаются редко и во время работы сервиса обычно неизменны, поэтому каталог
 * каждого инстанса кэшируется до явной инвалидации (пересборки схемы). Потокобезопасен: операции —
 * атомарные методы {@link ConcurrentHashMap}.
 */
@ApplicationScoped
public class CacheProvider {
  private final Map<Long, Catalog> catalogs = new ConcurrentHashMap<>();

  public Optional<Catalog> get(long instanceId) {
    return Optional.ofNullable(catalogs.get(instanceId));
  }

  public void put(long instanceId, Catalog catalog) {
    catalogs.put(instanceId, catalog);
  }

  public void remove(long instanceId) {
    catalogs.remove(instanceId);
  }

  public void clear() {
    catalogs.clear();
  }
}
