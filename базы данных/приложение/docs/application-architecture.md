# Schema Catalog Aggregator — архитектура приложения и структура БД

Актуально на текущую версию кода (`backend`, `frontend`, `sql/V1..V6`).

---

## 1. Обзор

Сервис каталогизирует схемы нескольких СУБД-источников (PostgreSQL, MariaDB),
хранит их снапшоты в собственной PostgreSQL-базе (`sc_*`), умеет сравнивать схемы,
контролировать дрейф данных (row-level) и выполнять **федеративные SQL-запросы** с
JOIN таблиц из разных источников.

```
┌──────────────────────────┐        REST / SSE        ┌──────────────────────────┐
│   Frontend (Svelte 5)    │ ◄──────────────────────► │   Backend (Quarkus 3)    │
│   http://localhost:3000  │                          │   http://localhost:8080  │
└──────────────────────────┘                          └────────────┬─────────────┘
                                                                    │ JDBC (read-only)
                          storage JDBC (rw)                         │
        ┌───────────────────────────────┐              ┌────────────┴─────────────┐
        │  Хранилище (PostgreSQL 17)    │              │  Целевые БД-источники    │
        │  db:5432/scagg, таблицы sc_*  │              │  PostgreSQL / MariaDB    │
        └───────────────────────────────┘              └──────────────────────────┘
```

Контейнеры (`docker-compose.yml`):

| Сервис | Образ | Порт | Назначение |
|--------|-------|------|------------|
| `db` | `postgres:17-alpine` | 5432 (внутри) | Хранилище `sc_*`, миграции V1..V6 |
| `backend` | maven → temurin 25 | `8080:8080` | REST/SSE API |
| `frontend` | node 22 → nginx | `3000:80` | Статика SPA + прокси `/api` |

Технологии: **Java 25, Quarkus 3.22, JOOQ (тонкая JDBC-обёртка), JSqlParser 5.4,
pgcrypto, Testcontainers** (тесты); **Svelte 5 (runes), TypeScript, Vite, Vitest** (фронт).

---

## 2. Backend (Quarkus)

Пакет `com.example.dbagg`, слои: `controller → service → repository → model/util`.

### 2.1. Controllers (REST / SSE), root `/api`

