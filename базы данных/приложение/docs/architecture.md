# Архитектура

## Обзор

Сервис собирает схемы PostgreSQL-инстансов, хранит снапшоты в собственной реляционной базе и позволяет сравнивать их между собой.

```
                    ┌────────────────────────────┐
                    │      Frontend (Svelte 5)   │
                    └─────────────┬──────────────┘
                                  │ REST / SSE
                    ┌─────────────▼──────────────┐
                    │    Backend (Quarkus 3)     │
                    │  controller → service      │
                    └─────────────┬──────────────┘
        ┌─────────────────────────┼─────────────────────────┐
        │                         │                         │
┌───────▼────────┐       ┌────────▼────────┐       ┌────────▼────────┐
│  Хранилище     │       │  Целевая БД #1  │       │  Целевая БД #2  │
│  схемы (PG)    │       │  (PostgreSQL)   │       │  (PostgreSQL)   │
│  sc_*          │       │  pg_catalog     │       │  pg_catalog     │
└────────────────┘       └─────────────────┘       └─────────────────┘
```

## Компоненты

### 1. Хранилище схемы (PostgreSQL)

Реляционная база с таблицами `sc_*`, хранящая снапшоты схем целевых инстансов:

- `sc_instances` — подключения к целевым БД (пароли шифруются через pgcrypto; `kind`)
- `sc_schemas` — схемы верхнего уровня
- `sc_tables` — таблицы, вьюхи, матвьюхи, foreign tables
- `sc_columns` — колонки
- `sc_constraints` + `sc_constraint_columns` — ограничения (PK/FK/UNIQUE/CHECK/EXCLUDE) и состав ключа с порядком
- `sc_indexes` + `sc_index_columns` — индексы и состав колонок
- `sc_constraints` + `sc_constraint_columns` — ограничения (PK/FK/UNIQUE/CHECK/EXCLUDE) и состав ключа с порядком
- `sc_indexes` + `sc_index_columns` — индексы и состав колонок
- `sc_query_history` — журнал федеративных запросов

Схема описана миграциями `sql/V1..V7`: `V1__create_schema.sql`, `V2__encrypt_password.sql`
(шифрование), `V3__snapshot_rows.sql`, `V4__federation.sql` (алиасы/kind/история),
`V5__constraints_and_triggers.sql` (CHECK статуса, триггер `updated_at`),
`V6__normalize_catalog.sql` (нормализация ограничений/индексов, MariaDB),
`V7__drop_sc_rows.sql` (удаление устаревшей таблицы снапшотов строк).

### 2. Backend (Quarkus 3)

| Слой | Классы | Ответственность |
|------|--------|-----------------|
| `model` | `Catalog`, `Schema`, `Table`, `Column`, `Constraint`(+`ConstraintColumn`), `Index`(+`IndexColumn`), `DbKind`, `DiffResult`, `RowGrid` | Объектная модель схемы и строк |
| `repository` | `InstanceRepository`, `CatalogRepository`, `QueryHistoryRepository` | Доступ к хранилищу `sc_*` через чистый SQL + JOOQ |
| `service` | `SchemaLoader`, `DiffService`, `CacheProvider`, `RowsService`, `CatalogEventBus`, `InstanceLookup`, `ConnectionUrlValidator`, `ConnectorRegistry`/`SourceConnector` (`PostgresConnector`, `MariaDbConnector`), `FederationService`/`FederationAnalyzer`/`QueryParser`/`TableResolver`/`SourceReader`/`LocalMaterializer`/`CatalogAssembler` | Сбор схемы, сравнение, кэширование, выборка строк, федеративные запросы, коннекторы СУБД (Postgres/MariaDB) |
| `controller` | `DbController`, `RowsController`, `JoinController` | REST и SSE API |
| `util` | `SqlQueries`, `CryptoUtil`, `RowsUtil` | SQL-константы, шифрование, хэши/JSON строк |

### 3. Frontend (Svelte 5)

| Компонент | Назначение |
|-----------|------------|
| `InstanceDock` | Левый док: вкладки «Инстансы» (форма, дерево) и «SQL»; сворачивание/ресайз |
| `SchemaTree` | Дерево «инстанс → схема → таблица» с составом ключей и индексов |
| `DataPanel` | Панель данных выбранной таблицы |
| `RowsExplorer` | Постраничный DataGrid, кнопки Compare/Capture baseline, хэш-дрифт, подсветка |
| `RowDiffView` | Постраничный построчный дифф (бок о бок: было / стало) |
| `SchemaDiffDrawer` | Выдвижная панель отличий схемы |
| `DiffViewer` | Отображение отличий схемы (добавлено/удалено/изменено) |
| `JoinBuilder` / `QueryResultView` | Визуальный конструктор джойнов и таблица результата |

Состояние вынесено в runes-модули `lib/instances.svelte.ts` и `lib/join.svelte.ts`.

## Поток данных

1. **Сбор схемы.** Пользователь инициирует загрузку (`SchemaLoader`): открывается соединение к целевой БД; коннектор по `DbKind` читает словарь данных (Postgres — `information_schema`/`pg_catalog`, MariaDB — `information_schema`).
2. **Сохранение.** Собранный `Catalog` транзакционно сохраняется (`CatalogRepository`: две фазы — таблицы, затем ограничения с резолвом FK), кэш обновляется, событие публикуется в `CatalogEventBus` (SSE).
3. **Сравнение схем.** `DiffService` сравнивает два каталога из кэша и возвращает `DiffResult`.
4. **Отображение.** Frontend отображает diff через `DiffViewer`/`SchemaDiffDrawer`.
5. **Просмотр строк.** Пользователь выбирает таблицу в `SchemaTree`; `RowsService` открывает соединение к целевой БД и постранично читает строки (`RowGrid`) с `ORDER BY` по PK, `RowsExplorer` рендерит DataGrid.
6. **Обновление и сравнение строк.** Кнопка **«Обновить и сравнить»** на фронте берёт строки «на экране» как было, читает свежие live-строки, строит построчный дифф (по первичному ключу) через `rowDiff`, обновляет таблицу и подсвечивает изменения в `RowDiffView`.

## Федеративные запросы

`FederationService`: parse (`QueryParser`) → plan (`FederationAnalyzer`: резолв таблиц `instance.schema.table`, проекция колонок, пушдаун однотабличных предикатов) → read (`SourceReader`) → materialize (`LocalMaterializer`: TEMP-таблицы в storage-PG) → execute. Запросы к отношениям вне источников (в т.ч. `sc_*`) отклоняются. Детали — [docs/federation.md](federation.md).

## Ключевые решения

- Схема хранится в реляционной БД `sc_*` (нормализованные ограничения/индексы с составом колонок)
- Пароли шифруются на уровне БД через расширение `pgcrypto`
- JOOQ используется только как тонкая обёртка для выполнения чистых SQL-строк, без генерации SQL
- Стиль Java энфорсится Spotless (google-java-format), фронтенд — Prettier + ESLint