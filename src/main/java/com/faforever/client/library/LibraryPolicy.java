package com.faforever.client.library;

import com.faforever.client.update.ClientConfiguration;
import com.faforever.client.update.ClientConfiguration.OpenAnnihilation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which Library mods the server lets players host: the registries it trusts and the mod ids it hides because TAF
 * features them itself. Read from the client configuration each time, so changes need no client release.
 */
public record LibraryPolicy(List<Registry> trustedRegistries, Set<String> hiddenModIds) {

  public record Registry(String id, String catalogueUrl) {
  }

  /** A missing or empty configuration trusts nothing. */
  public static LibraryPolicy from(@Nullable ClientConfiguration configuration) {
    OpenAnnihilation oa = configuration == null ? null : configuration.getOpenAnnihilation();
    if (oa == null) {
      return new LibraryPolicy(List.of(), Set.of());
    }
    List<Registry> registries = oa.getTrustedRegistries() == null ? List.of() : oa.getTrustedRegistries().stream()
        .filter(registry -> registry.getId() != null && !registry.getId().isBlank())
        .map(registry -> new Registry(registry.getId().trim(), registry.getCatalogueUrl()))
        .toList();
    Set<String> hidden = oa.getHiddenLibraryMods() == null ? Set.of() : oa.getHiddenLibraryMods().stream()
        .filter(id -> id != null && !id.isBlank())
        .map(id -> id.trim().toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());
    return new LibraryPolicy(registries, hidden);
  }

  public boolean isHidden(String modId) {
    return hiddenModIds.contains(modId.toLowerCase(Locale.ROOT));
  }

  public boolean trusts(String registryId) {
    return trustedRegistries.stream().anyMatch(registry -> registry.id().equals(registryId));
  }
}
