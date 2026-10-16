package com.faforever.client.library;

import com.faforever.client.fa.oa.OaInstallationService;
import com.faforever.client.fa.oa.OaInstallationService.InstalledOa;
import com.faforever.client.fa.oa.OaVersion;
import com.faforever.client.library.LibraryJoinVerdict.Outcome;
import com.faforever.client.library.LibraryReadiness.Status;
import com.faforever.client.update.ClientConfiguration;
import com.faforever.client.update.ClientConfiguration.OaMod;
import com.faforever.client.update.ClientConfiguration.OaSupport;
import com.faforever.client.update.ClientConfiguration.OpenAnnihilation;
import com.faforever.client.library.OaContentListing.ListedMap;
import com.faforever.client.preferences.PreferencesService;
import com.faforever.client.preferences.TotalAnnihilationPrefs;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LibraryServiceTest {

  private static final Path EXE = Path.of("oa", "open-annihilation.exe");
  private static final String SIM = "a5eed394ee460d28768eec0b9421a40d59796521734e220fb538d1c4a3bdb350";
  private static final String LISTING = "{\"maps\":[{\"shown-name\":\"Acid Pools\",\"key\":\"Acid Pools\",\"players\":10,\"size\":\"32 x 23\"}],"
      + "\"mods\":[{\"id\":\"talon\",\"name\":\"Talon\"}],\"expansions\":[]}";

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private final List<List<String>> commands = new ArrayList<>();
  private LibraryService service;
  private TotalAnnihilationPrefs prefs;
  private Path userFolder;
  private LibraryService.CommandRunner runner;
  private int listContentExit = 0;
  private OaInstallationService oa;
  private OaMod policy;
  private final List<List<String>> started = new ArrayList<>();

  @Before
  public void setUp() throws IOException {
    PreferencesService preferencesService = mock(PreferencesService.class);
    oa = mock(OaInstallationService.class);
    prefs = new TotalAnnihilationPrefs("oalibrary", null, "", null);
    when(preferencesService.getTotalAnnihilation("oalibrary")).thenReturn(prefs);
    when(oa.ensureExecutable("oalibrary")).thenReturn(CompletableFuture.completedFuture(EXE));
    when(oa.findInstalled("oalibrary")).thenReturn(new InstalledOa(EXE, new OaVersion(0, 8, 1)));
    policy = new OaMod();
    policy.setModTechnical("oalibrary");
    policy.setSupport(OaSupport.OA_ONLY);
    policy.setMinVersion("0.8.0");
    ClientConfiguration configuration = new ClientConfiguration();
    OpenAnnihilation openAnnihilation = new OpenAnnihilation();
    openAnnihilation.setMods(List.of(policy));
    configuration.setOpenAnnihilation(openAnnihilation);
    when(preferencesService.getClientRemoteConfiguration()).thenReturn(configuration);
    userFolder = temp.newFolder("oa-user").toPath();
    Path mod = userFolder.resolve("Mods").resolve("talon");
    Files.createDirectories(mod);
    Files.writeString(mod.resolve("oamod.yaml"), "oamod: 1\nid: talon\nname: Talon\nversion: \"1.03\"\n"
        + "author: {name: A}\npackaging: {revision: 2, date: 2026-10-04, packager: P}\n");
    runner = command -> {
      commands.add(command);
      if (command.contains("--print-profile")) {
        return new LibraryService.CommandResult(0, "{}\nsha256-sim  " + SIM + "\nsha256-full  " + "b".repeat(64) + "\n");
      }
      return new LibraryService.CommandResult(listContentExit, listContentExit == 0 ? "timing\n" + LISTING + "\n" : "no game folder");
    };
    prefs.setOaExtraArgs("--user-folder " + userFolder);
    service = new LibraryService(preferencesService, oa, runner, started::add);
  }

  @Test
  public void installedModsAreDiscoveredInTheUserFolderAndCheckedAgainstListContent() {
    List<LibraryMod> mods = service.installedMods().join();

    assertThat(mods.stream().map(LibraryMod::id).toList(), contains("talon"));
    assertThat(commands.get(0), hasItems(EXE.toString(), "--list-content", "--json", "--user-folder", userFolder.toString()));
    assertThat(commands.get(0).contains("--game-dir"), is(false));
  }

  @Test
  public void aFailedListContentMeansOaIsNotReadyAndNothingIsOffered() {
    listContentExit = 1;

    assertThat(service.installedMods().join().isEmpty(), is(true));
  }

  @Test
  public void mapsForAModComeFromListContentForThatModsFolderAndContent() {
    LibraryMod talon = service.installedMods().join().get(0);

    List<ListedMap> maps = service.mapsFor(talon).join();

    assertThat(maps.get(0).key(), is("Acid Pools"));
    List<String> command = commands.get(commands.size() - 1);
    assertThat(command, hasItems("--list-content", "--json", "--mod-dir", talon.folder().toString(), "--content", "totala"));
  }

  @Test
  public void theHostInfoCarriesTheModsVersionRevisionAndSimHashFromPrintProfile() {
    LibraryMod talon = service.installedMods().join().get(0);

    LibraryGameInfo info = service.hostInfo(talon).join();

    assertThat(info.modId(), is("talon"));
    assertThat(info.version(), is("1.03"));
    assertThat(info.revision(), is(2));
    assertThat(info.simHash(), is(SIM));
    List<String> command = commands.get(commands.size() - 1);
    assertThat(command, hasItems("--print-profile", "--mod-dir", talon.folder().toString()));
  }

  @Test
  public void theUserFolderIsOasDocumentsFolderUnlessExtraArgsNameAnother() {
    Path home = Path.of("home", "alex");

    assertThat(LibraryService.userFolder("", home), is(home.resolve("Documents").resolve("Open Annihilation")));
    assertThat(LibraryService.userFolder("--mute --user-folder D:/oa", home), is(Path.of("D:/oa")));
  }

  private static LibraryGameInfo hosted(String modId, String version) {
    return new LibraryGameInfo(modId, "Talon", "coreprime", version, 2, null, "totala", List.of());
  }

  @Test
  public void withoutAnOaOnDiskTheSnapshotSaysNoOaAndDoesNotRunOrDownloadAnything() {
    when(oa.findInstalled("oalibrary")).thenReturn(InstalledOa.NONE);

    LibrarySnapshot snapshot = service.refresh(false).join();

    assertThat(snapshot.readiness().status(), is(Status.NO_OA));
    assertThat(snapshot.mods().isEmpty(), is(true));
    assertThat(commands.isEmpty(), is(true));
    verify(oa, never()).ensureExecutable("oalibrary");
  }

  @Test
  public void whenAllowedToDownloadAMissingManagedOaIsFetchedFirst() {
    when(oa.findInstalled("oalibrary")).thenReturn(InstalledOa.NONE);
    when(oa.detectVersion(EXE)).thenReturn(java.util.Optional.of(new OaVersion(0, 8, 1)));

    LibrarySnapshot snapshot = service.refresh(true).join();

    assertThat(snapshot.readiness().status(), is(Status.READY));
    assertThat(snapshot.mods().size(), is(1));
  }

  @Test
  public void anOaOutsideTheAllowedRangeIsReportedWithoutRunningIt() {
    when(oa.findInstalled("oalibrary")).thenReturn(new InstalledOa(EXE, new OaVersion(0, 7, 3)));

    LibrarySnapshot snapshot = service.refresh(false).join();

    assertThat(snapshot.readiness().status(), is(Status.OA_VERSION_NOT_ALLOWED));
    assertThat(commands.isEmpty(), is(true));
  }

  @Test
  public void aListContentFailureMeansSetupIsNotDone() {
    listContentExit = 1;

    LibrarySnapshot snapshot = service.refresh(false).join();

    assertThat(snapshot.readiness().status(), is(Status.SETUP_NOT_DONE));
    assertThat(snapshot.mods().isEmpty(), is(true));
  }

  @Test
  public void theSnapshotGivesAJoinVerdictPerHostedGameAndIsPublishedAsAProperty() {
    LibrarySnapshot snapshot = service.refresh(false).join();

    assertThat(service.snapshotProperty().get(), is(snapshot));
    assertThat(snapshot.verdictFor(hosted("talon", "1.03")).outcome(), is(Outcome.JOINABLE));
    assertThat(snapshot.verdictFor(hosted("rhyoss", "1.0")).outcome(), is(Outcome.NEEDS_MOD));
    assertThat(snapshot.verdictFor(hosted("talon", "1.02")).outcome(), is(Outcome.JOINABLE_WITH_VERSION_HINT));
  }

  @Test
  public void openingOaStartsItsExecutableWithTheUserFoldersExtraArgsAndNothingElse() {
    service.openOa().join();

    assertThat(started.size(), is(1));
    assertThat(started.get(0), contains(EXE.toString(), "--user-folder", userFolder.toString()));
  }
}
