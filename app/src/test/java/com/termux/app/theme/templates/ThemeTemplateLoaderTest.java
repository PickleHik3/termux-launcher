package com.termux.app.theme.templates;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Manifest reading, output expansion, and which source wins when both have an id. */
public class ThemeTemplateLoaderTest {

    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();

    private File mBuiltInRoot;
    private File mUserRoot;
    private File mHome;

    @Before
    public void setUp() throws IOException {
        mBuiltInRoot = mFolder.newFolder("builtin");
        mUserRoot = mFolder.newFolder("user");
        mHome = mFolder.newFolder("home");
    }

    private ThemeTemplateLoader loader(Map<String, String> environment) {
        return new ThemeTemplateLoader(
            new DirectoryThemeTemplateSource(mBuiltInRoot, true),
            new DirectoryThemeTemplateSource(mUserRoot),
            new ThemeTemplatePaths(mHome.getAbsolutePath(), environment),
            ThemeTemplateLog.NONE);
    }

    private ThemeTemplateLoader loader() {
        return loader(Collections.emptyMap());
    }

    @Test
    public void readsTheManifest() throws IOException {
        ThemeTemplateFixtures.template(mBuiltInRoot, "starship", "~/.config/starship.toml");
        List<ThemeTemplate> builtIns = loader().builtInTemplates();
        assertEquals(1, builtIns.size());
        ThemeTemplate template = builtIns.get(0);
        assertEquals("starship", template.id);
        assertEquals("Starship", template.name);
        assertEquals("Colours for starship", template.summary);
        assertEquals("input.txt", template.input);
        assertEquals(mHome.getAbsolutePath() + "/.config/starship.toml", template.output);
        assertEquals("apply.sh", template.postHook);
        assertEquals("undo.sh", template.undoHook);
        assertTrue(template.isBuiltIn());
        assertTrue(template.hasPostHook());
        assertTrue(template.hasUndoHook());
        // Most tools need nothing from the user's shell startup, so the key is optional.
        assertFalse(template.hasSetupHook());
        assertEquals("", template.setupHook);
    }

    @Test
    public void readsTheOptionalSetupHook() throws IOException {
        File directory = ThemeTemplateFixtures.template(mBuiltInRoot, "ohmyposh", "~/.config/omp.json");
        ThemeTemplateFixtures.write(new File(directory, ThemeTemplate.MANIFEST_NAME),
            "name=Oh My Posh\ninput=input.txt\noutput=~/.config/omp.json\n"
                + "post_hook=apply.sh\nundo_hook=undo.sh\nsetup_hook=setup.sh\n");
        ThemeTemplate template = loader().find("ohmyposh");
        assertNotNull(template);
        assertTrue(template.hasSetupHook());
        assertEquals("setup.sh", template.setupHook);
        String setupDir = new File(mBuiltInRoot, "ohmyposh").getAbsolutePath();
        assertEquals("env TERMUX_THEME_ID='ohmyposh' TERMUX_THEME_DIR='" + setupDir + "'"
                + " TERMUX_THEME_OUTPUT='" + template.output + "' bash '" + setupDir + "/setup.sh'",
            template.setupCommand());
    }

    @Test
    public void aTemplateWithoutAnInputOrOutputIsSkipped() throws IOException {
        File directory = new File(mBuiltInRoot, "broken");
        ThemeTemplateFixtures.write(new File(directory, ThemeTemplate.MANIFEST_NAME), "name=Broken\n");
        assertTrue(loader().builtInTemplates().isEmpty());
        assertNull(loader().find("broken"));
    }

    @Test
    public void expandsXdgDefaultsWhenTheEnvironmentIsEmpty() throws IOException {
        ThemeTemplateFixtures.template(mBuiltInRoot, "btop", "$XDG_CONFIG_HOME/btop/themes/x.theme");
        ThemeTemplateFixtures.template(mBuiltInRoot, "bat", "${XDG_CACHE_HOME}/bat/themes/x.tmTheme");
        Map<String, String> byId = byId(loader().builtInTemplates());
        assertEquals(mHome.getAbsolutePath() + "/.config/btop/themes/x.theme", byId.get("btop"));
        assertEquals(mHome.getAbsolutePath() + "/.cache/bat/themes/x.tmTheme", byId.get("bat"));
    }

