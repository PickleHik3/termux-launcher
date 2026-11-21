package com.termux.app.launcher;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The app menu's Uninstall starts the system's uninstall dialog. From Android 9 the system drops
 * that request without a word unless the asking app declares REQUEST_DELETE_PACKAGES, so the
 * manifest has to keep it for as long as an uninstall action exists.
 */
public class UninstallPermissionTest {

    @Test
    public void theManifestDeclaresThePermissionAnUninstallRequestNeeds() throws Exception {
        File manifest = new File("src/main/AndroidManifest.xml");
        if (!manifest.exists()) manifest = new File("app/src/main/AndroidManifest.xml");
        String text = new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8);
        assertTrue("REQUEST_DELETE_PACKAGES is declared",
            text.contains("android.permission.REQUEST_DELETE_PACKAGES"));
    }
}
