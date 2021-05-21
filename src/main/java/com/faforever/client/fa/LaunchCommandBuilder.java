package com.faforever.client.fa;

import org.springframework.util.Assert;

import java.net.Inet4Address;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.faforever.client.util.Assert.checkNullIllegalState;

public class LaunchCommandBuilder {

  private static final Pattern QUOTED_STRING_PATTERN = Pattern.compile("([^\"]\\S*|\"+.+?\"+)\\s*");
  private static final String QUOTED_STRING_DECORATOR = "\"%s\"";

  private boolean requireUac;
  private Path gpgnet4taExecutable;
  private String baseModName;
  private Path gameInstalledPath;
  private String gameExecutable;
  private String gameCommandLineOptions;
  private boolean upnpEnabled;
  private boolean proactiveResendEnabled;
  private boolean autoLaunch;

  private Integer uid;
  private String username;
  private String country;
  private Float mean;
  private Float deviation;

  private List<String> additionalArgs;
  private Integer localGpgPort;
  private URI replayUri;
  private Path logFile;
  private String ircUrl;
  private Integer consolePort;

  private Integer launchServerPort;
  private boolean startLaunchServer;

  public static LaunchCommandBuilder create() {
    return new LaunchCommandBuilder();
  }

  private static List<String> split(String string) {
    Matcher matcher = QUOTED_STRING_PATTERN.matcher(string);
    ArrayList<String> result = new ArrayList<>();
    while (matcher.find()) {
      result.add(matcher.group(1).replace("\"", ""));
    }
    return result;
  }

  public LaunchCommandBuilder launchServerPort(int port) {
    this.launchServerPort = port;
    return this;
  }

  public LaunchCommandBuilder startLaunchServer(boolean start) {
    this.startLaunchServer = start;
    return this;
  }

  public LaunchCommandBuilder requireUac(boolean requireUac) {
    this.requireUac = requireUac;
    return this;
  }

  public LaunchCommandBuilder gpgnet4taExecutable(Path gpgnet4taExecutable) {
    this.gpgnet4taExecutable = gpgnet4taExecutable;
    return this;
  }

    public LaunchCommandBuilder baseModName(String modName) {
    this.baseModName = modName;
    return this;
  }

  public LaunchCommandBuilder gameInstalledPath(Path gameInstalledPath) {
    this.gameInstalledPath = gameInstalledPath;
    return this;
  }

  public LaunchCommandBuilder gameExecutable(String gameExecutable) {
    this.gameExecutable = gameExecutable;
    return this;
  }

  public LaunchCommandBuilder gameCommandLineOptions(String gameCommandLineOptions) {
    this.gameCommandLineOptions = gameCommandLineOptions;
    return this;
  }

  public LaunchCommandBuilder upnpEnabled(boolean upnpEnabled) {
    this.upnpEnabled = upnpEnabled;
    return this;
  }

  public LaunchCommandBuilder proactiveResendEnabled(boolean proactiveResendEnabled) {
    this.proactiveResendEnabled = proactiveResendEnabled;
    return this;
  }

  public LaunchCommandBuilder localGpgPort(int localGpgPort) {
    this.localGpgPort = localGpgPort;
    return this;
  }

  public LaunchCommandBuilder uid(Integer uid) {
    this.uid = uid;
    return this;
  }

  public LaunchCommandBuilder mean(Float mean) {
    this.mean = mean;
    return this;
  }

  public LaunchCommandBuilder deviation(Float deviation) {
    this.deviation = deviation;
    return this;
  }

  public LaunchCommandBuilder country(String country) {
    this.country = country;
    return this;
  }

  public LaunchCommandBuilder username(String username) {
    this.username = username;
    return this;
  }

  public LaunchCommandBuilder additionalArgs(List<String> additionalArgs) {
    this.additionalArgs = additionalArgs;
    return this;
  }

  public LaunchCommandBuilder replayUri(URI replayUri) {
    this.replayUri = replayUri;
    return this;
  }

  public LaunchCommandBuilder logFile(Path logFile) {
    this.logFile = logFile;
    return this;
  }

  public LaunchCommandBuilder ircUrl(String ircUrl) {
    this.ircUrl = ircUrl;
    return this;
  }

  public LaunchCommandBuilder autoLaunch(boolean autoLaunch) {
    this.autoLaunch = autoLaunch;
    return this;
  }

  public LaunchCommandBuilder consolePort(Integer consolePort) {
    this.consolePort = consolePort;
    return this;
  }

  public List<String> build() {
    checkNullIllegalState(gpgnet4taExecutable, "gpgnet4ta executable has not been set");
    Assert.state(!(replayUri != null && uid != null), "uid and replayUri cannot be set at the same time");
    Assert.state(!(uid != null && username == null), "username has not been set");

    List<String> command = new ArrayList<>();
    command.add(String.format(QUOTED_STRING_DECORATOR, gpgnet4taExecutable.toAbsolutePath().toString()));

    if (this.startLaunchServer) {
      command.add("--launchserver");
    }

    if (this.launchServerPort != null) {
      command.add("--launchserverport");
      command.add(String.valueOf(launchServerPort));
    }

    if (requireUac) {
      command.add("--uac");
    }

    if (baseModName != null) {
      command.add("--gamemod");
      command.add(baseModName);
    }

    if (gameInstalledPath != null) {
      command.add("--gamepath");
      command.add(String.format(QUOTED_STRING_DECORATOR, gameInstalledPath.toAbsolutePath().toString()));
    }

    if (gameExecutable != null) {
      command.add("--gameexe");
      command.add(String.format(QUOTED_STRING_DECORATOR, gameExecutable));
    }

    if (gameCommandLineOptions != null) {
      command.add("--gameargs");
      command.add(String.format(QUOTED_STRING_DECORATOR, gameCommandLineOptions));
    }

    if (upnpEnabled) {
      //command.add("--upnp");
    }

    if (proactiveResendEnabled) {
      command.add("--proactiveresend");
    }

    if (autoLaunch) {
      command.add("--autolaunch");
    }

    String localIp = Inet4Address.getLoopbackAddress().getHostAddress();
    if (localGpgPort != null) {
      command.add("--gpgnet");
      command.add(localIp + ":" + localGpgPort);
    }

    if (mean != null) {
      //command.add("--mean");
      //command.add(String.valueOf(mean));
    }

    if (deviation != null) {
      //command.add("--deviation");
      //command.add(String.valueOf(deviation));
    }

    if (country != null && !country.isEmpty()) {
      //command.add("--country");
      //command.add(country);
    }

    if (logFile != null) {
      command.add("--logfile");
      command.add(String.format(QUOTED_STRING_DECORATOR, logFile.toString()));
    }

    if (ircUrl != null) {
      command.add("--irc");
      command.add(ircUrl);
    }

    if (consolePort != null) {
      command.add("--consoleport");
      command.add(String.valueOf(consolePort));
    }

    if (additionalArgs != null) {
      List<String> args = additionalArgs.stream()
          .map((String arg) -> arg.replace("/", "--"))
          .collect(Collectors.toList());

      List<String> allowedArgs = Arrays.asList("--numgames", "--players");
      List<String> validArgs = new ArrayList<String>();
      boolean isValid = false;
      for(String arg: args) {
        if (arg.length()>2 && arg.startsWith("--")) {
          isValid = allowedArgs.contains(arg);
        }
        if (isValid) {
          validArgs.add(arg);
        }
      }
      //command.addAll(validArgs);
    }

    return command;
  }
}
