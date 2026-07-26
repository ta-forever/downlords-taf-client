package com.faforever.client.assetserver;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TotalAnnihilationPrefs;
import com.faforever.client.preferences.AskAlwaysOrNever;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class LocalAssetServerServiceTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Mock
  private PreferencesService preferencesService;
  @Mock
  private com.faforever.client.preferences.Preferences preferences;

  private LocalAssetServerService instance;
  private String baseUrl;
  private byte[] hpiContent;

  @Before
  public void setUp() throws IOException {
    Path installDir = temp.getRoot().toPath();

    hpiContent = new byte[100];
    for (int i = 0; i < hpiContent.length; i++) {
      hpiContent[i] = (byte) i;
    }
    Files.write(installDir.resolve("totala1.hpi"), hpiContent);
    Files.write(installDir.resolve("TAForever.gp3"), new byte[] {1, 2, 3});
    Files.write(installDir.resolve("maps.ufo"), new byte[] {4, 5, 6});
    Files.write(installDir.resolve("readme.txt"), new byte[] {7});

    ClientProperties clientProperties = new ClientProperties();
    clientProperties.getWebsite().setBaseUrl("https://www.taforever.com");

    TotalAnnihilationPrefs taPrefs = new TotalAnnihilationPrefs(
        "tacc", installDir.resolve(TotalAnnihilationPrefs.TOTAL_ANNIHILATION_EXE), "", AskAlwaysOrNever.NEVER);
    when(preferencesService.getTotalAnnihilation(anyString())).thenReturn(taPrefs);
    when(preferencesService.getPreferences()).thenReturn(preferences);
    when(preferences.getTotalAnnihilationAllMods()).thenReturn(javafx.collections.FXCollections.observableArrayList(taPrefs));

    instance = new LocalAssetServerService(clientProperties, preferencesService);
    baseUrl = instance.ensureRunning();
  }

  @After
  public void tearDown() {
    instance.stop();
  }

  private HttpURLConnection get(String pathAndQuery, String... headers) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + pathAndQuery).openConnection();
    for (int i = 0; i < headers.length; i += 2) {
      connection.setRequestProperty(headers[i], headers[i + 1]);
    }
    return connection;
  }

  @Test
  public void manifestListsArchivesInPrecedenceOrder() throws IOException {
    HttpURLConnection connection = get("/manifest?mod=tacc");
    assertThat(connection.getResponseCode(), is(200));
    JsonNode root = JSON.readTree(connection.getInputStream());

    assertThat(root.get("mod").asText(), is("tacc"));
    JsonNode archives = root.get("archives");
    // TAForever.gp3 outranks everything; then ufo above hpi; readme.txt filtered out.
    assertThat(archives.size(), is(3));
    assertThat(archives.get(0).get("name").asText(), is("TAForever.gp3"));
    assertThat(archives.get(1).get("name").asText(), is("maps.ufo"));
    assertThat(archives.get(2).get("name").asText(), is("totala1.hpi"));
    assertThat(archives.get(2).get("size").asLong(), is(100L));
  }

  @Test
  public void manifestListsLooseFilesExcludingArchivesAndDotDirs() throws IOException {
    // loose files outrank archives in native TA (fopen_HPI tries the filesystem first) —
    // the manifest must enumerate them so the viewer VFS can honour that without probing
    Path installDir = temp.getRoot().toPath();
    Files.createDirectories(installDir.resolve("units"));
    Files.write(installDir.resolve("units").resolve("armcom.fbi"), new byte[] {9});
    Files.createDirectories(installDir.resolve(".git"));
    Files.write(installDir.resolve(".git").resolve("index"), new byte[] {1});

    HttpURLConnection connection = get("/manifest?mod=tacc");
    assertThat(connection.getResponseCode(), is(200));
    JsonNode root = JSON.readTree(connection.getInputStream());

    JsonNode loose = root.get("loose");
    java.util.List<String> paths = new java.util.ArrayList<>();
    loose.forEach(n -> paths.add(n.get("path").asText()));
    assertThat(paths, org.hamcrest.Matchers.hasItems("readme.txt", "units/armcom.fbi"));
    // archives and dot-directories are excluded
    for (String p : paths) {
      assertThat(p, org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(".git")));
      assertThat(p, org.hamcrest.Matchers.not(org.hamcrest.Matchers.endsWith(".hpi")));
      assertThat(p, org.hamcrest.Matchers.not(org.hamcrest.Matchers.endsWith(".gp3")));
    }
  }

  @Test
  public void corsPinnedToWebsiteOrigin() throws IOException {
    HttpURLConnection connection = get("/manifest?mod=tacc");
    assertThat(connection.getHeaderField("Access-Control-Allow-Origin"), is("https://www.taforever.com"));
  }

  @Test
  public void rangeRequestReturnsPartialContent() throws IOException {
    HttpURLConnection connection = get("/archive/totala1.hpi?mod=tacc", "Range", "bytes=10-19");
    assertThat(connection.getResponseCode(), is(206));
    assertThat(connection.getHeaderField("Content-Range"), is("bytes 10-19/100"));

    byte[] body = connection.getInputStream().readAllBytes();
    assertThat(body.length, is(10));
    assertThat(body[0], is((byte) 10));
    assertThat(body[9], is((byte) 19));
  }

  @Test
  public void fullArchiveFetchReturns200WithEtag() throws IOException {
    HttpURLConnection connection = get("/archive/totala1.hpi?mod=tacc");
    assertThat(connection.getResponseCode(), is(200));
    String etag = connection.getHeaderField("ETag");
    assertThat(connection.getInputStream().readAllBytes().length, is(100));

    HttpURLConnection cached = get("/archive/totala1.hpi?mod=tacc", "If-None-Match", etag);
    assertThat(cached.getResponseCode(), is(304));
  }

  @Test
  public void unsatisfiableRangeReturns416() throws IOException {
    HttpURLConnection connection = get("/archive/totala1.hpi?mod=tacc", "Range", "bytes=500-600");
    assertThat(connection.getResponseCode(), is(416));
  }

  @Test
  public void wrongTokenIs404() throws IOException {
    String withoutToken = baseUrl.substring(0, baseUrl.lastIndexOf('/'));
    HttpURLConnection connection = (HttpURLConnection) new URL(withoutToken + "/wrongtoken/manifest?mod=tacc").openConnection();
    assertThat(connection.getResponseCode(), is(404));
  }

  @Test
  public void pathTraversalRejected() throws IOException {
    assertThat(get("/file/..%2F..%2Fsecret.txt?mod=tacc").getResponseCode(), is(404));
    assertThat(get("/archive/..%5Ctotala1.hpi?mod=tacc").getResponseCode(), is(404));
  }

  @Test
  public void nonArchiveFilesNotServedViaArchiveEndpoint() throws IOException {
    assertThat(get("/archive/readme.txt?mod=tacc").getResponseCode(), is(404));
  }

  @Test
  public void looseFileServed() throws IOException {
    assertThat(get("/file/readme.txt?mod=tacc").getResponseCode(), is(200));
  }

  @Test
  public void modsEndpointListsInstalledMods() throws IOException {
    HttpURLConnection connection = get("/mods");
    assertThat(connection.getResponseCode(), is(200));
    JsonNode root = JSON.readTree(connection.getInputStream());
    assertThat(root.get("mods").size(), is(1));
    assertThat(root.get("mods").get(0).asText(), is("tacc"));
  }
}
