package com.faforever.client.preferences;

/**
 * Where the Open Annihilation executable comes from.
 */
public enum OaInstallationMode {
  /** TAF downloads and pins the release the server allows. */
  TAF_MANAGED,
  /** The user points at an OA install of their own; accepted only if its version is allowed. */
  EXISTING
}
