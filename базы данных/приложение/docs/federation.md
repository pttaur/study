# Федеративные запросы: JOIN таблиц из разных СУБД

Цель — уметь в консоли писать SQL, который джойнит любые колонки любых таблиц
из разных подключённых СУБД.

## Зафиксированные решения

- **Самописный слой**, без сторонних движков (DuckDB/Trino/Calcite-as-engine).
- **Материализация в наш PostgreSQL**: подтаблицы вытягиваются по JDBC и
  загружаются во временные таблицы storage-PostgreSQL, где и выполняется JOIN.
- **Парсер — JSqlParser**, только для построения AST; планирование и исполнение — наши.
- **P1 — только PostgreSQL-источники**; архитектура готова к добавлению других СУБД.

## Архитектура

```
controller/JoinController                 POST /api/join, /api/join/history
service/
  FederationService                       оркестрация: parse → plan → materialize → execute
  QueryParser                             SQL → FederationQuery (через JSqlParser AST)
  FederationAnalyzer                      резолв таблиц, проекция колонок, пушдаун, переписывание имён
  TableResolver                           instance.schema.table → instance + metadata (каталог sc_*)
  SourceReader                            чтение подтаблиц по JDBC
  LocalMaterializer                       TEMP-таблицы в storage-PG + батч-загрузка
model/
  FederationQuery, TableRef, FederationPlan, SourcePlan, ResolvedTable, QueryResult
```

Коннекторы источников (`SourceConnector`): `PostgresConnector` (`information_schema`/`pg_catalog`)
и `MariaDbConnector` (`information_schema`, бэктики). Выбор по `DbKind` — в `ConnectorRegistry`.
Коннектор отвечает за сбор каталога, ссылку на таблицу (диалект алиаса: Postgres `AS "t"`,
MariaDB `` `t` ``) и маппинг типов колонок в типы внутреннего PostgreSQL.

## Нейминг таблиц

Полное имя в консоли: `instance.schema.table` (и колонки `instance.schema.table.column`).

```sql
SELECT u.id, o.total
FROM prod_pg.public.users u
JOIN sales_mysql.orders o ON o.user_id = u.id
WHERE u.active = true;
```

Первая часть (`instance`) — название инстанса (`sc_instances.label`).

## Поток выполнения

1. `POST /api/join` с моделью соединения (`tables`, `select`, лимиты). Бэкенд компилирует её в SQL (`JoinSqlBuilder`) и исполняет.
2. **Парсинг** (`QueryParser`) → `FederationQuery`: FROM/JOIN, ON, WHERE, SELECT, GROUP/ORDER/LIMIT.
   Разрешены только `SELECT`/`WITH`, ровно один стейтмент.
3. **Резолв** (`TableResolver`): `instance.schema.table` → инстанс и таблица каталога
   (`sc_instances`/`sc_schemas`/`sc_tables`/`sc_columns`).
4. **Планирование** (`FederationAnalyzer`):
   - нужные колонки каждой таблицы (из SELECT/ON/WHERE/ORDER);
   - предикаты, зависящие ровно от одной таблицы → пушдаун в источник;
   - кросс-табличные предикаты и соединения остаются для storage-PG;
   - имена `instance.schema.table` → `fed_<runId>_<n>`.
   Дополнительно отклоняются подзапросы и обращения к отношениям вне FROM/JOIN
   (`TablesNamesFinder`), в т.ч. к внутреннему хранилищу `sc_*`.
5. **Чтение** (`SourceReader`): `SELECT <cols> FROM schema.table WHERE <pushable>` по JDBC.
6. **Материализация** (`LocalMaterializer`): `CREATE TEMP TABLE fed_<runId>_<n>` на
   выделенном соединении storage-PG, пакетная загрузка (INSERT-батчи).
7. **Исполнение**: `SELECT * FROM (<outerSql>) AS fed_result LIMIT maxRows+1` на том же
   соединении, с `statement_timeout`.
8. **Ответ** `QueryResult`; запись в `sc_query_history`; закрытие соединения (TEMP-таблицы
   удаляются автоматически).

Важно: TEMP-таблицы живут в рамках сессии, поэтому шаги 6–8 выполняются на
**одном** соединении storage-PG.

## Модель данных (миграции `V4`, `V8`)

```sql
-- V4: тип СУБД и история запросов
ALTER TABLE sc_instances ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'POSTGRES';

-- снимаем старый CHECK на jdbc:postgresql://
ALTER TABLE sc_instances DROP CONSTRAINT ck_sc_instances_url;

CREATE TABLE sc_query_history (
    id          BIGSERIAL   PRIMARY KEY,
    sql_text    TEXT        NOT NULL,
    status      VARCHAR(20) NOT NULL,
    row_count   BIGINT,
    duration_ms BIGINT,
    error       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- V8: отдельный alias больше не нужен — в ссылках используется label
ALTER TABLE sc_instances DROP COLUMN IF EXISTS alias;
```