    @Test
    public void usesTheEnvironmentWhenTheXdgVariablesAreSet() throws IOException {
        ThemeTemplateFixtures.template(mBuiltInRoot, "btop", "$XDG_CONFIG_HOME/btop/themes/x.theme");
        Map<String, String> environment = new HashMap<>();
        environment.put("XDG_CONFIG_HOME", "/elsewhere/config");
        Map<String, String> byId = byId(loader(environment).builtInTemplates());
        assertEquals("/elsewhere/config/btop/themes/x.theme", byId.get("btop"));
    }

    @Test
    public void expandsHomeAndUnknownVariables() throws IOException {
        ThemeTemplateFixtures.template(mBuiltInRoot, "one", "~");
        ThemeTemplateFixtures.template(mBuiltInRoot, "two", "$HOME/.config/two.conf");
        ThemeTemplateFixtures.template(mBuiltInRoot, "three", "~/$NOT_SET/three.conf");
        Map<String, String> byId = byId(loader().builtInTemplates());
        assertEquals(mHome.getAbsolutePath(), byId.get("one"));
        assertEquals(mHome.getAbsolutePath() + "/.config/two.conf", byId.get("two"));
        assertEquals(mHome.getAbsolutePath() + "//three.conf", byId.get("three"));
    }

    @Test
    public void aShellStyleDefaultInTheOutputPathIsHonoured() throws IOException {
        ThemeTemplateFixtures.template(mBuiltInRoot, "starship",
            "${XDG_CACHE_HOME:-$HOME/.cache}/launcher-material/starship-palette.toml");
        ThemeTemplateFixtures.template(mBuiltInRoot, "tmux", "${XDG_CONFIG_HOME:-$HOME/.config}/tmux/x.conf");
        Map<String, String> environment = new HashMap<>();
        environment.put("XDG_CONFIG_HOME", "/elsewhere/config");
        ThemeTemplateLoader loader = loader(environment);
        assertEquals(mHome.getAbsolutePath() + "/.cache/launcher-material/starship-palette.toml",
            loader.find("starship").output);
        assertEquals("/elsewhere/config/tmux/x.conf", loader.find("tmux").output);
    }

    @Test
    public void builtInsApplyOnlyWhileTheyAreEnabled() throws IOException {
        ThemeTemplateFixtures.template(mBuiltInRoot, "starship", "~/.config/starship.toml");
        ThemeTemplateFixtures.template(mBuiltInRoot, "btop", "~/.config/btop.theme");
        assertTrue(loader().active(Collections.emptySet()).isEmpty());
        List<ThemeTemplate> active = loader().active(Collections.singleton("btop"));
        assertEquals(1, active.size());
        assertEquals("btop", active.get(0).id);
    }

    @Test
    public void userTemplatesApplyWithoutBeingEnabled() throws IOException {
        ThemeTemplateFixtures.template(mUserRoot, "mine", "~/.config/mine.conf");
        List<ThemeTemplate> active = loader().active(Collections.emptySet());
        assertEquals(1, active.size());
        assertEquals("mine", active.get(0).id);
        assertFalse(active.get(0).isBuiltIn());
    }

    @Test
    public void aUserTemplateShadowsTheBuiltInWithTheSameId() throws IOException {
        ThemeTemplateFixtures.template(mBuiltInRoot, "starship", "~/.config/shipped.toml");
        ThemeTemplateFixtures.template(mUserRoot, "starship", "~/.config/mine.toml");
        List<ThemeTemplate> active = loader().active(Collections.singleton("starship"));
        assertEquals(1, active.size());
        assertEquals(mHome.getAbsolutePath() + "/.config/mine.toml", active.get(0).output);
        assertFalse(active.get(0).isBuiltIn());
        ThemeTemplate found = loader().find("starship");
        assertNotNull(found);
        assertFalse(found.isBuiltIn());
        // The shipped one is still listed in settings under the name the user knows it by.
        assertEquals(1, loader().builtInTemplates().size());
    }

    @Test
    public void anEmptySourceIsFine() {
        assertTrue(loader().builtInTemplates().isEmpty());
        assertTrue(loader().active(Collections.singleton("starship")).isEmpty());
        assertNull(loader().find("starship"));
    }

    @Test
    public void readsTheInputThroughTheSource() throws IOException {
        ThemeTemplateFixtures.template(mUserRoot, "mine", "~/.config/mine.conf", "hello\n");
        assertEquals("hello\n", loader().find("mine").readInput());
        assertEquals(new File(mUserRoot, "mine"), loader().find("mine").directory());
    }

    private static Map<String, String> byId(List<ThemeTemplate> templates) {
        Map<String, String> outputs = new HashMap<>();
        for (ThemeTemplate template : templates) outputs.put(template.id, template.output);
        return outputs;
    }
}
