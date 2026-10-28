-- ===========================================================================
-- Шифрование паролей подключения к инстансам
-- ---------------------------------------------------------------------------
-- 1. Включаем расширение pgcrypto
-- 2. Добавляем колонку password_encrypted
-- 3. Переносим существующие пароли в зашифрованный вид
-- 4. Удаляем открытый столбец password
-- 5. Ограничиваем размер connection_url
-- ===========================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE sc_instances ADD COLUMN password_encrypted BYTEA;

UPDATE sc_instances
SET password_encrypted = pgp_sym_encrypt(password, current_setting('app.db_encrypt_key', true));

ALTER TABLE sc_instances ALTER COLUMN password_encrypted SET NOT NULL;

ALTER TABLE sc_instances DROP COLUMN password;

ALTER TABLE sc_instances
    ALTER COLUMN connection_url TYPE VARCHAR(255),
    ADD CONSTRAINT ck_sc_instances_url_len CHECK (length(connection_url) <= 255);

COMMENT ON COLUMN sc_instances.password_encrypted IS
    'Пароль зашифрован через pgp_sym_encrypt; ключ задаётся переменной окружения SCHEMA_ENCRYPT_KEY (в коде передаётся как application setting app.db_encrypt_key).';

-- Пароль в sc_instances.password_encrypted хранится в BYTEA (pgp_sym_encrypt возвращает bytea).