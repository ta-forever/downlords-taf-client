package com.faforever.client.update;

/**
 * One line of the {@link UpdateDiagnosticsService} dry-run report: whether a configured download
 * (the client installer or a hotfix archive) was reachable and passed Ed25519 signature
 * verification, without installing or extracting anything.
 */
public record UpdateCheckResult(String item, Status status, String detail) {

  public enum Status {OK, FAIL, SKIP}

  public static UpdateCheckResult ok(String item, String detail) {
    return new UpdateCheckResult(item, Status.OK, detail);
  }

  public static UpdateCheckResult fail(String item, String detail) {
    return new UpdateCheckResult(item, Status.FAIL, detail);
  }

  public static UpdateCheckResult skip(String item, String detail) {
    return new UpdateCheckResult(item, Status.SKIP, detail);
  }
}
