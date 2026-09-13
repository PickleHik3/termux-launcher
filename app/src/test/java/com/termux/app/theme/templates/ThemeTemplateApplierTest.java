package com.termux.app.theme.templates;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** The pass: what gets written, which hooks run, and what a disabled template leaves behind. */
public class ThemeTemplateApplierTest {

    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();

    /** Every hook the pass asked for, as "id apply.sh". */
    private static final class RecordingHooks implements ThemeTemplateApplier.HookRunner {

        final List<String> calls = new ArrayList<>();

        Runnable beforeEachCall;

        @Override
        public boolean run(ThemeTemplate template, File directory, String hook, String mode) {
            if (beforeEachCall != null) beforeEachCall.run();
            calls.add(template.id + " " + hook + " " + mode);
            return true;
        }
    }

    private File mBuiltInRoot;
    private File mUserRoot;
    private File mHome;
    private File mAppliedFile;
    private RecordingHooks mHooks;

    @Before
    public void setUp() throws IOException {
        mBuiltInRoot = mFolder.newFolder("builtin");
        mUserRoot = mFolder.newFolder("user");
        mHome = mFolder.newFolder("home");
        mAppliedFile = new File(mFolder.newFolder("state"), ".applied");
        mHooks = new RecordingHooks();
    }

    private ThemeTemplateApplier applier() {
        ThemeTemplateLoader loader = new ThemeTemplateLoader(
            new DirectoryThemeTemplateSource(mBuiltInRoot, true),
            new DirectoryThemeTemplateSource(mUserRoot),
            new ThemeTemplatePaths(mHome.getAbsolutePath(), Collections.emptyMap()),
            ThemeTemplateLog.NONE);
        return new ThemeTemplateApplier(loader, mHooks, mAppliedFile, ThemeTemplateLog.NONE);
    }

    private File output(String name) {
        return new File(mHome, ".config/" + name);
    }

    @Test
    public void theFirstPassWritesTheOutputAndRunsThePostHook() throws IOException {
        ThemeTemplateFixtures.template(mBuiltInRoot, "starship", "~/.config/starship.toml");
        applier().apply(ThemeTemplateFixtures.palette(), Collections.singleton("starship"));
        assertEquals("primary = \"#4080c0\"\n", ThemeTemplateFixtures.read(output("starship.toml")));
        assertEquals(Collections.singletonList("starship apply.sh dark"), mHooks.calls);
        assertEquals(output("starship.toml").getAbsolutePath(),
            applier().readApplied().get("starship"));
    }

    @Test
    public void anUnchangedPaletteWritesNothingAndRunsNothing() throws IOException {
        ThemeTemplateFixtures.template(mBuiltInRoot, "starship", "~/.config/starship.toml");
        ThemeTemplateApplier applier = applier();
        Properties palette = ThemeTemplateFixtures.palette();
        applier.apply(palette, Collections.singleton("starship"));
        long writtenAt = output("starship.toml").lastModified();
        mHooks.calls.clear();
        applier.apply(palette, Collections.singleton("starship"));
        assertTrue(mHooks.calls.isEmpty());
        assertEquals(writtenAt, output("starship.toml").lastModified());
    }

    @Test
    public void aChangedPaletteRewritesAndRunsThePostHookAgain() throws IOException {
        ThemeTemplateFixtures.template(mBuiltInRoot, "starship", "~/.config/starship.toml");
        ThemeTemplateApplier applier = applier();
        applier.apply(ThemeTemplateFixtures.palette(), Collections.singleton("starship"));
        mHooks.calls.clear();
        Properties moved = ThemeTemplateFixtures.palette();
        moved.setProperty("primary", "#FF0000");
        moved.setProperty("mode", "light");
        applier.apply(moved, Collections.singleton("starship"));
        assertEquals("primary = \"#ff0000\"\n", ThemeTemplateFixtures.read(output("starship.toml")));
        assertEquals(Collections.singletonList("starship apply.sh light"), mHooks.calls);
    }

    @Test
    public void aTemplateNoLongerEnabledRunsItsUndoHook() throws IOException {
        ThemeTemplateFixtures.template(mBuiltInRoot, "starship", "~/.config/starship.toml");
        ThemeTemplateApplier applier = applier();
        applier.apply(ThemeTemplateFixtures.palette(), Collections.singleton("starship"));
        mHooks.calls.clear();
        applier.apply(ThemeTemplateFixtures.palette(), Collections.emptySet());
        assertEquals(Collections.singletonList("starship undo.sh dark"), mHooks.calls);
        assertTrue(applier.readApplied().isEmpty());
        // The undo hook owns the rendered file; the pass does not second-guess it.
        assertTrue(output("starship.toml").isFile());
    }

