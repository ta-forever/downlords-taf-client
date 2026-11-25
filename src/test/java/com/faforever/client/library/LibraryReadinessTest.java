package com.faforever.client.library;

import com.faforever.client.fa.oa.OaVersion;
import com.faforever.client.library.LibraryReadiness.Status;
import com.faforever.client.update.ClientConfiguration.OaMod;
import com.faforever.client.update.ClientConfiguration.OaSupport;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class LibraryReadinessTest {

  private static final OaVersion V081 = new OaVersion(0, 8, 1);

  private static OaMod policy(String min, String max) {
    OaMod mod = new OaMod();
    mod.setModTechnical("oalibrary");
    mod.setSupport(OaSupport.OA_ONLY);
    mod.setMinVersion(min);
    mod.setMaxVersion(max);
    return mod;
  }

  @Test
  public void noOaFoundMeansNoOa() {
    LibraryReadiness readiness = LibraryReadiness.assess(policy("0.8.0", null), false, null, null);

    assertThat(readiness.status(), is(Status.NO_OA));
    assertThat(readiness.requiredVersion(), is("0.8.0"));
  }

  @Test
  public void aServerThatDoesNotOfferOaForLibraryModsMeansNoOa() {
    assertThat(LibraryReadiness.assess(null, true, V081, 0).status(), is(Status.NO_OA));
  }

  @Test
  public void aVersionOutsideTheAllowedRangeIsNotAllowed() {
    LibraryReadiness tooOld = LibraryReadiness.assess(policy("0.8.0", null), true, new OaVersion(0, 7, 3), 0);
    LibraryReadiness tooNew = LibraryReadiness.assess(policy("0.8.0", "0.8.2"), true, new OaVersion(0, 9, 0), 0);

    assertThat(tooOld.status(), is(Status.OA_VERSION_NOT_ALLOWED));
    assertThat(tooOld.foundVersion(), is("0.7.3"));
    assertThat(tooNew.status(), is(Status.OA_VERSION_NOT_ALLOWED));
  }

  @Test
  public void listContentExitingNonZeroMeansFirstRunSetupIsNotDone() {
    assertThat(LibraryReadiness.assess(policy("0.8.0", null), true, V081, 1).status(), is(Status.SETUP_NOT_DONE));
  }

  @Test
  public void anAllowedVersionWhoseListContentWorksIsReady() {
    LibraryReadiness readiness = LibraryReadiness.assess(policy("0.8.0", null), true, V081, 0);

    assertThat(readiness.status(), is(Status.READY));
    assertThat(readiness.isReady(), is(true));
  }

  @Test
  public void aVersionOaCannotBeToldIsNotHeldAgainstIt() {
    assertThat(LibraryReadiness.assess(policy("0.8.0", null), true, null, 0).status(), is(Status.READY));
  }
}
