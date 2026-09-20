package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** D9's one piece of memory: that the user already turned this situation down. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class DistroSetupStoreTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private Context context;
    private DistroSetupStore store;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        store = new DistroSetupStore(context);
    }

    private File containersWith(String container, boolean apt, boolean user) throws IOException {
        File containers = temp.newFolder();
        File rootfs = new File(containers, container + "/rootfs");
        assertTrue(rootfs.mkdirs());
        if (apt) {
            File tool = new File(rootfs, "usr/bin/apt-get");
            tool.getParentFile().mkdirs();
            Files.write(tool.toPath(), "x".getBytes(StandardCharsets.UTF_8));
        }
        if (user) {
            File passwd = new File(rootfs, "etc/passwd");
            passwd.getParentFile().mkdirs();
            Files.write(passwd.toPath(), "user:x:1000:1000::/home/user:/bin/sh\n"
                .getBytes(StandardCharsets.UTF_8));
        }
        return containers;
    }

    @Test public void nothingIsDismissedToStartWith() {
        assertEquals("", store.dismissed());
    }

    @Test public void aNotNowIsRememberedAcrossStores() throws IOException {
        DistroSetup.Readiness readiness = DistroSetup.read(containersWith("debian", true, false));

        store.dismiss(readiness);

        assertEquals("debian|user,fonts", new DistroSetupStore(context).dismissed());
    }

    // --- when the offer is out ---------------------------------------------------------------

    @Test public void anUntouchedPhoneIsOfferedTheFlow() throws IOException {
        DistroSetup.Readiness readiness = DistroSetup.read(temp.newFolder());

        assertTrue(DistroSetupStore.shouldOffer(readiness, "", true));
    }

    @Test public void theOfferIsWithheldOutsideThePlacesRestingState() throws IOException {
        DistroSetup.Readiness readiness = DistroSetup.read(temp.newFolder());

        assertFalse(DistroSetupStore.shouldOffer(readiness, "", false));
    }

    @Test public void aContainerThatIsAlreadySetUpIsNeverOffered() throws IOException {
        File containers = containersWith("debian", true, true);
        File fonts = new File(containers, "debian/rootfs/usr/share/fonts/X11/misc/fixed.pcf.gz");
        fonts.getParentFile().mkdirs();
        Files.write(fonts.toPath(), "x".getBytes(StandardCharsets.UTF_8));

        assertFalse(DistroSetupStore.shouldOffer(DistroSetup.read(containers), "", true));
    }

    @Test public void sayingNotNowTakesTheOfferAwayForGood() throws IOException {
        File containers = containersWith("debian", true, false);
        DistroSetup.Readiness readiness = DistroSetup.read(containers);
        store.dismiss(readiness);

        assertFalse(DistroSetupStore.shouldOffer(DistroSetup.read(containers), store.dismissed(), true));
    }

    @Test public void theOfferComesBackWhenTheSituationChanges() throws IOException {
        File empty = temp.newFolder();
        store.dismiss(DistroSetup.read(empty));
        assertFalse(DistroSetupStore.shouldOffer(DistroSetup.read(empty), store.dismissed(), true));

        // A container installed by hand later is a different situation, so it is offered once more.
        File containers = containersWith("debian", true, false);
        assertTrue(DistroSetupStore.shouldOffer(DistroSetup.read(containers), store.dismissed(), true));
    }

    // --- what answers the offer --------------------------------------------------------------

    @Test public void copyingTheCommandIsWhatTakesTheOfferAway() throws IOException {
        File containers = containersWith("debian", true, false);
        assertTrue(DistroSetupStore.shouldOffer(DistroSetup.read(containers), store.dismissed(), true));

        // What the Get GUI apps screen does the moment the command reaches the clipboard.
        DistroSetupStore.dismissCurrent(context, containers);

        assertEquals("debian|user,fonts", store.dismissed());
        assertFalse(DistroSetupStore.shouldOffer(DistroSetup.read(containers), store.dismissed(), true));
    }

    @Test public void merelyOpeningTheScreenLeavesTheOfferWhereItWas() throws IOException {
        File containers = containersWith("debian", true, false);

        // Opening the screen records nothing, so a user who backs out of it is offered again.
        assertEquals("", store.dismissed());
        assertTrue(DistroSetupStore.shouldOffer(DistroSetup.read(containers), store.dismissed(), true));
    }

    @Test public void copyingRecordsTheSituationAsItIsNotAsItWasOffered() throws IOException {
        File containers = containersWith("debian", true, false);

        // The account appears between the offer being made and the command being copied.
        File passwd = new File(containers, "debian/rootfs/etc/passwd");
        passwd.getParentFile().mkdirs();
        Files.write(passwd.toPath(), "user:x:1000:1000::/home/user:/bin/sh\n"
            .getBytes(StandardCharsets.UTF_8));
        DistroSetupStore.dismissCurrent(context, containers);

        assertEquals("debian|fonts", store.dismissed());
    }

    @Test public void finishingHalfTheJobByHandIsANewSituation() throws IOException {
        File containers = containersWith("debian", true, false);
        store.dismiss(DistroSetup.read(containers));

        File passwd = new File(containers, "debian/rootfs/etc/passwd");
        passwd.getParentFile().mkdirs();
        Files.write(passwd.toPath(), "user:x:1000:1000::/home/user:/bin/sh\n"
            .getBytes(StandardCharsets.UTF_8));

        assertTrue(DistroSetupStore.shouldOffer(DistroSetup.read(containers), store.dismissed(), true));
    }
}