`DbController` — инстансы и схемы:

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/instances` | Список инстансов |
| GET | `/instances/{id}` | Инстанс по id |
| POST | `/instances` | Создать инстанс (валидация URL/kind) |
| PATCH | `/instances/{id}/password` | Сменить пароль |
| POST | `/instances/{id}/load` | Собрать схему → сохранить → кэш → SSE-publish |
| POST | `/instances/{id}/check` | Дрейф схемы (эталон vs live) |
| GET | `/instances/{id}/schema` | Каталог (кэш, иначе хранилище) |
| POST | `/instances/compare` | Сравнить две схемы |
| GET | `/instances/{id}/events` | **SSE**-поток каталогов |

`RowsController` — эксплорер строк, база `/instances/{id}/tables/{schema}/{table}/rows`:

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `` | Страница строк (`limit`, `offset`), детерминированный `ORDER BY` по PK |

`JoinController` — конструктор джойнов, база `/join`:

| Метод | Путь | Описание |
|-------|------|----------|
| POST | `` | Выполнить джойн (структура соединения; компилируется в SQL и исполняется) |
| GET | `/history` | Последние джойны (`sc_query_history`) |

### 2.2. Services

| Класс | Ответственность |
|-------|-----------------|
| `SchemaLoader` | Сбор каталога через коннектор → `CatalogRepository.saveCatalog` → `CacheProvider` |
| `ConnectorRegistry` | Выбор `SourceConnector` по `DbKind` (CDI-набор) |
| `PostgresConnector` | Каталог PG: `information_schema` + `pg_catalog`, двойные кавычки |
| `MariaDbConnector` | Каталог MariaDB: `information_schema`, бэктики, маппинг типов |
| `CatalogAssembler` | Общая сборка `Schema/Table` из сгруппированных строк словаря |
| `SourceConnection` | Открытие соединения к источнику **read-only** |
| `DiffService` | Сравнение каталогов по именам и сигнатурам → `DiffResult` |
| `CacheProvider` | In-memory кэш каталогов (`ConcurrentHashMap`) |
| `CatalogEventBus` | In-memory SSE-шина каталогов (Mutiny emitters) |
| `RowsService` | Live-постраничная выборка строк |
| `InstanceLookup` | Проверка существования инстанса |
| `ConnectionUrlValidator` | Схема JDBC под `DbKind` + allowlist хостов (anti-SSRF) |
| `FederationService` | Оркестрация федеративного запроса |
| `QueryParser` | SQL → `FederationQuery` (JSqlParser AST, один SELECT) |
| `FederationAnalyzer` | Резолв таблиц, проекция колонок, пушдаун предикатов, переписывание имён |
| `TableResolver` | `instance.schema.table` → инстанс + запись каталога |
| `SourceReader` | Чтение подтаблицы по JDBC (лимит 500 000 строк) |
| `LocalMaterializer` | `CREATE TEMP TABLE` + пакетная загрузка во внутреннем PG |

### 2.3. Repositories (JOOQ, чистый SQL из `util/SqlQueries`)

| Класс | Таблицы |
|-------|---------|
| `InstanceRepository` | `sc_instances` (+ `CryptoUtil` для паролей) |
| `CatalogRepository` | `sc_schemas`, `sc_tables`, `sc_columns`, `sc_constraints`(+`_columns`), `sc_indexes`(+`_columns`) |
| `QueryHistoryRepository` | `sc_query_history` |

### 2.4. Util

| Класс | Назначение |
|-------|------------|
| `SqlQueries` | Все SQL-константы (хранилище + диалекты источников) |
| `CryptoUtil` | pgcrypto: `pgp_sym_encrypt/decrypt` |
| `RowsUtil` | Квотинг идентификаторов SQL |

### 2.5. Модель (records)

`Catalog, Schema, Table, Column, Constraint(+ConstraintColumn), Index(+IndexColumn),
Instance, DbKind, TableKind, ConstraintType, IndexKind, DiffResult/SchemaDiff/TableDiff/LeafDiff,
RowGrid, QueryRequest, QueryResult, ColumnInfo, QueryHistoryEntry,
FederationQuery, FederationPlan, SourcePlan, ResolvedTable, TableRef, SourceData`.

---

## 3. Потоки данных

### 3.1. Сбор и сохранение схемы
```
POST /instances/{id}/load
  SchemaLoader.load
    InstanceLookup.require → InstanceRepository.decryptPassword
    ConnectorRegistry.forInstance(kind) → PostgresConnector|MariaDbConnector
      readCatalog (read-only соединение)
    CatalogRepository.saveCatalog (транзакция, 2 фазы):
      1) sc_schemas → sc_tables → sc_columns, sc_indexes(+columns)
      2) sc_constraints (FK: резолв ref_table_id; нерезолвимые FK пропускаются с логом)
         + sc_constraint_columns
    CacheProvider.put → CatalogEventBus.publish → SSE
```

### 3.2. Дрейф схемы
```
POST /instances/{id}/check
  baseline = cache ?? CatalogRepository.loadCatalog
  live     = SchemaLoader.collectLive
  DiffService.compare → DiffResult (сравнение по именам и «сигнатурам», не по id)
```

### 3.3. Строки
```
GET  …/rows?limit&offset   → live-выборка (ORDER BY по PK из DatabaseMetaData)
```
Построчный diff на клиенте: кнопка «Обновить и сравнить» берёт строки «на экране» как «было»,
читает свежие live-строки и строит дифф по PK (`frontend/src/lib/rowDiff.ts`), затем обновляет
таблицу и подсвечивает изменения.

### 3.4. Федеративный запрос
```
POST /join {tables, select, maxRows, offset, timeoutMs}
  JoinSqlBuilder.build         → SQL (квотинг, алиасы t0..tN, ORDER BY по выбранным колонкам)
  QueryParser.parse            → один PlainSelect (DDL/DML/несколько стейтментов/CTE — отказ)
  FederationAnalyzer.analyze   → FederationPlan
    • сбор FROM/JOIN; подзапросы/отношения вне источников (в т.ч. sc_*) — отказ
    • проекция нужных колонок; однотабличные предикаты WHERE → пушдаун
    • переименование таблиц → fed_<runId>_<n>
  FederationService.doExecute  (одно соединение storage-PG):
    read источников  → SourceReader (read-only JDBC)
    materialize      → LocalMaterializer (TEMP TABLE, батчи по 1000)
    count(*)         → totalRows
    SELECT * FROM (<outerSql>) fed_result LIMIT maxRows OFFSET offset
    запись в sc_query_history