Актуальный набор `kind` задаётся в `V6__normalize_catalog.sql`:
`CHECK (kind IN ('POSTGRES','MARIADB','MYSQL','SQLITE','MSSQL','OTHER'))`.
Первая часть ссылки `instance.schema.table` — название инстанса (`sc_instances.label`, уникальное).

## API

| Метод  | Путь                     | Описание |
|--------|--------------------------|----------|
| `POST` | `/api/join`             | Выполнить джойн; тело — структура `{tables, select, maxRows, offset, timeoutMs}` |
| `GET`  | `/api/join/history`     | Последние джойны из `sc_query_history` |

Ответ `/api/join`:
```json
{
  "columns": [{"name": "id", "type": "int4"}, {"name": "total", "type": "numeric"}],
  "rows": [[1, 100], [2, 250]],
  "truncated": false,
  "durationMs": 42
}
```

## Frontend

- Конструктор джойнов (`JoinBuilder`) в основной области (переключатель `Данные | Джойн`):
  карточки таблиц, выбор колонок, условия соединения, типы INNER/LEFT/RIGHT/FULL.
- Таблицы добавляются кнопкой «+» в дереве слева или пикером `instance.schema.table`.
- Результат — `QueryResultView` (Результат / История), серверная пагинация.

## Типы и пушдаун

- Маппинг `ResultSetMetaData` → тип PG; при сомнении — `text` (P1: PG-источники — тождественно).
- Пушдаун реализован: в источник уходят **проекция нужных колонок** и **однотабличные
  предикаты** (в `FederationAnalyzer`); продавленные предикаты исключаются из внешнего SQL.
- Не продавливаются: предикаты с подзапросами и с неквалифицированными колонками.
- При `SELECT *`, `t.*` или неоднозначной колонке для таблицы берутся все колонки.
- Кросс-табличные условия и любые агрегаты выполняет storage-PG.

## Безопасность и лимиты

- Запрет DDL/DML и множественных стейтментов; только один `SELECT`. Подзапросы/CTE и обращения
  к отношениям вне источников (в т.ч. `sc_*`) отклоняются (`FederationAnalyzer`).
- Лимиты соединения паролей: URL валидируется по схеме и allowlist хостов (`SC_CONNECTION_ALLOWED_HOSTS`).
- `maxRows` (по умолчанию 1000, максимум 100 000), `timeoutMs` (по умолчанию 30s, максимум 300s),
  лимит строк на источник (`SourceReader.MAX_SOURCE_ROWS = 500_000`).
- Пароли — только через `CryptoUtil`; аудит в `sc_query_history`.
- Read-only транзакции на источниках и защита от декартова взрыва — **задел, не реализовано**.

## Роадмап

- **P0.** `QueryParser` (JSqlParser) + модель `FederationQuery` + `TableResolver` +
  `label/kind` в `sc_instances`. ✅
- **P1.** `FederationAnalyzer` (проекция + пушдаун однотабличных предикатов) + `SourceReader`
  (Postgres) + `LocalMaterializer` + `JoinController` + конструктор. ✅ **Рабочий PG→PG JOIN.**
- **P2.** Коннектор **MariaDB** (`MariaDbConnector`: `information_schema`, бэктики, маппинг типов
  MariaDB→PostgreSQL), выбор через `DbKind`. ✅ Oracle был заменён на MariaDB.
- **P3.** Стриминг больших результатов, кэш/материализация по требованию, история. ⏳
- **P4 (опц.).** Свой JVM-исполнитель джойна (хэш/merge) для случаев без storage-PG. ⏳

## E2E-проверка (docker-compose)

1. `docker-compose up --build` — поднимает PostgreSQL (миграции V1…V7), backend
   (`/api`, порт 8080) и frontend (http://localhost:3000).
2. Создать 1–2 инстанса (UI или `POST /api/instances`); название инстанса используется в ссылках.
3. Собрать схему каждого инстанса: `POST /api/instances/{id}/load`.
4. В основной области переключиться на «Джойн», добавить таблицы (кнопка «+» в дереве или пикер),
   отметить колонки и задать условие соединения, например `orders.user_id = users.id`.
5. Нажать «Выполнить»; результат — `GET`/`POST /api/join`; история — `GET /api/join/history`.

## Риски

- Сложность парсинга SQL (подзапросы, CTE, вложенные JOIN).
- Корректность квотинга при переписывании имён.
- Несовместимость типов колонок соединения между источниками.
- Производительность: даже с пушдауном данные материализуются; нет потоковой обработки.
- Безопасность: это SQL-gateway к прод-БД.
