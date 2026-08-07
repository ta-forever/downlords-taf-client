package com.faforever.client.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "faf-client", ignoreUnknownFields = false)
public class ClientProperties {

  private String mainWindowTitle = "Downlord's TAF Client";
  private News news = new News();
  private ForgedAlliance forgedAlliance = new ForgedAlliance();
  private Irc irc = new Irc();
  private Server server = new Server();
  private Vault vault = new Vault();
  private Tada tada = new Tada();
  private Replay replay = new Replay();
  private Imgur imgur = new Imgur();
  private TrueSkill trueSkill = new TrueSkill();
  private Api api = new Api();
  private UnitDatabase unitDatabase = new UnitDatabase();
  private MapGenerator mapGenerator = new MapGenerator();
  private Website website = new Website();
  private Discord discord = new Discord();
  private String translationProjectUrl;
  private String clientConfigUrl;
  private boolean useRemotePreferences;
  private Duration clientConfigConnectTimeout = Duration.ofSeconds(30);
  private boolean showIceAdapterDebugWindow;
  /** Cutover switch for the legacy skill-rating (MMR) display. While the lobby's in-process rater
   * is authoritative, show the rating alongside Ladder Points so players keep their familiar number
   * (LP is the hero). Flip to false when rating moves to the combat rating service (combat rating is
   * hidden, LP is the only player-facing number) — at which point the rating delta has nothing live
   * to show. Defaults on; a remote client-config can flip it at cutover without a redeploy. */
  private boolean showLegacyRating = true;
  private Map<String, String> links = new HashMap<>();
  private GalacticWar galacticWar = new GalacticWar();
  private Wager wager = new Wager();
  private LiveViewer liveViewer = new LiveViewer();

  @Data
  public static class News {
    /**
     * URL to fetch the RSS news feed from.
     */
    private String feedUrl;
  }

  @Data
  public static class ForgedAlliance {
    /**
     * Title of the Total Annihilation window. Required to find the window handle.
     */
    private String windowTitle = "Total Annihilation";

    /**
     * URL to download the ForgedAlliance.exe from.
     */
    private String exeUrl;
  }

  @Data
  public static class Irc {
    private String host;
    private int port = 8167;
    private int reconnectDelay = (int) Duration.ofSeconds(5).toMillis();
  }

  @Data
  public static class Server {
    private String host;
    private int port = 8001;
  }

  @Data
  public static class Vault {
    private String baseUrl;
    private String mapRulesUrl;
    private String modRulesUrl;
    private String mapValidationUrl;
    private String mapDownloadUrlFormat;
    private String mapPreviewUrlFormat;
    private String replayDownloadUrlFormat;
  }

  @Data
  public static class Tada {
    private String rootUrl;
    private String downloadReplayUrlRegex;
    private String browseReplayUrlRegex;
    private String replayUrlRegex;
    private String tadaUrlRegex;
    private String replayDownloadEndpointFormat;
  }

  @Data
  public static class Replay {
    private String remoteHost;
    private int remotePort = 15000;
    private String replayFileFormat = "%d-%s.tad";
    private String replayFileGlob = "*.tad";
    // TODO this should acutally be reported by the server
    private int watchDelaySeconds = 300;

    // Demo compiler TLS. The plaintext (remotePort) and TLS (compilerTlsPort) endpoints run
    // in parallel during migration, so this is a config-only switch. The TLS endpoint is
    // terminated by traefik, which matches on SNI - so remoteHost must stay a hostname.
    private boolean compilerTls = false;
    private int compilerTlsPort = 15443;
    private boolean compilerPlaintextFallback = true;

    public int getCompilerPort() {  // the demo compiler gathers game data from each player to compile a .tad file
      return compilerTls ? compilerTlsPort : remotePort;
    }

    // Plaintext port gpgnet4ta falls back to if TLS won't connect, so a broken TLS path
    // never costs us a demo. 0 when TLS is off (we are already on the plaintext port) or
    // when the fallback is disabled. Closing the port server-side is what actually retires
    // plaintext; this client-side preference cannot enforce that and does not try to.
    public int getCompilerPlaintextFallbackPort() {
      return compilerTls && compilerPlaintextFallback ? remotePort : 0;
    }

    public int getReplayServerPort() {  // the replay server plays back the .tad file recorded by the demo compiler
      return remotePort +1;
    }
  }

  @Data
  public static class LiveViewer {
    /** Page that hosts the browser-based 3D live viewer; %d is the game id. The ticket and
     * local asset-server URL are passed in the fragment (never the query) so they stay out of
     * server access logs. Empty/null disables the "Watch in browser" menu item. */
    private String urlFormat;
    /** Port for the local asset HTTP server the viewer page fetches game assets from.
     * 0 (default) picks an ephemeral port. */
    private int assetServerPort = 0;
  }

  @Data
  public static class Imgur {
    private Upload upload = new Upload();

    @Data
    public static class Upload {
      private String baseUrl = "https://api.imgur.com/3/image";
      private String clientId;
      private int maxSize = 2097152;
    }
  }

  /**
   * @deprecated load from server
   */
  @Data
  @Deprecated
  public static class TrueSkill {
    private int initialStandardDeviation;
    private int initialMean;
    private int beta;
    private float dynamicFactor;
    private float drawProbability;
  }

  @Data
  public static class Website {
    private String baseUrl;
    private String forgotPasswordUrl;
    private String createAccountUrl;
    private String reportUrl;
    private String newsHubUrl;
  }

  @Data
  public static class Api {
    private String baseUrl;
    private String clientId;
    private String clientSecret;
    private int maxPageSize = 10_000;
  }

  @Data
  public static class UnitDatabase {
    private String spookiesUrl;
    private String rackOversUrl;
  }

  @Data
  public static class MapGenerator {
    private String downloadUrlFormat;
    private String repoAndOwnerName;
    private String queryLatestVersionUrl;
    private String queryVersionsUrl;
    private int maxSupportedMajorVersion;
    private int minSupportedMajorVersion;
  }

  @Data
  public static class Discord {
    private String applicationId;
    private String smallImageKey;
    private String bigImageKey;
    /** URL to join Discord server. */
    private List<DiscordServer> servers;
  }

  @Data
  public static class DiscordServer {
    private String title;
    private String url;
  }

  @Data
  public static class GalacticWar {
    /**
     * URL to fetch the latest GW state from
     */
    private String url;
  }

  @Data
  public static class Wager {
    /**
     * User id of the house model-maker bot (the server's WAGER_BOT_USER_ID / the DB's
     * wager_config.bot_user_id, not exposed via the API). Its trades shape the price line but
     * are excluded from the per-trade "who traded" markers on the price charts. 0 disables the
     * filter (every trade gets a marker).
     */
    private int botUserId = 522;
  }
}
