package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.os.Build;

import com.termux.app.x11.X11Apps;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@link LauncherCategoryPasteImporter#apply} must refuse a pasted line for the reserved
 * Linux-apps package ({@code x11:linux}) even when it is handed to it directly, regardless of
 * whether it also appears in {@code knownPackages} — an AI reply can echo back a package name it
 * was never asked to place.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherCategoryPasteImporterTest {

    private File categoryFile;

    @Before public void setUp() {
        categoryFile = new File(
            RuntimeEnvironment.getApplication().getCacheDir(), "app-categories-paste-test.conf");
        categoryFile.delete();
    }

    @Test public void aPastedLineForTheLinuxAppsPackageIsRefused() {
        Set<String> knownPackages = new LinkedHashSet<>(
            java.util.Arrays.asList("com.example.notes", X11Apps.PACKAGE));
        String reply = "[productivity]\ncom.example.notes\n" + X11Apps.PACKAGE + "\n";

        LauncherCategoryPasteImporter.Result result = LauncherCategoryPasteImporter.apply(
            RuntimeEnvironment.getApplication(), categoryFile, knownPackages, reply);

        assertEquals(1, result.applied);
        assertNull(result.errorMessage);
        try {
            LauncherCategoryFile written = LauncherCategoryFile.parse(categoryFile);
            assertNull("the reserved package must never land in the file",
                written.categoryForPackage(X11Apps.PACKAGE));
            assertEquals("productivity", written.categoryForPackage("com.example.notes"));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test public void anOrdinaryPastedLineIsUnaffected() {
        Set<String> knownPackages = Collections.singleton("com.example.notes");
        String reply = "[productivity]\ncom.example.notes\n";

        LauncherCategoryPasteImporter.Result result = LauncherCategoryPasteImporter.apply(
            RuntimeEnvironment.getApplication(), categoryFile, knownPackages, reply);

        assertEquals(1, result.applied);
        assertFalse(result.isFailure());
    }
}
