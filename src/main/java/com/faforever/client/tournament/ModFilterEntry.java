package com.faforever.client.tournament;

import java.util.Objects;

/**
 * An entry in the shared mod-filter ComboBox. {@code modId == null} means "All mods".
 * {@code modName} is the display name used for bracket-list filtering; {@code modId}
 * is used for Hall of Fame API queries.
 */
public class ModFilterEntry {
  public final Integer modId;
  public final String modName;
  public final String label;

  public ModFilterEntry(Integer modId, String modName, String label) {
    this.modId = modId;
    this.modName = modName;
    this.label = label;
  }

  /** "All mods" sentinel. */
  public boolean isAll() { return modId == null; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ModFilterEntry other)) return false;
    return Objects.equals(modId, other.modId);
  }

  @Override
  public int hashCode() { return Objects.hashCode(modId); }

  @Override
  public String toString() { return label; }
}
