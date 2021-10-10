package com.faforever.client.api.dto;

import com.github.jasminb.jsonapi.annotations.Id;
import com.github.jasminb.jsonapi.annotations.Relationship;
import com.github.jasminb.jsonapi.annotations.Type;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Type("featuredMod")
public class FeaturedMod {
  @Id
  private String id;
  private String description;
  private String displayName;
  private int order;
  private String gitBranch;
  private String gitUrl;
  private String bireusUrl;
  private String technicalName;
  private boolean visible;

  @Relationship("version")
  private List<FeaturedModVersion> versions;
}
