package com.faforever.client.remote;

import com.faforever.client.i18n.I18n;
import com.faforever.client.preferences.EngineUpdates;
import com.faforever.client.remote.domain.NoticeMessage;

/**
 * Words the server's structured Open Annihilation "version not allowed" refusal to fit the
 * player's Engine updates setting (CONTEXT.md).
 */
public final class OaVersionRefusalText {

  public static final String REASON_OA_VERSION_NOT_ALLOWED = "oa_version_not_allowed";

  private OaVersionRefusalText() {
  }

  /**
   * @return the text to show, or null when the server's own text should be shown as it is (not this refusal,
   * an older server without the reason, or the TAF setting where the server's text already fits).
   */
  public static String forNotice(NoticeMessage notice, EngineUpdates setting, I18n i18n) {
    if (!REASON_OA_VERSION_NOT_ALLOWED.equals(notice.getReason()) || setting == null) {
      return null;
    }
    String key;
    switch (setting) {
      case OPEN_ANNIHILATION -> key = "oa.versionNotAllowed.openAnnihilation";
      case OWN_INSTALL -> key = "oa.versionNotAllowed.own";
      default -> {
        return null;
      }
    }
    String yours = notice.getYourVersion() != null ? notice.getYourVersion() : i18n.get("oa.versionNotAllowed.unknownVersion");
    return i18n.get(key, yours, range(notice.getMinVersion(), notice.getMaxVersion(), i18n));
  }

  private static String range(String min, String max, I18n i18n) {
    if (min != null && max != null) {
      return i18n.get("oa.versionNotAllowed.range.between", min, max);
    }
    if (min != null) {
      return i18n.get("oa.versionNotAllowed.range.min", min);
    }
    if (max != null) {
      return i18n.get("oa.versionNotAllowed.range.max", max);
    }
    return i18n.get("oa.versionNotAllowed.range.any");
  }
}