    @Test
    public void aTemplateThatIsGoneHasItsOutputDeleted() throws IOException {
        File directory = ThemeTemplateFixtures.template(mUserRoot, "mine", "~/.config/mine.conf");
        ThemeTemplateApplier applier = applier();
        applier.apply(ThemeTemplateFixtures.palette(), Collections.emptySet());
        assertTrue(output("mine.conf").isFile());
        mHooks.calls.clear();
        deleteTree(directory);
        applier.apply(ThemeTemplateFixtures.palette(), Collections.emptySet());
        assertTrue(mHooks.calls.isEmpty());
        assertFalse(output("mine.conf").isFile());
        assertTrue(applier.readApplied().isEmpty());
    }

    @Test
    public void aSupersededPassStopsBetweenTemplates() throws IOException {
        ThemeTemplateFixtures.template(mUserRoot, "a-first", "~/.config/first.conf");
        ThemeTemplateFixtures.template(mUserRoot, "b-second", "~/.config/second.conf");
        ThemeTemplateApplier applier = applier();
        // The palette changes under the first template's hook, the way a wallpaper refresh landing
        // while a pass is running does.
        mHooks.beforeEachCall = applier::schedule;
        applier.apply(ThemeTemplateFixtures.palette(), Collections.emptySet(), applier.schedule());
        assertEquals(Collections.singletonList("a-first apply.sh dark"), mHooks.calls);
        assertTrue(output("first.conf").isFile());
        assertFalse(output("second.conf").isFile());
        Map<String, String> applied = applier.readApplied();
        assertEquals(Collections.singletonList("a-first"), new ArrayList<>(applied.keySet()));
    }

    @Test
    public void aPassThatChangesNothingStillReachesTheUndo() throws IOException {
        ThemeTemplateFixtures.template(mUserRoot, "a-first", "~/.config/first.conf");
        File second = ThemeTemplateFixtures.template(mUserRoot, "b-second", "~/.config/second.conf");
        ThemeTemplateApplier applier = applier();
        applier.apply(ThemeTemplateFixtures.palette(), Collections.emptySet());
        assertEquals(Arrays.asList("a-first", "b-second"),
            new ArrayList<>(applier.readApplied().keySet()));
        deleteTree(second);
        mHooks.calls.clear();
        applier.apply(ThemeTemplateFixtures.palette(), Collections.emptySet());
        assertTrue(mHooks.calls.isEmpty());
        assertFalse(output("second.conf").isFile());
        assertEquals(Collections.singletonList("a-first"),
            new ArrayList<>(applier.readApplied().keySet()));
    }

    @Test
    public void aTemplateThatCannotBeRenderedIsSkippedAndKeptForNextTime() throws IOException {
        ThemeTemplateFixtures.template(mUserRoot, "broken", "~/.config/broken.conf",
            "x = {{ colors.no_such_role.dark.hex }}\n");
        ThemeTemplateFixtures.template(mUserRoot, "sound", "~/.config/sound.conf");
        ThemeTemplateApplier applier = applier();
        applier.apply(ThemeTemplateFixtures.palette(), Collections.emptySet());
        assertFalse(output("broken.conf").isFile());
        assertTrue(output("sound.conf").isFile());
        assertEquals(Collections.singletonList("sound apply.sh dark"), mHooks.calls);
        assertEquals(Collections.singletonList("sound"), new ArrayList<>(applier.readApplied().keySet()));
    }

    @Test
    public void aUserTemplateShadowingABuiltInIsNotUndoneWhenTheBuiltInIsSwitchedOff()
        throws IOException {
        ThemeTemplateFixtures.template(mBuiltInRoot, "starship", "~/.config/shipped.toml");
        ThemeTemplateFixtures.template(mUserRoot, "starship", "~/.config/mine.toml");
        ThemeTemplateApplier applier = applier();
        applier.apply(ThemeTemplateFixtures.palette(), Collections.singleton("starship"));
        mHooks.calls.clear();
        applier.apply(ThemeTemplateFixtures.palette(), Collections.emptySet());
        assertTrue(mHooks.calls.isEmpty());
        assertTrue(output("mine.toml").isFile());
        assertFalse(output("shipped.toml").isFile());
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
