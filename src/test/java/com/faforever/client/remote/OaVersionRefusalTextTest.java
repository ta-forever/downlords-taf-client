package com.faforever.client.remote;

import com.faforever.client.i18n.I18n;
import com.faforever.client.preferences.EngineUpdates;
import com.faforever.client.remote.domain.NoticeMessage;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class OaVersionRefusalTextTest {

  private I18n i18n;

  @Before
  public void setUp() {
    // Echo "key(arg,arg)" so the test sees which key was chosen and what it was given.
    i18n = mock(I18n.class, inv -> {
      Object[] a = inv.getArguments();
      StringBuilder sb = new StringBuilder(String.valueOf(a[0]));
      if (a.length > 1) {
        sb.append('(');
        for (int i = 1; i < a.length; i++) {
          if (i > 1) {
            sb.append(',');
          }
          sb.append(a[i]);
        }
        sb.append(')');
      }
      return sb.toString();
    });
  }

  private static NoticeMessage notice(String reason, String yours, String min, String max) {
    NoticeMessage m = new NoticeMessage();
    m.setText("server text");
    m.setReason(reason);
    m.setYourVersion(yours);
    m.setMinVersion(min);
    m.setMaxVersion(max);
    return m;
  }

  @Test
  public void tafSettingKeepsServerText() {
    assertNull(OaVersionRefusalText.forNotice(notice("oa_version_not_allowed", "0.8.1", "0.8.5", "0.8.9"), EngineUpdates.TAF, i18n));
  }

  @Test
  public void openAnnihilationSettingPointsAtOaUpdates() {
    assertEquals("oa.versionNotAllowed.openAnnihilation(0.8.1,oa.versionNotAllowed.range.between(0.8.5,0.8.9))",
        OaVersionRefusalText.forNotice(notice("oa_version_not_allowed", "0.8.1", "0.8.5", "0.8.9"), EngineUpdates.OPEN_ANNIHILATION, i18n));
  }

  @Test
  public void ownInstallSettingAsksToUpdateOrReplace() {
    assertEquals("oa.versionNotAllowed.own(0.8.1,oa.versionNotAllowed.range.between(0.8.5,0.8.9))",
        OaVersionRefusalText.forNotice(notice("oa_version_not_allowed", "0.8.1", "0.8.5", "0.8.9"), EngineUpdates.OWN_INSTALL, i18n));
  }

  @Test
  public void missingYourVersionSaysUnknownAndOpenRangesAreWorded() {
    assertEquals("oa.versionNotAllowed.openAnnihilation(oa.versionNotAllowed.unknownVersion,oa.versionNotAllowed.range.min(0.8.5))",
        OaVersionRefusalText.forNotice(notice("oa_version_not_allowed", null, "0.8.5", null), EngineUpdates.OPEN_ANNIHILATION, i18n));
    assertEquals("oa.versionNotAllowed.openAnnihilation(0.8.1,oa.versionNotAllowed.range.max(0.8.9))",
        OaVersionRefusalText.forNotice(notice("oa_version_not_allowed", "0.8.1", null, "0.8.9"), EngineUpdates.OPEN_ANNIHILATION, i18n));
    assertEquals("oa.versionNotAllowed.own(0.8.1,oa.versionNotAllowed.range.any)",
        OaVersionRefusalText.forNotice(notice("oa_version_not_allowed", "0.8.1", null, null), EngineUpdates.OWN_INSTALL, i18n));
  }

  @Test
  public void noReasonFallsBackToServerText() {
    assertNull(OaVersionRefusalText.forNotice(notice(null, "0.8.1", "0.8.5", "0.8.9"), EngineUpdates.OPEN_ANNIHILATION, i18n));
    assertNull(OaVersionRefusalText.forNotice(notice("something_else", "0.8.1", "0.8.5", "0.8.9"), EngineUpdates.OWN_INSTALL, i18n));
  }

  @Test
  public void theServersSnakeCaseJsonBindsToTheNoticeFields() {
    NoticeMessage m = new com.google.gson.GsonBuilder()
        .setFieldNamingPolicy(com.google.gson.FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create()
        .fromJson("{\"command\":\"notice\",\"style\":\"game_join_fail\",\"text\":\"server text\","
            + "\"reason\":\"oa_version_not_allowed\",\"your_version\":\"0.8.1\",\"min_version\":\"0.8.5\","
            + "\"max_version\":null}", NoticeMessage.class);

    assertEquals("oa.versionNotAllowed.own(0.8.1,oa.versionNotAllowed.range.min(0.8.5))",
        OaVersionRefusalText.forNotice(m, EngineUpdates.OWN_INSTALL, i18n));
  }
}
