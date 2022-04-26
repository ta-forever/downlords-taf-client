package com.faforever.client.mod;

import lombok.Value;

import java.util.List;

@Value
public class FeaturedModInstallSpecs {
  String url;
  List<FeaturedModInstallFolders> folders;
}
