package com.termux.app.x11;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The graphics setup script's promises that a device check would otherwise be the only proof of. */
public class X11GpuSetupScriptTest {

    private static String script() throws Exception {
        File f = new File("src/main/assets/x11/x11-gpu-setup.sh");
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test public void testDisplayIsHeadlessSoTheLauncherNeverAdoptsIt() throws Exception {
        String s = script();
        assertTrue(s.contains("TERMUX_X11_HEADLESS=1 termux-x11"));
        assertFalse(s.contains("is already running; using it"));
    }

    @Test public void vulkanWrapperIsDownloadedAndPinned() throws Exception {
        String s = script();
        for (String sum : new String[] {
            "407a024fe25c37e34e83d32a199eb82e204aa1c80f391c5eecc88d5d4ca98f96",
            "dec9d90eed2cb4d5e21acd665661e3f20476d13711b9c27eb38d9c276a3d4890",
            "fc11784fee6a553eae78b30a4bff267981b735be7b1d4fac4e894e09be8058ca",
            "2fbbf12d692534e640d270faa8f840c08ef0d38a65cbb191dd1bf896a32b923f"}) {
            assertTrue(sum, s.contains(sum));
        }
        assertTrue(s.contains("sha256sum"));
    }
}
