package com.termux.app.editmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.look.MemoryLookStore;
import com.termux.app.look.StoredTerlLookStore;
import com.termux.app.colours.ColourSchemeFile;
import com.termux.app.colours.TerminalColours;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The Text tab's cursor and colour scheme, through an edit session as the card drives them. */
public class CursorAndColoursTest {

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private final Map<String, String> mProperties = new HashMap<>();
    private final List<String> mWrites = new ArrayList<>();
    private final Map<String, Object> mOthers = new HashMap<>();
    private boolean mWallpaper = true;
    private int mColoursChanged;
    private File mColorsFile;
    private CursorAndColours mKeyed;
    private EditSession mSession;

    @Before
    public void setUp() {
        mColorsFile = new File(folder.getRoot(), "colors.properties");
        TerminalColours colours = new TerminalColours(new TerminalColours.Source() {
            @Override public boolean fromWallpaper() { return mWallpaper; }
            @Override public void setFromWallpaper(boolean on) { mWallpaper = on; }
        }, new ColourSchemeFile(mColorsFile));
        mKeyed = new CursorAndColours(new CursorAndColours.Host() {
            @Nullable @Override public String property(@NonNull String key) {
                return mProperties.get(key);
            }

            @Override public void writeCursorProperty(@NonNull String key, @NonNull String value) {
                mProperties.put(key, value);
                mWrites.add(key + "=" + value);
            }

            @NonNull @Override public TerminalColours colours() { return colours; }

            @Override public void coloursChanged() { mColoursChanged++; }
        }, new EditSession.Keyed() {
            @Nullable @Override public Object get(@NonNull String key) { return mOthers.get(key); }
            @Override public void set(@NonNull String key, @NonNull Object value) { mOthers.put(key, value); }
        });
        mSession = EditSession.begin(new StoredTerlLookStore(new MemoryLookStore()), null, null,
            null, null, mKeyed);
    }

    @Test
    public void theCursorShapeIsTermuxPropertiesOwnAndUndoes() {
        assertEquals("block until the file names one", "block", mKeyed.get(EditSession.Keyed.CURSOR_STYLE));

        mSession.setKeyedChoice(EditSession.Keyed.CURSOR_STYLE, "bar");
        assertEquals("terminal-cursor-style=bar", mWrites.get(0));
        assertEquals("bar", mKeyed.get(EditSession.Keyed.CURSOR_STYLE));

        mSession.undo();
        assertEquals("block", mProperties.get("terminal-cursor-style"));
    }

    @Test
    public void blinkOnWritesTheUsualRateAndUndoBringsAHandSetRateBack() {
        mProperties.put("terminal-cursor-blink-rate", "500");
        assertEquals("on:500", mKeyed.get(EditSession.Keyed.CURSOR_BLINK));

        mSession.setKeyedChoice(EditSession.Keyed.CURSOR_BLINK, "off");
        assertEquals("0", mProperties.get("terminal-cursor-blink-rate"));
        mSession.undo();
        assertEquals("the rate set by hand, not the usual one", "500",
            mProperties.get("terminal-cursor-blink-rate"));

        mProperties.put("terminal-cursor-blink-rate", "0");
        mSession.setKeyedChoice(EditSession.Keyed.CURSOR_BLINK, "on");
        assertEquals("600", mProperties.get("terminal-cursor-blink-rate"));
    }

    @Test
    public void aBlinkRateOutsideTheTerminalsRangeReadsOff() {
        mProperties.put("terminal-cursor-blink-rate", "20");
        assertEquals("off", mKeyed.get(EditSession.Keyed.CURSOR_BLINK));
        mProperties.put("terminal-cursor-blink-rate", "nonsense");
        assertEquals("off", mKeyed.get(EditSession.Keyed.CURSOR_BLINK));
    }

    @Test
    public void aSchemePickWritesTheFileAndTellsTheScreenOnce() {
        mSession.setKeyedChoice(EditSession.Keyed.SCHEME, "nord");
        assertFalse(mWallpaper);
        assertTrue(mColorsFile.isFile());
        assertEquals("nord", mKeyed.get(EditSession.Keyed.SCHEME));
        assertEquals(1, mColoursChanged);

        mSession.setKeyedChoice(EditSession.Keyed.SCHEME, "nord");
        assertEquals("the same scheme again changes nothing", 1, mColoursChanged);

        mSession.undo();
        assertTrue("undo goes back to Wallpaper colours", mWallpaper);
        assertEquals(TerminalColours.WALLPAPER, mKeyed.get(EditSession.Keyed.SCHEME));
        assertEquals(2, mColoursChanged);
    }

    @Test
    public void everyOtherKeyIsTheCardsOwn() {
        mSession.set(EditSession.Setting.keyedNumber(EditSession.Keyed.RIM), 0);
        assertEquals(0, mOthers.get(EditSession.Keyed.RIM));
        assertTrue("nothing written to termux.properties", mWrites.isEmpty());
    }
}
