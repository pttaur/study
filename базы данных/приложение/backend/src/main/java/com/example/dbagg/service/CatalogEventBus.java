package com.example.dbagg.service;

import com.example.dbagg.model.Catalog;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Шина событий загрузки каталогов для SSE-подписчиков.
 *
 * <p>После каждой успешной сборки схемы {@link SchemaLoader} публикует каталог в шину, а открытые
 * SSE-соединения ({@code GET /instances/{id}/events}) получают свежий каталог. Подписчики
 * регистрируются на идентификатор инстанса и автоматически удаляются при закрытии соединения.
 */
@ApplicationScoped
public class CatalogEventBus {
  private final Map<Long, List<MultiEmitter<? super Catalog>>> subscribers =
      new ConcurrentHashMap<>();

  /**
   * Открывает SSE-поток каталогов для инстанса.
   *
   * @param instanceId идентификатор инстанса
   * @return multi с каталогами, опубликованными после подписки
   */
  public Multi<Catalog> stream(long instanceId) {
    return Multi.createFrom()
        .<Catalog>emitter(
            emitter -> {
              register(instanceId, emitter);
              emitter.onTermination(() -> unregister(instanceId, emitter));
            });
  }

  /**
   * Публикует каталог всем активным подписчикам инстанса.
   *
   * @param instanceId идентификатор инстанса
   * @param catalog собранный каталог
   */
  public void publish(long instanceId, Catalog catalog) {
    for (var emitter : subscribers.getOrDefault(instanceId, List.of())) {
      emitter.emit(catalog);
    }
  }

  private void register(long instanceId, MultiEmitter<? super Catalog> emitter) {
    subscribers.computeIfAbsent(instanceId, k -> new CopyOnWriteArrayList<>()).add(emitter);
  }

  private void unregister(long instanceId, MultiEmitter<? super Catalog> emitter) {
    var list = subscribers.get(instanceId);
    if (list == null) {
      return;
    }
    list.remove(emitter);
    if (list.isEmpty()) {
      subscribers.remove(instanceId, list);
    }
  }
}
