package com.faforever.client.map;

import com.faforever.client.config.ClientProperties;
import com.faforever.client.game.KnownFeaturedMod;
import com.faforever.client.i18n.I18n;
import com.faforever.client.io.DownloadService;
import com.faforever.client.map.MapService.PreviewType;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.player.PlayerService;
import com.faforever.client.preferences.Preferences;
import com.faforever.client.preferences.PreferencesBuilder;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.remote.AssetService;
import com.faforever.client.remote.FafService;
import com.faforever.client.task.CompletableTask;
import com.faforever.client.task.TaskService;
import com.faforever.client.test.AbstractPlainJavaFxTest;
import com.faforever.client.theme.UiService;
import com.faforever.client.update.ClientConfiguration;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.eventbus.EventBus;
import javafx.scene.image.Image;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.luaj.vm2.LuaError;
import org.mockito.Mock;
import org.springframework.context.ApplicationContext;
import org.springframework.util.FileSystemUtils;
import org.testfx.util.WaitForAsyncUtils;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static com.faforever.client.util.LinkOrCopy.linkOrCopy;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MapServiceTest extends AbstractPlainJavaFxTest {

  @Rule
  public TemporaryFolder cacheDirectory = new TemporaryFolder();
  @Rule
  public TemporaryFolder mod1Directory = new TemporaryFolder();
  @Rule
  public TemporaryFolder mod2Directory = new TemporaryFolder();
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private MapService instance;
  private Path mapsDirectory;

  @Mock
  private PreferencesService preferencesService;
  @Mock
  private TaskService taskService;
  @Mock
  private I18n i18n;
  @Mock
  private UiService uiService;
  @Mock
  private AssetService assetService;
  @Mock
  private NotificationService notificationService;
  @Mock
  private DownloadService downloadService;

  @Mock
  private ApplicationContext applicationContext;
  @Mock
  private FafService fafService;
  @Mock
  private PlayerService playerService;
  @Mock
  private EventBus eventBus;

  @Before
  public void setUp() throws Exception {
    ClientProperties clientProperties = new ClientProperties();
    clientProperties.getVault().setMapPreviewUrlFormat("http://127.0.0.1:65534/preview/%s/%s");
    clientProperties.getVault().setMapDownloadUrlFormat("http://127.0.0.1:65534/fakeDownload/%s");

    Preferences preferences = PreferencesBuilder.create().defaultValues().get();
    preferences.setTotalAnnihilation("MOD1", mod1Directory.getRoot().toPath(), "");
    preferences.setTotalAnnihilation("MOD2", mod2Directory.getRoot().toPath(), "");

    when(preferencesService.getPreferences()).thenReturn(preferences);
    when(preferencesService.getCacheDirectory()).thenReturn(cacheDirectory.getRoot().toPath());
    instance = new MapService(preferencesService, taskService, applicationContext,
        fafService, assetService, notificationService, i18n, uiService, clientProperties, eventBus, playerService);
    instance.afterPropertiesSet();

    doAnswer(invocation -> {
      CompletableTask<?> task = invocation.getArgument(0);
      WaitForAsyncUtils.asyncFx(task);
      task.getFuture().get();
      return task;
    }).when(taskService).submitTask(any());

    instance.afterPropertiesSet();
  }

  @Test
  public void testGetLocalMapsNoMaps() {
    assertThat(instance.getInstalledMaps("MOD1"), hasSize(0));
  }

  @Test
  public void testGetLocalMapsOfficialMap() throws Exception {
    instance.otaMaps = ImmutableSet.of("SHERWOOD");
    instance.ccMaps = ImmutableSet.of("Gasplant Plain");
    instance.btMaps = ImmutableSet.of("Wretched Ridges");
    instance.cdMaps = ImmutableSet.of("Comet Catcher");

//    Path scmp001 = Files.createDirectory(mapsDirectory.resolve("SCMP_001"));
//    Files.copy(getClass().getResourceAsStream("/maps/SCMP_001/SCMP_001_scenario.lua"), scmp001.resolve("SCMP_001_scenario.lua"));
//
//    instance.afterPropertiesSet();
//
//    ObservableList<MapBean> localMapBeans = instance.getInstalledMaps();
//    assertThat(localMapBeans, hasSize(1));
//
//    MapBean mapBean = localMapBeans.get(0);
//    assertThat(mapBean, notNullValue());
//    assertThat(mapBean.getFolderName(), is("SCMP_001"));
//    assertThat(mapBean.getDisplayName(), is("Burial Mounds"));
//    assertThat(mapBean.getSize(), equalTo(MapSize.valueOf(1024, 1024)));
  }

  @Test
  public void testReadMapOfNonFolderThrowsException() {
    expectedException.expect(MapLoadException.class);
    expectedException.expectMessage(startsWith("Not a folder"));

    instance.readMap("something", null);
  }

  @Test
  public void testReadMapInvalidMap() throws Exception {
    Path corruptMap = Files.createDirectory(mapsDirectory.resolve("corruptMap"));
    Files.write(corruptMap.resolve("corruptMap_scenario.lua"), "{\"This is invalid\", \"}".getBytes(UTF_8));

    expectedException.expect(MapLoadException.class);
    expectedException.expectCause(instanceOf(LuaError.class));

    instance.readMap("corruptMap", null);
  }

  @Test
  public void testReadMap() throws Exception {
    MapBean mapBean = instance.readMap("SHERWOOD", null);

    assertThat(mapBean, notNullValue());
    assertThat(mapBean.getId(), isEmptyOrNullString());
    assertThat(mapBean.getDescription(), startsWith("Initial scans of the planet"));
    assertThat(mapBean.getSize(), is(MapSize.valueOf(1024, 1024)));
    assertThat(mapBean.getVersion(), is(new ComparableVersion("1")));
    assertThat(mapBean.getHpiArchiveName(), is("SCMP_001"));
  }


  @Test
  public void testLoadPreview() {
    for (PreviewType previewType : PreviewType.values()) {
      Path cacheSubDir = Paths.get("maps").resolve(previewType.getFolderName(10));
      when(assetService.loadAndCacheImage(any(URL.class), eq(cacheSubDir), any())).thenReturn(new Image("theme/images/unknown_map.png"));
      instance.loadPreview(KnownFeaturedMod.DEFAULT.getTechnicalName(), "preview", previewType, 10);
      verify(assetService).loadAndCacheImage(any(URL.class), eq(cacheSubDir), any());
    }
  }

  @Test
  public void testGetRecommendedMaps() {
    ClientConfiguration clientConfiguration = mock(ClientConfiguration.class);
    List<Integer> recommendedMapIds = Lists.newArrayList(1, 2, 3);
    when(clientConfiguration.getRecommendedMaps()).thenReturn(recommendedMapIds);
    when(preferencesService.getRemotePreferencesAsync()).thenReturn(CompletableFuture.completedFuture(clientConfiguration));
    when(fafService.getMapsByIdWithPageCount(recommendedMapIds, 10, 0)).thenReturn(CompletableFuture.completedFuture(null));

    instance.getRecommendedMapsWithPageCount(10, 0);

    verify(fafService).getMapsByIdWithPageCount(recommendedMapIds, 10, 0);
  }

  @Test
  public void testGetHighestRatedMaps() {
    when(fafService.getHighestRatedMapsWithPageCount(10, 0)).thenReturn(CompletableFuture.completedFuture(null));
    instance.getHighestRatedMapsWithPageCount(10, 0);
    verify(fafService).getHighestRatedMapsWithPageCount(10, 0);
  }

  @Test
  public void testGetNewestMaps() {
    when(fafService.getNewestMapsWithPageCount(10, 0)).thenReturn(CompletableFuture.completedFuture(null));
    instance.getNewestMapsWithPageCount(10, 0);
    verify(fafService).getNewestMapsWithPageCount(10, 0);
  }

  @Test
  public void testGetMostPlayedMaps() {
    when(fafService.getMostPlayedMapsWithPageCount(10, 0)).thenReturn(CompletableFuture.completedFuture(null));
    instance.getMostPlayedMapsWithPageCount(10, 0);
    verify(fafService).getMostPlayedMapsWithPageCount(10, 0);
  }

  @Test
  public void testGetLatestVersionMap() {
    MapBean oldestMap = MapBeanBuilder.create().folderName("unitMap v1").version(null).get();
    assertThat(instance.getMapLatestVersion(oldestMap).join(), is(oldestMap));

    MapBean map = MapBeanBuilder.create().folderName("junit_map1.v0003").version(3).get();
    MapBean sameMap = MapBeanBuilder.create().folderName("junit_map1.v0003").version(3).get();
    when(fafService.getMapLatestVersion(map.getMapName()))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(sameMap)));
    assertThat(instance.getMapLatestVersion(map).join(), is(sameMap));

    MapBean outdatedMap = MapBeanBuilder.create().folderName("junit_map2.v0001").version(1).get();
    MapBean newMap = MapBeanBuilder.create().folderName("junit_map2.v0002").version(2).get();
    when(fafService.getMapLatestVersion(outdatedMap.getMapName()))
        .thenReturn(CompletableFuture.completedFuture(Optional.of(newMap)));
    assertThat(instance.getMapLatestVersion(outdatedMap).join(), is(newMap));
  }

  @ParameterizedTest
  @CsvSource({
      ",",
      ",TA_Features_2013.ccx;mymaps_v1.hpi;mymaps_v2.hpi",
      "TA_Features_2013.ccx,mymaps_v1.hpi;mymaps_v2.hpi",
      "TA_Features_2013.ccx;mymaps_v1.hpi,mymaps_v2.hpi",
      "TA_Features_2013.ccx;mymaps_v1.hpi;mymaps_v2.hpi,"
  })
  public void testEnsureMap(String mod2Contents, String mapCacheContents) throws Exception {

    for (String archive: mod2Contents.split(";")) {
      Path source = Paths.get(getClass().getResource("/maps/" + archive).toURI());
      Path dest = preferencesService.getTotalAnnihilation("MOD2").getInstalledPath().resolve(archive);
      linkOrCopy(source, dest);
    }

    for (String archive: mapCacheContents.split(";")) {
      Path source = Paths.get(getClass().getResource("/maps/" + archive).toURI());
      Path dest = preferencesService.getCacheDirectory().resolve("maps").resolve(archive);
      linkOrCopy(source, dest);
    }

    doMapInstallationTests("MOD1");
  }

  private void doMapInstallationTests(String modTechnical) {
    checkMapInstallationStatus(modTechnical, "mymaps_v1.hpi", false);
    checkMapInstallationStatus(modTechnical, "mymaps_v2.hpi", false);

    instance.ensureMap(modTechnical, "bar", "d7c6292f", "mymaps_v1.hpi", null, null);
    checkMapInstallationStatus(modTechnical, "TA_Features_2013.ccx", true);
    checkMapInstallationStatus(modTechnical, "mymaps_v1.hpi", true);
    checkMapInstallationStatus(modTechnical, "mymaps_v2.hpi", false);

    instance.ensureMap(modTechnical, "foo", "f24f8f30", "mymaps_v1.hpi", null, null);
    checkMapInstallationStatus(modTechnical, "mymaps_v1.hpi", true);
    checkMapInstallationStatus(modTechnical, "mymaps_v2.hpi", false);

    instance.ensureMap(modTechnical, "foo", "f24f8f30", "mymaps_v2.hpi", null, null);
    checkMapInstallationStatus(modTechnical, "mymaps_v1.hpi", true);
    checkMapInstallationStatus(modTechnical, "mymaps_v2.hpi", false);

    instance.ensureMap(modTechnical, "bar", "1a11b3db", "mymaps_v2.hpi", null, null);
    checkMapInstallationStatus(modTechnical, "mymaps_v1.hpi", false);
    checkMapInstallationStatus(modTechnical, "mymaps_v2.hpi", true);

    instance.ensureMap(modTechnical, "foo", "f24f8f30", "mymaps_v1.hpi", null, null);
    checkMapInstallationStatus(modTechnical, "mymaps_v1.hpi", false);
    checkMapInstallationStatus(modTechnical, "mymaps_v2.hpi", true);

    instance.ensureMap(modTechnical, "foo", "f24f8f30", "mymaps_v2.hpi", null, null);
    checkMapInstallationStatus(modTechnical, "mymaps_v1.hpi", false);
    checkMapInstallationStatus(modTechnical, "mymaps_v2.hpi", true);

    instance.ensureMap(modTechnical, "bon", "36cf395f", "mymaps_v2.hpi", null, null);
    checkMapInstallationStatus(modTechnical, "mymaps_v1.hpi", false);
    checkMapInstallationStatus(modTechnical, "mymaps_v2.hpi", true);

    instance.ensureMap(modTechnical, "ban", "997b04cd", "mymaps_v1.hpi", null, null);
    checkMapInstallationStatus(modTechnical, "mymaps_v1.hpi", true);
    checkMapInstallationStatus(modTechnical, "mymaps_v2.hpi", false);
  }

  private void checkMapInstallationStatus(String modTechnical, String hpiArchiveName, boolean expectedStatus) {
    assertThat(Files.exists(preferencesService.getTotalAnnihilation(modTechnical).getInstalledPath().resolve(hpiArchiveName)), is(expectedStatus));
    if (hpiArchiveName == "mymaps_v1.hpi") {
      assertThat(instance.isInstalled(modTechnical, "foo", "f24f8f30"), is(expectedStatus));
      assertThat(instance.isInstalled(modTechnical, "bar", "d7c6292f"), is(expectedStatus));
      assertThat(instance.isInstalled(modTechnical, "ban", "997b04cd"), is(expectedStatus));
    }
    else if (hpiArchiveName == "mymaps_v2.hpi") {
      assertThat(instance.isInstalled(modTechnical, "foo", "f24f8f30"), is(expectedStatus));
      assertThat(instance.isInstalled(modTechnical, "bar", "1a11b3db"), is(expectedStatus));
      assertThat(instance.isInstalled(modTechnical, "bon", "36cf395f"), is(expectedStatus));
    }
    else if (hpiArchiveName == "TA_Features_2013.ccx") {
    }
    else {
      assert(false);
    }
  }

  private void prepareDownloadMapTask(String modTechnical, String hpiArchiveName) {
    StubDownloadMapTask task = new StubDownloadMapTask(
        preferencesService, notificationService, downloadService, i18n, preferencesService.getCacheDirectory().resolve("maps"));
    task.setHpiArchiveName(hpiArchiveName);
    task.setInstallationPath(preferencesService.getTotalAnnihilation(modTechnical).getInstalledPath());
    when(applicationContext.getBean(DownloadMapTask.class)).thenReturn(task);
  }

  private void prepareUninstallMapTask(String modTechnical, String hpiArchiveName) {
    UninstallMapTask task = new UninstallMapTask();
    task.setHpiArchiveName(hpiArchiveName);
    task.setInstallationPath(preferencesService.getTotalAnnihilation(modTechnical).getInstalledPath());
    when(applicationContext.getBean(UninstallMapTask.class)).thenReturn(task);
  }

  private void copyMapsToMapsCacheDirectory(String... hpiArchiveNames) throws Exception {
    for (String hpiArchiveName : hpiArchiveNames) {
      Path source = Paths.get(getClass().getResource("/maps/" + hpiArchiveName).toURI());
      Path dest = preferencesService.getCacheDirectory().resolve("maps").resolve(hpiArchiveName);
      linkOrCopy(source, dest);
    }
  }
}
