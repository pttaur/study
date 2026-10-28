package com.example.dbagg.service;

import com.example.dbagg.model.DbKind;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Проверяет URL подключения к целевой БД: схема должна соответствовать типу СУБД, а хост (при
 * заданном allowlist {@code app.connection.allowed-hosts}) — входить в разрешённые. Снижает риск
 * SSRF при создании инстанса.
 */
@ApplicationScoped
public class ConnectionUrlValidator {
  private final List<String> allowedHosts;

  @Inject
  public ConnectionUrlValidator(
      @ConfigProperty(name = "app.connection.allowed-hosts") Optional<String> allowedHosts) {
    this.allowedHosts =
        allowedHosts.stream()
            .flatMap(value -> Arrays.stream(value.split(",")))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .map(value -> value.toLowerCase(Locale.ROOT))
            .toList();
  }

  public void validate(String connectionUrl, String kindCode) {
    var url = connectionUrl == null ? "" : connectionUrl.trim();
    var kind = DbKind.fromCode(kindCode);
    var expectedScheme = kind == DbKind.MARIADB ? "jdbc:mariadb:" : "jdbc:postgresql:";
    if (!url.toLowerCase(Locale.ROOT).startsWith(expectedScheme)) {
      throw new BadRequestException(
          "Connection URL must start with " + expectedScheme + " for " + kind + ": " + url);
    }
    if (allowedHosts.isEmpty()) {
      return;
    }
    var host = extractHost(url);
    if (host.isEmpty() || !isAllowed(host)) {
      throw new BadRequestException("Host is not allowed by configuration: " + url);
    }
  }

  private boolean isAllowed(String host) {
    var lower = host.toLowerCase(Locale.ROOT);
    for (var allowed : allowedHosts) {
      var suffix = allowed.startsWith("*.") ? allowed.substring(1) : null;
      if (lower.equals(allowed) || (suffix != null && lower.endsWith(suffix))) {
        return true;
      }
    }
    return false;
  }

  static String extractHost(String url) {
    var lower = url.toLowerCase(Locale.ROOT);
    String rest;
    if (lower.startsWith("jdbc:postgresql://")) {
      rest = url.substring("jdbc:postgresql://".length());
    } else if (lower.startsWith("jdbc:mariadb://")) {
      rest = url.substring("jdbc:mariadb://".length());
    } else {
      var at = url.indexOf('@');
      if (at < 0) {
        return "";
      }
      rest = url.substring(at + 1);
      if (rest.startsWith("//")) {
        rest = rest.substring(2);
      }
    }
    var end = rest.length();
    for (var separator : new char[] {':', '/', '?', '\\'}) {
      var index = rest.indexOf(separator);
      if (index >= 0 && index < end) {
        end = index;
      }
    }
    return rest.substring(0, end);
  }
}
