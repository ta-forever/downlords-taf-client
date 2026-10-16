package com.faforever.client.library;

import javax.annotation.Nullable;
import java.util.List;

/**
 * What a Library mod game needs OA to load, known before gpgnet4ta asks the launch server to start OA.
 *
 * @param mapKey the host's map key as {@code --list-content} gives it; null for a joiner, whose map is the host's
 */
public record LibraryLaunchPlan(String mod, String baseGame, List<String> expansions, @Nullable String mapKey) {
}