```

---

## 4. Frontend (Svelte 5, runes)

Состояние — в runes-модулях, компоненты — композиция.

| Файл | Роль |
|------|------|
| `App.svelte` | Композиция: док, ресайз, контент, дифф-дровер |
| `lib/instances.svelte.ts` | `InstanceStore`: инстансы, каталоги (SvelteMap), диффы, EventSource |
| `lib/join.svelte.ts` | `JoinStore`: таблицы, соединения, выбор колонок, результат, план, история |
| `lib/api.ts` | Клиент REST/SSE |
| `lib/types.ts` | Типы домена |
| `lib/rowDiff.ts` | `diffRows`, `annotateRows`, `primaryKeyColumns` |
| `lib/format.ts`, `lib/errors.ts` | Утилиты |
| `components/InstanceDock.svelte` | Вкладки «Инстансы» / «SQL» |
| `components/SchemaTree.svelte` | Дерево «инстанс → схема → таблица» (ключи, FK, индексы) |
| `components/DataPanel.svelte` / `RowsExplorer.svelte` / `RowDiffView.svelte` | Данные и построчный дифф |
| `components/SchemaDiffDrawer.svelte` / `DiffViewer.svelte` | Дрейф схемы |
| `components/JoinBuilder.svelte` / `QueryResultView.svelte` | Конструктор джойнов и Результат / История |

---

## 5. Структура БД хранилища (`sc_*`, схема `public`)

Полное DDL — миграции `sql/V1..V7`. Итоговое состояние:

### 5.1. ER-диаграмма
```
sc_instances 1──∞ sc_schemas 1──∞ sc_tables 1──∞ sc_columns
     │                                   │       sc_constraints 1──∞ sc_constraint_columns
     │                                   └───── sc_indexes     1──∞ sc_index_columns
     └──∞ sc_query_history

