package com.faforever.client.mod;

import lombok.Value;

import java.util.List;
import java.util.Set;

@Value
public class FeaturedModInstallFolders {
  String regex;
  Set<String> platforms;
}
