package com.example.dbagg.config;

import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Провайдер ключа шифрования паролей подключения к целевым БД.
 *
 * <p>Ключ задаётся переменной окружения {@code SCHEMA_ENCRYPT_KEY} (свойство {@code
 * app.encrypt.key}) и не должен попадать в код или логи.
 */
@ApplicationScoped
@Unremovable
public class EncryptionConfig {
  private final String encryptKey;

  @Inject
  public EncryptionConfig(@ConfigProperty(name = "app.encrypt.key") String encryptKey) {
    this.encryptKey = encryptKey;
  }

  public String getEncryptKey() {
    return encryptKey;
  }
}
