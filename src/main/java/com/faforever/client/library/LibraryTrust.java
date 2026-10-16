package com.faforever.client.library;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Keeps the Library mods TAF offers: not hidden, and from a trusted registry. OA's own install record
 * ({@code .oa-origin.yaml}) is the cheap signal; a mod without one (or a hand-made folder) must match a trusted
 * registry's catalogue by id and version, or by sim hash. Anything that can't be shown to come from a trusted
 * registry is dropped, including when a catalogue can't be fetched.
 */
public final class LibraryTrust {

  private LibraryTrust() {
  }

  /**
   * @param catalogues the catalogue of a registry id, fetched lazily (only when some mod has no usable record)
   * @param simHash    the sim hash of a mod (from {@code --print-profile}), asked only when id and version fail
   */
  public static List<LibraryMod> filter(List<LibraryMod> mods, LibraryPolicy policy,
                                        Function<String, Optional<RegistryCatalogue>> catalogues,
                                        Function<LibraryMod, Optional<String>> simHash) {
    return mods.stream()
        .filter(mod -> !policy.isHidden(mod.id()))
        .filter(mod -> isFromTrustedRegistry(mod, policy, catalogues, simHash))
        .toList();
  }

  private static boolean isFromTrustedRegistry(LibraryMod mod, LibraryPolicy policy,
                                               Function<String, Optional<RegistryCatalogue>> catalogues,
                                               Function<LibraryMod, Optional<String>> simHash) {
    if (mod.registry() != null) {
      return policy.trusts(mod.registry());
    }
    for (LibraryPolicy.Registry registry : policy.trustedRegistries()) {
      Optional<RegistryCatalogue> catalogue = catalogues.apply(registry.id());
      if (catalogue.isEmpty()) {
        continue;
      }
      if (catalogue.get().matches(mod, null) || catalogue.get().matches(mod, simHash.apply(mod).orElse(null))) {
        return true;
      }
    }
    return false;
  }
}
