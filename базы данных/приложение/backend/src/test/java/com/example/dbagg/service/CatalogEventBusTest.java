package com.example.dbagg.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.dbagg.model.Catalog;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class CatalogEventBusTest {
  @Test
  void deliversPublishedCatalogToSubscriber() throws Exception {
    var bus = new CatalogEventBus();
    var received = new ArrayList<Catalog>();
    var latch = new CountDownLatch(1);

    bus.stream(1L)
        .subscribe()
        .with(
            c -> {
              received.add(c);
              latch.countDown();
            });

    var catalog = new Catalog(1L, List.of());
    bus.publish(1L, catalog);

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(received).containsExactly(catalog);
  }

  @Test
  void doesNotDeliverToOtherInstanceSubscribers() throws Exception {
    var bus = new CatalogEventBus();
    var received = new ArrayList<Catalog>();
    var latch = new CountDownLatch(1);

    bus.stream(1L)
        .subscribe()
        .with(
            c -> {
              received.add(c);
              latch.countDown();
            });

    bus.publish(2L, new Catalog(2L, List.of()));

    assertThat(latch.await(500, TimeUnit.MILLISECONDS)).isFalse();
    assertThat(received).isEmpty();
  }

  @Test
  void publishWithoutSubscribersIsNoOp() {
    var bus = new CatalogEventBus();
    bus.publish(1L, new Catalog(1L, List.of()));
    bus.publish(2L, new Catalog(2L, List.of()));
  }

  @Test
  void cancelledSubscriberStopsReceivingEvents() throws Exception {
    var bus = new CatalogEventBus();
    var received = new ArrayList<Catalog>();

    var subscription = bus.stream(1L).subscribe().with(received::add);
    subscription.cancel();

    bus.publish(1L, new Catalog(1L, List.of()));

    assertThat(received).isEmpty();
  }
}
