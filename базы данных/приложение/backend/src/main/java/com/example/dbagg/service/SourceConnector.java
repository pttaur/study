package com.example.dbagg.service;

import com.example.dbagg.model.Catalog;
import com.example.dbagg.model.DbKind;
import com.example.dbagg.model.Instance;

/**
 * Диалект источника: сбор каталога, ссылка на таблицу и маппинг типов колонок в типы внутреннего
 * PostgreSQL.
 */
public interface SourceConnector {
  DbKind kind();

  /** Собирает каталог схемы напрямую из целевой БД. */
  Catalog readCatalog(Instance instance, String password);

  /** Квалифицированная ссылка на таблицу с опциональным алиасом (диалект источника). */
  String tableReference(String schema, String table, String alias);

  /** Маппит имя типа колонки источника в тип внутреннего PostgreSQL. */
  String mapTypeName(String sourceType);
}
