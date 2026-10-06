package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class FilesWidgetPathsTest {
    private static final String HOME = "/data/data/com.termux/files/home";

    @Test public void expandsTheHomeFolder() {
        assertEquals(HOME + "/notes/tasks.md", FilesWidgetPaths.expand("~/notes/tasks.md", HOME));
        assertEquals(HOME, FilesWidgetPaths.expand("~", HOME));
        assertEquals(HOME + "/notes/x.md", FilesWidgetPaths.expand("  notes/x.md ", HOME));
        assertEquals("/sdcard/x.md", FilesWidgetPaths.expand("/sdcard/x.md", HOME));
        assertEquals(HOME + "/a.md", FilesWidgetPaths.expand("~/a.md", HOME + "/"));
    }

    @Test public void displaysTheHomeFolderAsTilde() {
        assertEquals("~/notes/scratch.md", FilesWidgetPaths.display(HOME + "/notes/scratch.md", HOME));
        assertEquals("~", FilesWidgetPaths.display(HOME, HOME));
        assertEquals("/sdcard/x.md", FilesWidgetPaths.display("/sdcard/x.md", HOME));
        assertEquals(HOME + "x/a", FilesWidgetPaths.display(HOME + "x/a", HOME));
    }

    @Test public void namesTheFile() {
        assertEquals("scratch.md", FilesWidgetPaths.fileName(HOME + "/notes/scratch.md"));
        assertEquals("tasks", FilesWidgetPaths.baseName(HOME + "/notes/tasks.md"));
        assertEquals(".profile", FilesWidgetPaths.baseName(HOME + "/.profile"));
        assertEquals("notes", FilesWidgetPaths.fileName("notes"));
    }

    @Test public void quotesForTheShell() {
        assertEquals("'/a b/c.md'", FilesWidgetPaths.shellQuote("/a b/c.md"));
        assertEquals("'it'\\''s.md'", FilesWidgetPaths.shellQuote("it's.md"));
        assertEquals("'$HOME `x`'", FilesWidgetPaths.shellQuote("$HOME `x`"));
    }

    @Test public void editorCommandRunsTheEditorOnTheQuotedPath() {
        assertEquals(Arrays.asList("bash", "-lc", "\"${EDITOR:-nano}\" '/h/my notes.md'"),
            FilesWidgetPaths.editorCommand("/h/my notes.md"));
    }

    @Test public void splitsCommaLists() {
        assertEquals(Arrays.asList("ideas.md", "~/x.md"), FilesWidgetPaths.splitList(" ideas.md, ,~/x.md,"));
        assertEquals(Collections.emptyList(), FilesWidgetPaths.splitList(""));
    }
}
