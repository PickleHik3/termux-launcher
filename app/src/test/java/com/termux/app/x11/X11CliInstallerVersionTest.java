package com.termux.app.x11;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;

/**
 * The installer rewrites the prefix scripts only when {@link X11CliInstaller#VERSION} changes, so
 * an edited script asset that ships without a bump never reaches a phone that already has the old
 * one. These hashes pin the assets to the version: when one fails, bump VERSION and record the new
 * hash here in the same commit.
 */
public class X11CliInstallerVersionTest {

    private static final int VERSION_OF_THESE_ASSETS = 15;
    private static final String GPU_SETUP_SHA256 =
        "87262325600ede79d531ace0a1ecf9471dc59aec86173f7c3d83740854e63f21";

    private static String sha256(String asset) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(new File("src/main/assets/" + asset).toPath()));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    @Test public void editedScriptAssetsComeWithAVersionBump() throws Exception {
        String bump = "a shipped X11 script changed: bump X11CliInstaller.VERSION and record the new hash here";
        assertEquals(bump, GPU_SETUP_SHA256, sha256("x11/x11-gpu-setup.sh"));
        assertEquals("VERSION changed: record the current asset hashes with it",
            VERSION_OF_THESE_ASSETS, X11CliInstaller.VERSION);
    }
}
