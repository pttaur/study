package com.example.dbagg.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dbagg.model.Instance;
import com.example.dbagg.repository.CatalogRepository;
import com.example.dbagg.repository.InstanceRepository;
import com.example.dbagg.repository.StoragePostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@QuarkusTest
@QuarkusTestResource(StoragePostgresResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SchemaLoaderTest {
  @Inject InstanceRepository instanceRepository;

  @Inject CatalogRepository catalogRepository;

  @Inject SchemaLoader schemaLoader;

  @Inject CacheProvider cacheProvider;

  @Test
  @Order(1)
  void loadPopulatesCatalogFromTargetDb() {
    var instanceId =
        instanceRepository.saveInstance(
            new Instance(
                0,
                "loader-test",
                StoragePostgresResource.jdbcUrl() + "&ApplicationName=loader-test",
                StoragePostgresResource.username()),
            StoragePostgresResource.password());

    var catalog = schemaLoader.load(instanceId);

    assertThat(catalog.instanceId()).isEqualTo(instanceId);
    var schemas = catalog.schemas();
    assertThat(schemas).isNotEmpty();

    var publicSchema =
        schemas.stream().filter(s -> s.schemaName().equals("public")).findFirst().orElseThrow();
    assertThat(publicSchema.tables()).isNotEmpty();
  }

  @Test
  @Order(2)
  void loadedCatalogIsSavedAndCached() {
    var instanceId =
        instanceRepository.saveInstance(
            new Instance(
                0,
                "cache-test",
                StoragePostgresResource.jdbcUrl() + "&ApplicationName=cache-test",
                StoragePostgresResource.username()),
            StoragePostgresResource.password());

    var loaded = schemaLoader.load(instanceId);

    var fromCache = cacheProvider.get(instanceId);
    assertThat(fromCache).isPresent();
    assertThat(fromCache.get().schemas()).hasSameSizeAs(loaded.schemas());

    var fromStorage = catalogRepository.loadCatalog(instanceId);
    assertThat(fromStorage.schemas()).hasSameSizeAs(loaded.schemas());
  }
}
