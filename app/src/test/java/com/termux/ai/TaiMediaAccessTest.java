package com.termux.ai;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.net.InetAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The media policy is what stands between an OpenAI-compatible chat request and this app's UID:
 * without it a content part is a file-read and an SSRF primitive pointed at the device.
 */
@RunWith(RobolectricTestRunner.class)
public class TaiMediaAccessTest {

    @Test
    public void localPath_insideTermuxHomeIsAccepted() throws Exception {
        String path = TermuxConstants.TERMUX_HOME_DIR_PATH + "/pictures/screenshot.png";
        assertEquals(path, TaiMediaAccess.resolveLocalPath(path));
    }

    @Test
    public void localPath_outsideTheAllowedRootsIsRefused() {
        assertRefused("/data/data/com.termux/shared_prefs/com.termux_preferences.xml");
        assertRefused("/proc/self/environ");
        assertRefused("/data/misc/keystore/user_0/.masterkey");
    }

    @Test
    public void localPath_traversalOutOfAnAllowedRootIsRefused() {
        assertRefused(TermuxConstants.TERMUX_HOME_DIR_PATH + "/../usr/etc/passwd");
    }

    @Test
    public void localPath_launcherCtlStateIsRefusedEvenInsideHome() {
        assertRefused(TermuxConstants.TERMUX_HOME_DIR_PATH + "/.launcherctl/token");
        assertRefused(TermuxConstants.TERMUX_HOME_DIR_PATH + "/.launcherctl/notifications.jsonl");
    }

    @Test
    public void localPath_emptyIsRefused() {
        assertRefused("   ");
    }

    @Test
    public void destinations_privateAndLoopbackAddressesAreNotPublic() throws Exception {
        assertFalse(TaiMediaAccess.isPublicAddress(InetAddress.getByName("127.0.0.1")));
        assertFalse(TaiMediaAccess.isPublicAddress(InetAddress.getByName("0.0.0.0")));
        assertFalse(TaiMediaAccess.isPublicAddress(InetAddress.getByName("10.1.2.3")));
        assertFalse(TaiMediaAccess.isPublicAddress(InetAddress.getByName("192.168.1.1")));
        assertFalse(TaiMediaAccess.isPublicAddress(InetAddress.getByName("172.16.0.1")));
        // The cloud metadata address, the classic SSRF target.
        assertFalse(TaiMediaAccess.isPublicAddress(InetAddress.getByName("169.254.169.254")));
        // Carrier-grade NAT, which isSiteLocalAddress alone does not cover.
        assertFalse(TaiMediaAccess.isPublicAddress(InetAddress.getByName("100.64.0.1")));
        assertFalse(TaiMediaAccess.isPublicAddress(InetAddress.getByName("::1")));
        assertFalse(TaiMediaAccess.isPublicAddress(InetAddress.getByName("fd00::1")));

        assertTrue(TaiMediaAccess.isPublicAddress(InetAddress.getByName("8.8.8.8")));
        assertTrue(TaiMediaAccess.isPublicAddress(InetAddress.getByName("1.1.1.1")));
        assertTrue(TaiMediaAccess.isPublicAddress(InetAddress.getByName("2606:4700::1")));
    }

    @Test
    public void fetch_refusesNonHttpSchemes() {
        try {
            TaiMediaAccess.fetch("ftp://example.com/image.png", 1024);
            fail("Expected the scheme to be refused");
        } catch (JSONException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("media_fetch_failed"));
        }
    }

    @Test
    public void fetch_refusesLoopbackDestinationsBeforeConnecting() {
        // The API server itself listens on loopback: an unchecked fetch would let a chat request
        // drive it with whatever authority the request already has.
        try {
            TaiMediaAccess.fetch("http://127.0.0.1:11434/v1/models", 1024);
            fail("Expected the loopback destination to be refused");
        } catch (JSONException e) {
            assertEquals("media_access_denied:Media host resolves to a non-public address",
                e.getMessage());
        }
    }

    private static void assertRefused(String path) {
        try {
            String resolved = TaiMediaAccess.resolveLocalPath(path);
            fail("Expected " + path + " to be refused, resolved to " + resolved);
        } catch (JSONException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("media_access_denied")
                || e.getMessage().startsWith("media_fetch_failed"));
        }
    }

    @Test
    public void localPath_sharedStorageIsAcceptedWhenPresent() throws Exception {
        File shared = android.os.Environment.getExternalStorageDirectory();
        if (shared == null) return;
        String path = shared.getAbsolutePath() + "/Pictures/photo.jpg";
        assertEquals(path, TaiMediaAccess.resolveLocalPath(path));
    }
}
