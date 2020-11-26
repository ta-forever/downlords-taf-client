package com.faforever.client.fa;

import com.faforever.client.game.Faction;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class LaunchCommandBuilderTest {

  private static LaunchCommandBuilder defaultBuilder() {
    return LaunchCommandBuilder.create()
        .gameExecutable("test.exe")
        .logFile(Paths.get("preferences.log"))
        .username("junit");
  }

  @Test
  public void testAllSet() throws Exception {
    assertNotNull(defaultBuilder().build());
  }

  @Test(expected = IllegalStateException.class)
  public void testExecutableNullThrowsException() throws Exception {
    defaultBuilder().gameExecutable(null).build();
  }

  @Test
  public void testUidNullAllowed() throws Exception {
    defaultBuilder().uid(null).build();
  }

  @Test
  public void testMeanNullAllowed() throws Exception {
    defaultBuilder().mean(null).build();
  }

  @Test
  public void testDeviationNullAllowed() throws Exception {
    defaultBuilder().deviation(null).build();
  }

  @Test
  public void testCountryNullAllowed() throws Exception {
    defaultBuilder().country(null).build();
  }

  @Test(expected = IllegalStateException.class)
  public void testUsernameNullNotAllowedIfUidSet() throws Exception {
    defaultBuilder().uid(123).username(null).build();
  }

  @Test
  public void testUsernameNullAllowedIfUidNotSet() throws Exception {
    defaultBuilder().uid(null).username(null).build();
  }

  @Test
  public void testLogFileNullAllowed() throws Exception {
    defaultBuilder().logFile(null).build();
  }

  @Test
  public void testAdditionalArgsNullThrowsNoException() throws Exception {
    defaultBuilder().additionalArgs(null).build();
  }

    @Test
  public void testCommandFormatWithSpaces() throws Exception {
    String pathWithSpaces = "mypath/with space/test.exe";
    assertThat(
        defaultBuilder()
            .gameExecutable(pathWithSpaces)
            .build(),
        contains(
            "/path/to/my/wineprefix", "primusrun", "wine", Paths.get(pathWithSpaces).toAbsolutePath().toString(),
            "/init", "init.lua",
            "/nobugreport",
            "/log", Paths.get("preferences.log").toAbsolutePath().toString()
        ));
  }

  @Test
  public void testCommandFormatWithRedundantQuotionMarks() throws Exception {
    assertThat(
        defaultBuilder()
            .build(),
        contains(
            "/path/to/my/wineprefix", "primusrun", "wine", Paths.get("test.exe").toAbsolutePath().toString(),
            "/init", "init.lua",
            "/nobugreport",
            "/log", Paths.get("preferences.log").toAbsolutePath().toString()
        ));
  }

}
