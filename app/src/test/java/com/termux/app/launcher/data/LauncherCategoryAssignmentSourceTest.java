package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;
import android.content.Context;

import com.termux.app.launcher.drawer.AppDrawerCategory;
import com.termux.app.x11.X11Apps;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Covers the fix for the machine-written {@code x11:linux} line in {@code app-categories.conf}:
 * the file must not be able to answer for the reserved Linux-apps package, but a person's own
 * in-app choice for it (stored separately, in {@link LauncherCategoryOverrideStore}) still wins.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class LauncherCategoryAssignmentSourceTest {

    private Context context;
    private File categoryFile;

    @Before public void setUp() {
        context = RuntimeEnvironment.getApplication();
        categoryFile = new File(context.getCacheDir(), "app-categories-test.conf");
        categoryFile.delete();
    }

    @Test public void aFileEntryForTheLinuxAppsPackageIsIgnored() throws IOException {
        writeFile("[other]\n" + X11Apps.PACKAGE + "\n");
        LauncherCategoryAssignmentSource source = new LauncherCategoryAssignmentSource(
            new LauncherCategoryOverrideStore(context), categoryFile);

        assertNull(source.categoryForPackage(X11Apps.PACKAGE));
    }

    @Test public void anOverrideStoreChoiceForTheLinuxAppsPackageStillWins() throws IOException {
        writeFile("[other]\n" + X11Apps.PACKAGE + "\n");
        LauncherCategoryOverrideStore overrides = new LauncherCategoryOverrideStore(context);
        overrides.set(X11Apps.PACKAGE, AppDrawerCategory.PRODUCTIVITY.slug);
        LauncherCategoryAssignmentSource source =
            new LauncherCategoryAssignmentSource(overrides, categoryFile);

        assertEquals(AppDrawerCategory.PRODUCTIVITY, source.categoryForPackage(X11Apps.PACKAGE));
    }

    @Test public void anOrdinaryPackageInTheFileIsUnaffected() throws IOException {
        writeFile("[productivity]\ncom.example.notes\n");
        LauncherCategoryAssignmentSource source = new LauncherCategoryAssignmentSource(
            new LauncherCategoryOverrideStore(context), categoryFile);

        assertEquals(AppDrawerCategory.PRODUCTIVITY,
            source.categoryForPackage("com.example.notes"));
    }

    private void writeFile(String contents) throws IOException {
        LinkedHashMap<String, List<String>> sections = new LinkedHashMap<>();
        // Written by hand via LauncherCategoryFile.parse + write so this test exercises the same
        // on-disk format the drop-in uses, not a hand-rolled string.
        LauncherCategoryFile parsed = LauncherCategoryFile.parse(new java.io.StringReader(contents));
        for (java.util.Map.Entry<String, List<String>> section : parsed.sections().entrySet())
            sections.put(section.getKey(), section.getValue());
        LauncherCategoryFile.of(sections).write(categoryFile);
    }
}
