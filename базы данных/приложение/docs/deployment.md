# Развёртывание

Приложение собирается и запускается через Docker Compose: три контейнера —
`db` (PostgreSQL-хранилище), `backend` (Quarkus) и `frontend` (nginx + SPA).

```
frontend :80  ──(nginx, /api → backend:8080)──►  backend :8080  ──►  db :5432
   ▲                                                 │
   └── браузер пользователя                          └──(JDBC, read-only)──►  целевые БД-источники
```

---

## 1. Требования к серверу

- Docker Engine 24+ и Docker Compose v2 (`docker compose`).
- 2 vCPU / 2 ГБ RAM минимум (JVM `-Xmx512m`, PostgreSQL, nginx). Для крупных
  федеративных запросов — больше RAM (результаты материализуются в память и temp-таблицы).
- Свободные порты: `3000` (frontend) и `8080` (backend) — настраиваются через `.env`.
- Сетевой доступ от сервера к целевым БД-источникам (PostgreSQL/MariaDB).

---

## 2. Быстрый старт

```bash
git clone https://github.com/pttaur/db-things.git
cd db-things

cp .env.example .env
# отредактируйте .env: пароли и SCHEMA_ENCRYPT_KEY (обязательно)

docker compose up -d --build
```

Открыть: `http://<server>:${FRONTEND_PORT:-3000}`.
API: `http://<server>:${BACKEND_PORT:-8080}/api`. Health: `/q/health`.

Проверка состояния:

```bash
docker compose ps
docker compose logs -f backend
curl -fsS http://localhost:8080/q/health/live
```

Остановить / удалить:

```bash
docker compose down            # остановить (данные сохраняются в томе scagg-data)
docker compose down -v         # остановить и УДАЛИТЬ данные хранилища
```

---

## 3. Секреты (`.env`)

| Переменная | Обязательна | Назначение |
|------------|-------------|------------|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | да | Хранилище `sc_*` |
| `SC_STORAGE_URL` / `SC_STORAGE_USER` / `SC_STORAGE_PASSWORD` | да | Доступ backend к хранилищу |
| `SCHEMA_ENCRYPT_KEY` | **да** | Ключ pgcrypto для паролей источников; без него backend не стартует |
| `SC_CONNECTION_ALLOWED_HOSTS` | нет | Allowlist хостов источников (anti-SSRF), через запятую |
| `BACKEND_PORT` / `FRONTEND_PORT` | нет | Порты на хосте |

Сгенерировать ключ шифрования:

```bash
openssl rand -hex 32
```

> `SCHEMA_ENCRYPT_KEY` нельзя менять после первого запуска: иначе ранее
> сохранённые пароли источников не расшифруются (данные `sc_instances.password_encrypted`).

`.env` в git не коммитится (см. `.gitignore`).

---

## 4. Миграции БД

Схема хранилища задаётся файлами `sql/V1__*.sql` … `sql/V6__*.sql`. Они смонтированы
в `docker-entrypoint-initdb.d` контейнера `db`.

- **Первый запуск (пустой том `scagg-data`)** — все миграции применяются автоматически.
- **Обновление на существующем томе** — initdb-скрипты **не переигрываются**.
  Новую миграцию применяют вручную:

  ```bash
  docker compose exec -T db psql -v ON_ERROR_STOP=1 \
    -U "$POSTGRES_USER" -d "$POSTGRES_DB" < sql/V7__next_change.sql
  ```

  (Пароль роли при этом уже установлен при первой инициализации — смена
  `POSTGRES_PASSWORD` в `.env` на существующем томе пароль **не** изменит.)

- **Начать с чистого листа**: `docker compose down -v && docker compose up -d --build`.

---

## 5. TLS и reverse proxy

Прямой доступ по HTTP использовать не рекомендуется. Поставьте перед frontend
TLS-терминирующий прокси (nginx/traefik/Caddy). Пример nginx на хосте:

```nginx
server {
    listen 443 ssl;
    server_name scagg.example.com;

    ssl_certificate     /etc/letsencrypt/live/scagg.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/scagg.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:3000;   # frontend
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_buffering off;                # важно для SSE /api/instances/{id}/events
        proxy_read_timeout 1h;
    }
}

server {
    listen 80;
    server_name scagg.example.com;
    return 301 https://$host$request_uri;
}
```

Рекомендации:

- Не публиковать порт `backend` наружу: ограничьте firewall’ом или задайте
  `BACKEND_PORT=127.0.0.1:8080` в `.env` (тогда доступ только с самого хоста).
- Для SSE (`/api/instances/{id}/events`) обязательно `proxy_buffering off` и
  увеличенный `proxy_read_timeout` (в конфиге `frontend/nginx.conf` уже настроено).

---

## 6. Обновление версии

```bash
git pull
docker compose up -d --build      # пересборка backend/frontend, db не пересоздаётся
docker compose ps                 # дождитесь healthy
```

Если в релизе есть новые миграции — примените их (раздел 4). Данные `sc_*`
сохраняются в томе `scagg-data` между пересборками.

---

## 7. Бэкапы и восстановление

```bash
# Дамп хранилища
docker compose exec -T db pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" > backup.sql

# Восстановление в чистую БД
docker compose down -v && docker compose up -d db
docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" < backup.sql
```

> В дампе лежат зашифрованные пароли (`password_encrypted`). Для восстановления
> расшифровки нужен тот же `SCHEMA_ENCRYPT_KEY`.

---

## 8. Мониторинг и логи

- Health: `GET /q/health` (общий), `/q/health/live`, `/q/health/ready`.
- Логи backend — структурированный JSON в stdout (`docker compose logs backend`).
- Историю джойнов можно смотреть через `GET /api/join/history`.

---

## 9. Безопасность (важно)

- **API и SSE не аутентифицированы.** Любой, кто дотянется до портов, может читать
  данные всех подключённых источников и выполнять произвольный SELECT. Обязательно
  размещайте сервис **за доверенным периметром** и/или reverse-proxy с аутентификацией
  (Basic/OIDC на уровне прокси).
- Заполните `SC_CONNECTION_ALLOWED_HOSTS`, чтобы запретить регистрацию инстансов на
  произвольные хосты (SSRF).
- Соединения к источникам открываются **read-only**.
- Федеративный SQL отклоняет подзапросы и обращения к внутренним таблицам `sc_*`.
- Секреты держите только в `.env`/secret-store, не в git.

---

## 10. Локальная разработка

```bash
# Полный стек
cp .env.example .env
docker compose up -d --build

# Backend (dev)
cd backend && mvn quarkus:dev

# Frontend (dev)
cd frontend && npm install && npm run dev
```

Проверки качества:

```bash
cd backend && mvn -B spotless:apply test
cd frontend && npm run check && npm run lint && npm test
```
