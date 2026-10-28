package com.example.dbagg.util;

import com.example.dbagg.config.EncryptionConfig;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

/**
 * Шифрование и расшифровка паролей подключения к целевым БД.
 *
 * <p>Криптография выполняется на стороне PostgreSQL через расширение pgcrypto ({@code
 * pgp_sym_encrypt}/{@code pgp_sym_decrypt}); зашифрованный пароль никогда не покидает СУБД в
 * открытом виде. Ключ берётся из {@link EncryptionConfig}.
 */
@ApplicationScoped
public class CryptoUtil {
  private final DSLContext jooq;
  private final EncryptionConfig encryptionConfig;

  @Inject
  public CryptoUtil(
      @DataSource("storage") javax.sql.DataSource storageDataSource,
      EncryptionConfig encryptionConfig) {
    this.jooq = DSL.using(storageDataSource, SQLDialect.POSTGRES);
    this.encryptionConfig = encryptionConfig;
  }

  public String decryptPassword(long instanceId) {
    var rows =
        jooq.fetch(
            DSL.sql(SqlQueries.DECRYPT_PASSWORD, encryptionConfig.getEncryptKey(), instanceId));
    if (rows.isEmpty()) {
      throw new IllegalStateException("Instance not found: " + instanceId);
    }
    return rows.get(0).get("password", String.class);
  }

  public void storePassword(long instanceId, String plainPassword) {
    var updated =
        jooq.execute(
            DSL.sql(
                SqlQueries.UPDATE_INSTANCE_PASSWORD,
                plainPassword,
                encryptionConfig.getEncryptKey(),
                instanceId));
    if (updated == 0) {
      throw new IllegalStateException("Instance not found: " + instanceId);
    }
  }
}