sc_constraints.ref_table_id ──► sc_tables.id  (самоссылка FK)
```

### 5.2. `sc_instances` — источники
| Колонка | Тип | Прим. |
|---------|-----|-------|
| id | bigserial PK | |
| label | text | UNIQUE |
| connection_url | varchar(255) | UNIQUE, len ≤ 255 |
| username | text | |
| password_encrypted | bytea | `pgp_sym_encrypt`, NOT NULL |
| kind | varchar(20) | default `POSTGRES`, CHECK: POSTGRES/MARIADB/MYSQL/SQLITE/MSSQL/OTHER |
| created_at / updated_at | timestamptz | `updated_at` через триггер `sc_instances_set_updated_at` |

### 5.3. `sc_schemas` — схемы
`id PK`, `instance_id FK→sc_instances(CASCADE)`, `schema_name`, `owner`, `comment`;
UNIQUE `(instance_id, schema_name)`.

### 5.4. `sc_tables` — таблицы/вьюхи
`id PK`, `schema_id FK→sc_schemas(CASCADE)`, `table_name`, `kind`; UNIQUE `(schema_id, table_name)`;
CHECK kind: TABLE/PARTITIONED_TABLE/VIEW/MATERIALIZED_VIEW/FOREIGN_TABLE.

### 5.5. `sc_columns` — колонки
`id PK`, `table_id FK→sc_tables(CASCADE)`, `column_name`, `ordinal_position`, `data_type`,
`is_nullable`, `default_value`, `is_generated`; UNIQUE `(table_id, column_name)` и
`(table_id, ordinal_position)`; CHECK `ordinal_position > 0`.

### 5.6. `sc_constraints` — ограничения (нормализованные)
| Колонка | Тип | Прим. |
|---------|-----|-------|
| id | bigserial PK | |
| table_id | bigint FK→sc_tables(CASCADE) | |
| constraint_name | text | UNIQUE `(table_id, constraint_name)` |
| type | varchar(20) | PK/FOREIGN_KEY/UNIQUE/CHECK/EXCLUDE |
| expression | text | тело CHECK/EXCLUDE, иначе NULL |
| ref_table_id | bigint FK→sc_tables(CASCADE) | целевая таблица FK, иначе NULL |
| on_delete / on_update | varchar(20) | для FK: CASCADE/SET_NULL/SET_DEFAULT/RESTRICT/NO_ACTION |

Взаимоисключающие CHECK: `ck_sc_constraints_fk` (ref_table_id только для FK),
`ck_sc_constraints_expr` (expression только для CHECK/EXCLUDE),
`ck_sc_constraints_rules` (on_delete/on_update только для FK).

### 5.7. `sc_constraint_columns` — состав ключа
`id PK`, `constraint_id FK→sc_constraints(CASCADE)`, `column_name`, `position` (>0),
`ref_column_name` (для FK — колонка в целевой таблице); UNIQUE `(constraint_id, column_name)`
и `(constraint_id, position)`.

### 5.8. `sc_indexes` — индексы
`id PK`, `table_id FK→sc_tables(CASCADE)`, `index_name`, `is_unique`, `is_primary`,
`kind` (BTREE/HASH/GIN/GIST/SPGIST/BRIN), `definition` (полный DDL, для выражений/partial);
UNIQUE `(table_id, index_name)`.

### 5.9. `sc_index_columns` — состав индекса
`id PK`, `index_id FK→sc_indexes(CASCADE)`, `column_name`, `position` (>0);
UNIQUE `(index_id, column_name)` и `(index_id, position)`.

### 5.10. `sc_query_history` — журнал федеративных запросов
`id PK`, `sql_text`, `status` (CHECK: OK/ERROR/TIMEOUT/CANCELLED), `row_count`,
`duration_ms`, `error`, `created_at`; индекс `(created_at DESC)`.

### 5.11. Вспомогательные объекты
- `pgcrypto` — шифрование паролей.
- `trg_set_updated_at()` + триггер `sc_instances_set_updated_at` (BEFORE UPDATE).

---

## 6. Лимиты и безопасность

| Параметр | Значение |
|----------|----------|
| `maxRows` | default 1000, max 100 000 |
| `timeoutMs` | default 30 000, max 300 000 |
| строк из одного источника | 500 000 (`SourceReader.MAX_SOURCE_ROWS`) |
| страница DataGrid | default 100, max 200 |
| снапшот строк | default 200, `rows_json` ≤ 1 MB |
| история запросов | последние 50 |

- Пароли — только через `CryptoUtil` (pgcrypto); ключ `SCHEMA_ENCRYPT_KEY` (без дефолта).
- Федеративный SQL отклоняет подзапросы и отношения вне источников (чтение `sc_*` невозможно).
- `connectionUrl` валидируется по схеме под `DbKind`; allowlist хостов `SC_CONNECTION_ALLOWED_HOSTS`.
- Соединения к источникам — **read-only** (`SourceConnection`).
- **REST/SSE не аутентифицированы** — рассчитано на доверенный периметр/шлюз.

---

## 7. Переменные окружения

| Переменная | Default | Назначение |
|------------|---------|------------|
| `SC_STORAGE_URL` | `jdbc:postgresql://localhost:5432/scagg` | JDBC хранилища |
| `SC_STORAGE_USER` / `SC_STORAGE_PASSWORD` | `scagg` / `scagg` | Доступ к хранилищу |
| `SCHEMA_ENCRYPT_KEY` | — (обязателен) | Ключ pgcrypto |
| `SC_CONNECTION_ALLOWED_HOSTS` | пусто | Allowlist хостов источников |

---

## 8. Сборка и запуск

```bash
docker compose up -d --build          # db + backend(8080) + frontend(3000)
docker compose exec -T db psql -U scagg -d scagg < sql/V6__normalize_catalog.sql  # на существующем томе
```

Проверки качества:
```bash
# backend
docker run --rm --network host -e TESTCONTAINERS_RYUK_DISABLED=true \
  -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD/backend":/build -w /build \
  maven:3.9-eclipse-temurin-25 sh -c 'mvn -B -q spotless:apply && mvn -B -q test'
# frontend
npm run check && npm run lint && npx vitest run
```

---

## 9. Ключевые решения

- Хранилище — нормализованные реляционные `sc_*` (составные ключи/FK/индексы вынесены в
  отдельные таблицы).
- JOOQ — только как тонкая обёртка над JDBC, SQL — в `SqlQueries` (без ORM/генерации).
- Diff — по именам и сигнатурам, а не по id хранилища (устойчив к пересборке каталога).
- Федерация — самописный слой: материализация подтаблиц во временные таблицы storage-PG,
  где и выполняется JOIN; JSqlParser — только парсинг AST.
- Форматирование: Spotless + google-java-format (Java), Prettier + ESLint (frontend).
