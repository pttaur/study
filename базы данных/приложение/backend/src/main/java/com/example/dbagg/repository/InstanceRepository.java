package com.example.dbagg.repository;

import com.example.dbagg.config.EncryptionConfig;
import com.example.dbagg.model.Instance;
import com.example.dbagg.util.CryptoUtil;
import com.example.dbagg.util.SqlQueries;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.exception.IntegrityConstraintViolationException;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Хранилище инстансов: CRUD, алиасы и пароли подключения (таблица {@code sc_instances}). */
@ApplicationScoped
public class InstanceRepository {
  private static final Logger log = LoggerFactory.getLogger(InstanceRepository.class);

  private final CryptoUtil cryptoUtil;
  private final EncryptionConfig encryptionConfig;
  private final DSLContext jooq;

  @Inject
  public InstanceRepository(
      @DataSource("storage") javax.sql.DataSource storageDataSource,
      CryptoUtil cryptoUtil,
      EncryptionConfig encryptionConfig) {
    this.cryptoUtil = cryptoUtil;
    this.encryptionConfig = encryptionConfig;
    this.jooq = DSL.using(storageDataSource, SQLDialect.POSTGRES);
  }

  public List<Instance> findAllInstances() {
    var rows = jooq.fetch(DSL.sql(SqlQueries.FIND_INSTANCES));
    var instances = new ArrayList<Instance>(rows.size());
    for (var row : rows) {
      instances.add(toInstance(row));
    }
    return instances;
  }

  public Optional<Instance> findInstanceById(long id) {
    var rows = jooq.fetch(DSL.sql(SqlQueries.FIND_INSTANCE_BY_ID, id));
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(toInstance(rows.get(0)));
  }

  public Optional<Instance> findInstanceByLabel(String label) {
    var rows = jooq.fetch(DSL.sql(SqlQueries.FIND_INSTANCE_BY_LABEL, label));
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(toInstance(rows.get(0)));
  }

  public long saveInstance(Instance instance, String plainPassword) {
    try {
      var rows =
          jooq.fetch(
              DSL.sql(
                  SqlQueries.INSERT_INSTANCE,
                  instance.label(),
                  instance.connectionUrl(),
                  instance.username(),
                  instance.kind(),
                  plainPassword,
                  encryptionConfig.getEncryptKey()));
      if (rows.isEmpty()) {
        throw new IllegalStateException("INSERT_INSTANCE returned no id");
      }
      var id = rows.get(0).get("id", Long.class);
      log.debug("Instance saved: {} (id={})", instance.label(), id);
      return id;
    } catch (IntegrityConstraintViolationException e) {
      throw new IllegalArgumentException(
          "Instance with the same label or connection URL already exists");
    }
  }

  public String decryptPassword(long id) {
    return cryptoUtil.decryptPassword(id);
  }

  public String storeEncryptedPassword(long id, String plainPassword) {
    cryptoUtil.storePassword(id, plainPassword);
    log.debug("Encrypted password updated for instance {}", id);
    return String.valueOf(id);
  }

  private Instance toInstance(Record row) {
    return new Instance(
        row.get("id", Long.class),
        row.get("label", String.class),
        row.get("connection_url", String.class),
        row.get("username", String.class),
        row.get("kind", String.class));
  }
}
