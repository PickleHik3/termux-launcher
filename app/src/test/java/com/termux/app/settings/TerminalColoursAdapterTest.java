package com.termux.app.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.colours.ColourSchemeFile;
import com.termux.app.colours.TerminalColours;
import com.termux.shared.termux.settings.registry.InMemorySettingStore;
import com.termux.shared.termux.settings.registry.SettingDeclarations;
import com.termux.shared.termux.settings.registry.SettingsFile;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;

/** The colour scheme and Key spacing through the registry: launcherctl and terl.conf write what the cards write. */
public class TerminalColoursAdapterTest {

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private boolean mWallpaper = true;
    private int mChanged;
    private float mSpacing = 2.96f;
    private File mFile;
    private SettingsRegistry mRegistry;

    @Before
    public void setUp() {
        mFile = new File(folder.getRoot(), "colors.properties");
        TerminalColours colours = new TerminalColours(new TerminalColours.Source() {
            @Override public boolean fromWallpaper() { return mWallpaper; }
            @Override public void setFromWallpaper(boolean on) { mWallpaper = on; }
        }, new ColourSchemeFile(mFile));
        mRegistry = new SettingsRegistry(which -> new InMemorySettingStore(), SettingDeclarations.ALL,
            Arrays.asList(new TerminalColoursAdapter(colours, () -> mChanged++),
                new KeySpacingAdapter(new KeySpacingAdapter.Store() {
                    @Override public float scale() { return mSpacing; }
                    @Override public void setScale(float scale) { mSpacing = scale; }
                })));
    }

    @Test
    public void aSchemeByNameWritesTheFile() {
        assertEquals(TerminalColours.WALLPAPER, mRegistry.get(TerminalColoursAdapter.ID));
        SettingsRegistry.Change change = mRegistry.set(TerminalColoursAdapter.ID, "Solarized_Dark").change();
        assertNotNull(change);
        assertEquals("solarized_dark", mRegistry.get(TerminalColoursAdapter.ID));
        assertFalse(mWallpaper);
        assertTrue(mFile.isFile());
        assertEquals(1, mChanged);
    }

    @Test
    public void aNameThatIsNoSchemeIsRefused() {
        assertNotNull(mRegistry.set(TerminalColoursAdapter.ID, "monokai").refusal());
        assertTrue(mWallpaper);
        assertFalse(mFile.exists());
        assertEquals(0, mChanged);
    }

    @Test
    public void aTerlConfLineChoosesTheScheme() {
        SettingsFile.Applied applied = SettingsFile.apply("terminal.colours = nord\n", mRegistry);
        assertTrue(applied.errors.toString(), applied.ok);
        assertEquals("nord", mRegistry.get(TerminalColoursAdapter.ID));
    }

    @Test
    public void keySpacingIsTenthsOfTheMarginScale() {
        assertEquals("30", mRegistry.get(KeySpacingAdapter.ID));
        assertNull(mRegistry.set(KeySpacingAdapter.ID, "45").refusal());
        assertEquals(4.5f, mSpacing, 0.001f);
        assertNotNull(mRegistry.set(KeySpacingAdapter.ID, "81").refusal());
        assertEquals(4.5f, mSpacing, 0.001f);
    }
}
