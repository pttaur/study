package com.example.dbagg.service;

import com.example.dbagg.model.Catalog;
import com.example.dbagg.model.Instance;
import com.example.dbagg.repository.CatalogRepository;
import com.example.dbagg.repository.InstanceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Сбор каталога схемы целевого инстанса через коннектор, соответствующий типу СУБД ({@link
 * ConnectorRegistry}). Каталог сохраняется в хранилище {@code sc_*} и кладётся в {@link
 * CacheProvider}.
 */
@ApplicationScoped
public class SchemaLoader {
  private static final Logger log = LoggerFactory.getLogger(SchemaLoader.class);

  private final CatalogRepository catalogRepository;
  private final InstanceRepository instanceRepository;
  private final CacheProvider cacheProvider;
  private final ConnectorRegistry connectorRegistry;
  private final InstanceLookup instanceLookup;

  @Inject
  public SchemaLoader(
      CatalogRepository catalogRepository,
      InstanceRepository instanceRepository,
      CacheProvider cacheProvider,
      ConnectorRegistry connectorRegistry,
      InstanceLookup instanceLookup) {
    this.catalogRepository = catalogRepository;
    this.instanceRepository = instanceRepository;
    this.cacheProvider = cacheProvider;
    this.connectorRegistry = connectorRegistry;
    this.instanceLookup = instanceLookup;
  }

  /**
   * Собирает каталог инстанса, сохраняет его в хранилище и обновляет кэш.
   *
   * @param instanceId идентификатор инстанса в sc_instances
   * @return собранный каталог
   */
  public Catalog load(long instanceId) {
    var instance = instanceLookup.require(instanceId);
    var catalog = loadFromTarget(instance);
    log.info("Loaded catalog for instance {}: {} schemas", instanceId, catalog.schemas().size());
    catalogRepository.saveCatalog(catalog);
    cacheProvider.put(instanceId, catalog);
    return catalog;
  }

  /**
   * Собирает каталог инстанса «вживую» напрямую из целевой БД, не сохраняя его в хранилище.
   * Используется для контроля изменений (drift).
   *
   * @param instanceId идентификатор инстанса в sc_instances
   * @return свежесобранный каталог
   */
  public Catalog collectLive(long instanceId) {
    var instance = instanceLookup.require(instanceId);
    var catalog = loadFromTarget(instance);
    log.info(
        "Collected live catalog for instance {}: {} schemas", instanceId, catalog.schemas().size());
    return catalog;
  }

  private Catalog loadFromTarget(Instance instance) {
    var password = instanceRepository.decryptPassword(instance.id());
    return connectorRegistry.forInstance(instance).readCatalog(instance, password);
  }
}
