package com.faforever.client.preferences;

import com.google.gson.annotations.SerializedName;

/**
 * Engine updates (CONTEXT.md): who brings an Open Annihilation install up to date.
 * The alternates read preferences saved when this was OaInstallationMode.
 */
public enum EngineUpdates {
  /** TAF installs the newest release the server allows and replaces it when the server allows a newer one. */
  @SerializedName(value = "TAF", alternate = {"TAF_MANAGED"})
  TAF,
  /** TAF installs it once into engines/open-annihilation/self-updating/, then OA's own updater owns it. */
  @SerializedName("OPEN_ANNIHILATION")
  OPEN_ANNIHILATION,
  /** The user points at an OA install of their own, which TAF never updates; accepted only if its version is allowed. */
  @SerializedName(value = "OWN_INSTALL", alternate = {"EXISTING"})
  OWN_INSTALL
}
