# Schema Catalog Aggregator

Сервис для сбора, хранения и сравнения схем СУБД (PostgreSQL, MariaDB), расположенных
на разных серверах, с федеративными SQL-запросами между источниками.

## Возможности

- Подключение к инстансам PostgreSQL и MariaDB по логину и паролю
- Сбор схемы через словарь данных источника (`information_schema`/`pg_catalog` — PostgreSQL,
  `information_schema` — MariaDB), включая ограничения и индексы с составом колонок
- Хранение снапшотов схемы в реляционной БД (PostgreSQL)
- Сравнение двух хранящихся схем с отображением отличий
- Эксплорер строк таблиц: постраничный просмотр данных и построчный дифф «было ↔ стало»
  (кнопка «Обновить и сравнить», добавлено/удалено/изменено, по первичному ключу)
- **Конструктор джойнов**: визуальная сборка JOIN таблиц из разных СУБД (без SQL), план и история
- Шифрование паролей подключения (pgcrypto)
- REST + SSE API
- Структурированные JSON-логи

## Архитектура

| Компонент | Технология |
|-----------|------------|
| Backend | Java 25, Quarkus 3, JOOQ (как обёртка над чистым SQL), JSqlParser |
| Frontend | Svelte 5 (Runes), TypeScript, Vite |
| Хранилище схемы | PostgreSQL |
| Развёртывание | Docker Compose |

Подробнее: [docs/architecture.md](docs/architecture.md) ·
[docs/application-architecture.md](docs/application-architecture.md) ·
[схема БД](docs/schema.sql).

### Федеративные запросы

JOIN таблиц из разных СУБД: подтаблицы вытягиваются по read-only JDBC и материализуются
во временные таблицы внутреннего PostgreSQL, где выполняется JOIN. Детали —
[docs/federation.md](docs/federation.md).

## Запуск

```bash
cp .env.example .env        # задайте пароли и SCHEMA_ENCRYPT_KEY (обязателен)
docker compose up -d --build
```

При запуске через `docker compose` frontend доступен на http://localhost:3000,
backend API — на http://localhost:8080/api. Миграции `sql/V1`…`sql/V6`
применяются автоматически при первом старте контейнера БД (пустой том).

Разработка:

```bash
# Backend
cd backend && mvn quarkus:dev

# Frontend
cd frontend && npm install && npm run dev
```

## Развёртывание

Полное руководство (сервер, секреты, TLS/reverse-proxy, миграции на существующем
томе, бэкапы, обновление) — [docs/deployment.md](docs/deployment.md).

## API

Все эндпоинты находятся под префиксом `/api`.

| Метод  | Путь                        | Описание                              |
|--------|-----------------------------|---------------------------------------|
| `GET`  | `/api/instances`            | Список инстансов                      |
| `GET`  | `/api/instances/{id}`       | Инстанс по идентификатору             |
| `POST` | `/api/instances`            | Создание инстанса (201)               |
| `PATCH`| `/api/instances/{id}/password` | Смена пароля подключения (204)     |
| `POST` | `/api/instances/{id}/load`  | Собрать схему и опубликовать в SSE    |
| `GET`  | `/api/instances/{id}/schema`| Схема инстанса из хранилища           |
| `POST` | `/api/instances/compare`    | Сравнение двух инстансов              |
| `GET`  | `/api/instances/{id}/events`| SSE-поток событий инстанса            |
| `GET`  | `/api/instances/{id}/tables/{schema}/{table}/rows` | Строки таблицы (пагинация `limit`/`offset`; построчный дифф — на клиенте) |
| `POST` | `/api/join`                 | Выполнить собранный джойн (тело — структура соединения) |
| `GET`  | `/api/join/history`         | История джойнов |

Подробнее: [docs/architecture.md](docs/architecture.md)

## Тестирование

```bash
cd backend
mvn test

cd frontend
npm test
npm run check
```

## Конвенции

Все принятые конвенции проекта описаны в [docs/conventions.md](docs/conventions.md).

## Лицензия

MIT