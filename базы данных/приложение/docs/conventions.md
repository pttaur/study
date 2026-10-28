# Конвенции проекта

Все правила, которым следуют участники проекта при написании кода.

## Обязательные

### 1. Backend: Java

| Область | Правило |
|---------|---------|
| Версия Java | JDK 25 |
| Фреймворк | Quarkus 3 |
| Стиль кода | Google Java Style Guide |
| SQL | Чистый SQL в строковых константах |
| ORM | Не использовать |
| Логирование | Структурированные JSON-логи |

#### Локальные переменные — `var`

- `var` **обязателен** для всех локальных переменных, где тип очевиден
- `var` **запрещён** для полей класса, параметров методов, возвращаемых типов
- Исключение: если `var` ухудшает читаемость — допустим явный тип

```java
// правильно
var tables = new HashMap<String, Table>();

// неправильно
public Column findBy(Column column) { } // параметр — var нельзя
```

#### SQL-запросы

- Все запросы — чистый SQL в виде строковых констант в классе `SqlQueries`
- Для выполнения используется JOOQ `DSLContext` как тонкая обёртка над JDBC (без генерации SQL)
- SQL-константы именуются `UPPER_SNAKE_CASE`

#### Пакетная структура

```
com.example.dbagg
├── config
├── model
├── repository
├── service
├── controller
└── util
```

Все классы backend размещаются строго в одном из этих пакетов; новых корневых пакетов не заводим без обсуждения.

#### Именование

- Классы: `PascalCase`, один класс — один файл
- Методы: `camelCase`
- Модели: `Catalog`, `Schema`, `Table`, `Column`, `Constraint`, `Index`, `DiffResult`

### 2. Frontend: Svelte

| Область | Правило |
|---------|---------|
| Фреймворк | Svelte 5 (Runes) |
| Язык | TypeScript |
| Сборщик | Vite |
| Компоненты | `PascalCase`, до 200 строк |

- Пропсы: `let { prop } = $props()`
- Состояние: `$state`, `$derived`, `$effect`

### 3. Git

| Правило | Описание |
|---------|----------|
| Ветки | `main` (стабильная), `develop` (разработка), `feature/*`, `fix/*` |
| Коммиты | Conventional Commits: `feat:`, `fix:`, `refactor:`, `test:`, `docs:` |
| PR | Каждый PR проходит ревью агентом |
| Merge | Squash merge в `develop` |

### 4. Безопасность

- Пароли подключения хранятся в зашифрованном виде (pgcrypto)
- Ключ шифрования — через переменную окружения `SCHEMA_ENCRYPT_KEY`, **не** в коде
- Secrets не попадают в коммиты
- URL целевых БД валидируется: схема под тип СУБД + опциональный allowlist хостов `SC_CONNECTION_ALLOWED_HOSTS`
- **Ограничение:** REST API и SSE не аутентифицированы — сервис рассчитан на запуск за доверенным периметром/шлюзом с аутентификацией

### 5. Форматирование и линтинг

| Область | Команда | Инструмент |
|---------|---------|-----------|
| Backend (применить) | `mvn spotless:apply` | Spotless + google-java-format |
| Backend (проверить) | `mvn spotless:check` | Spotless + google-java-format |
| Frontend (применить) | `npm run format` | Prettier + prettier-plugin-svelte |
| Frontend (проверить) | `npm run format:check` | Prettier + prettier-plugin-svelte |
| Frontend (линт) | `npm run lint` | ESLint (flat config, svelte + typescript-eslint) |

Форматирование обязано быть чистым перед коммитом.

## Рекомендуемые

- Комментарии — только там, где они объясняют «почему», а не «что»