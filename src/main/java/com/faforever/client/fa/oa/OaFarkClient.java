package com.faforever.client.fa.oa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.Closeable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

/**
 * Minimal client for Open Annihilation's {@code --fark} automation endpoint (docs/automation.md
 * in the OA source), ported from the M0 prototype {@code fark.py}.
 *
 * <p>Used only to learn that the host's match has started until OA reports it itself (upstream
 * ask U2). The endpoint serves one client at a time, so each poll opens and closes a connection.
 * While {@code --fark} is on, OA labels the battle room "REMOTED".
 */
class OaFarkClient implements Closeable, OaHostSetup.Fark {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int CONNECT_TIMEOUT_MS = 1000;
  private static final int READ_TIMEOUT_MS = 2000;

  private final Socket socket = new Socket();
  private int nextId;

  OaFarkClient(Path endpointFile) throws IOException {
    JsonNode endpoint = MAPPER.readTree(Files.readAllBytes(endpointFile));
    String address = endpoint.path("address").asText();
    int colon = address.lastIndexOf(':');
    if (colon < 0) {
      throw new IOException("bad fark address: " + address);
    }
    String host = address.substring(0, colon).replace("[", "").replace("]", "");
    int port = Integer.parseInt(address.substring(colon + 1));
    socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
    socket.setSoTimeout(READ_TIMEOUT_MS);

    ObjectNode hello = MAPPER.createObjectNode().put("op", "hello").put("client", "taf-client")
        .put("token", endpoint.path("token").asText());
    hello.putArray("versions").add(1);
    if (!request(hello).path("ok").asBoolean(false)) {
      close();
      throw new IOException("fark endpoint refused hello (busy?)");
    }
  }

  /** @return the current OA screen name, eg {@code "match"} once the game is running */
  String screen() throws IOException {
    return request(MAPPER.createObjectNode().put("op", "screen")).path("screen").asText("");
  }

  void quit() throws IOException {
    request(MAPPER.createObjectNode().put("op", "quit"));
  }

  @Override
  public JsonNode request(ObjectNode message) throws IOException {
    int id = ++nextId;
    message.put("id", id);
    byte[] body = MAPPER.writeValueAsBytes(message);
    CRC32 crc = new CRC32();
    crc.update(body);
    String header = String.format("AUTO/1 - %d 0 %08x\n", body.length, crc.getValue());
    OutputStream out = socket.getOutputStream();
    out.write(header.getBytes(StandardCharsets.US_ASCII));
    out.write(body);
    out.flush();
    while (true) {
      JsonNode answer = readFrame(socket.getInputStream());
      if (answer.path("id").asInt(-1) == id) {
        return answer;
      }
    }
  }

  private static JsonNode readFrame(InputStream in) throws IOException {
    ByteArrayOutputStream headerBytes = new ByteArrayOutputStream();
    int b;
    while ((b = in.read()) != '\n') {
      if (b < 0) {
        throw new IOException("fark connection closed");
      }
      headerBytes.write(b);
    }
    String[] fields = headerBytes.toString(StandardCharsets.US_ASCII).trim().split("\\s+");
    if (fields.length < 5) {
      throw new IOException("bad fark frame header: " + headerBytes);
    }
    int jsonLength = Integer.parseInt(fields[2]);
    int payloadLength = Integer.parseInt(fields[3]);
    byte[] data = in.readNBytes(jsonLength + payloadLength);
    if (data.length < jsonLength) {
      throw new IOException("short fark frame");
    }
    return MAPPER.readTree(data, 0, jsonLength);
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }
}
