package com.faforever.client.chat.messagetags;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.feature.MessageTagManager;
import org.kitteh.irc.client.library.util.TriFunction;

public class Toxicity extends MessageTagManager.DefaultMessageTag {
  /**
   * Name of this message tag.
   */
  public static final String NAME = "taforever.com/toxicity";

  /**
   * Function to create this message tag.
   */
  @SuppressWarnings("ConstantConditions")
  public static final TriFunction<Client, String, String, Toxicity> FUNCTION = (client, name, value) -> new Toxicity(name, value);

  private Toxicity(@NonNull String name, @NonNull String value) {
    super(name, value);
  }
}