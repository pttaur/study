package com.example.dbagg.service;

import com.example.dbagg.model.Instance;
import com.example.dbagg.repository.InstanceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Единая точка проверки существования инстанса для сервисов: возвращает инстанс или бросает {@link
 * IllegalArgumentException}, если он не найден.
 */
@ApplicationScoped
public class InstanceLookup {
  private final InstanceRepository instanceRepository;

  @Inject
  public InstanceLookup(InstanceRepository instanceRepository) {
    this.instanceRepository = instanceRepository;
  }

  public Instance require(long instanceId) {
    return instanceRepository
        .findInstanceById(instanceId)
        .orElseThrow(() -> new IllegalArgumentException("Instance not found: " + instanceId));
  }
}
