package com.example.dbagg.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dbagg.model.Catalog;
import com.example.dbagg.model.Schema;
import java.util.List;
import org.junit.jupiter.api.Test;

class CacheProviderTest {
  @Test
  void putAndGetReturnCachedCatalog() {
    var cache = new CacheProvider();
    var catalog = new Catalog(7L, List.of());

    cache.put(7L, catalog);

    assertThat(cache.get(7L)).contains(catalog);
  }

  @Test
  void getReturnsEmptyForMissingInstance() {
    var cache = new CacheProvider();

    assertThat(cache.get(42L)).isEmpty();
  }

  @Test
  void removeEvictsCatalog() {
    var cache = new CacheProvider();
    cache.put(7L, new Catalog(7L, List.of()));

    cache.remove(7L);

    assertThat(cache.get(7L)).isEmpty();
  }

  @Test
  void clearEvictsEverything() {
    var cache = new CacheProvider();
    cache.put(
        1L, new Catalog(1L, List.of(new Schema(1, 1, "public", "postgres", null, List.of()))));

    cache.clear();

    assertThat(cache.get(1L)).isEmpty();
  }
}
