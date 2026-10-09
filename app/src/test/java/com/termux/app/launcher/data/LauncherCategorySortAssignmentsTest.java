package com.termux.app.launcher.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.StringReader;

/** What a sort run writes to app-categories.conf, and when. */
public class LauncherCategorySortAssignmentsTest {

    @Rule public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void anAnswerOfOtherIsRememberedButNeverWritten() throws Exception {
        File file = new File(folder.getRoot(), "app-categories.conf");
        LauncherCategorySortService.Assignments assignments =
            new LauncherCategorySortService.Assignments(LauncherCategoryFile.empty(), file);
        assignments.accept("com.example.odd", "other");
        assignments.accept("com.example.chat", "social");

        assertTrue(assignments.answeredOther.contains("com.example.odd"));
        assertEquals(1, assignments.assigned);
        assignments.save();
        LauncherCategoryFile written = LauncherCategoryFile.parse(file);
        assertEquals("social", written.categoryForPackage("com.example.chat"));
        assertNull(written.categoryForPackage("com.example.odd"));
    }

    @Test
    public void eachCheckpointSavesWhatTheRunHasSoFarOnTopOfTheFile() throws Exception {
        File file = new File(folder.getRoot(), "app-categories.conf");
        LauncherCategoryFile existing = LauncherCategoryFile.parse(new StringReader("[travel]\ncom.example.maps\n"));
        LauncherCategorySortService.Assignments assignments =
            new LauncherCategorySortService.Assignments(existing, file);

        // Nothing new: nothing written.
        assignments.checkpoint();
        assertFalse(file.exists());

        assignments.accept("com.example.chat", "social");
        assignments.checkpoint();
        LauncherCategoryFile first = LauncherCategoryFile.parse(file);
        assertEquals("travel", first.categoryForPackage("com.example.maps"));
        assertEquals("social", first.categoryForPackage("com.example.chat"));

        assignments.accept("com.example.bank", "finance");
        assignments.checkpoint();
        assertEquals("finance", LauncherCategoryFile.parse(file).categoryForPackage("com.example.bank"));
    }
}
