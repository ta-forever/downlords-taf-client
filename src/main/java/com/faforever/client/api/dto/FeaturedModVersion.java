package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Type("featuredModVersion")
public class FeaturedModVersion {
  @Id
  private String id;
  private String version;
  private String taHash;

  @Relationship("featuredMod")
  private FeaturedMod featuredMod;
}
