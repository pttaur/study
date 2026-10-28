package com.example.dbagg.service;

import com.example.dbagg.model.DbKind;
import com.example.dbagg.model.Instance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import java.util.EnumMap;
import java.util.Map;

/**
 * Выбирает коннектор источника по типу СУБД инстанса.
 *
 * <p>Все зарегистрированные бины {@link SourceConnector} собираются автоматически, поэтому новый
 * тип СУБД достаточно реализовать коннектором — реестр менять не нужно.
 */
@ApplicationScoped
public class ConnectorRegistry {
  private final Map<DbKind, SourceConnector> connectors;

  @Inject
  public ConnectorRegistry(@Any jakarta.enterprise.inject.Instance<SourceConnector> connectors) {
    var byKind = new EnumMap<DbKind, SourceConnector>(DbKind.class);
    for (var connector : connectors) {
      byKind.put(connector.kind(), connector);
    }
    this.connectors = byKind;
  }

  public SourceConnector forKind(DbKind kind) {
    var connector = connectors.get(kind);
    if (connector == null) {
      throw new IllegalArgumentException("No source connector registered for kind: " + kind);
    }
    return connector;
  }

  public SourceConnector forInstance(Instance instance) {
    return forKind(DbKind.fromCode(instance.kind()));
  }
}
