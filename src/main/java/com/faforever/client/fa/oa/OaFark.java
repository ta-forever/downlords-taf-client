package com.faforever.client.fa.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

/** One request to OA's {@code --fark} automation endpoint; {@link OaFarkClient} implements it and a test scripts it. */
interface OaFark {
  JsonNode request(ObjectNode message) throws IOException;
}
