package com.termux.app.chrome.appearance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.launcher.data.IconPackChoices;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The Icon pack controls: the tiles run Default then the installed packs in listing order, the
 * stored pack's tile is ringed, a tap parses the pack off the main thread and then applies it, the
 * switch writes the choice to the pinned key (on) or to the global key with the pinned override
 * cleared (off), the Default tile reads by scope, and rapid taps collapse to the last. A fake
 * {@link IconPackPage.Backend} stands in for the launcher, and both executors run inline unless a
 * test queues the background one. Native graphics, so text has real metrics. (The sheet's fit at
 * 360dp and 411dp is the panel's: AppearanceEditorPanelFitBase.)
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, qualifiers = "w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class IconPackPageTest {

    /** The launcher in memory: two keys and a pack list. */
    static final class FakeBackend implements IconPackPage.Backend {
        final Map<String, String> prefs = new HashMap<>();
        final List<String> writes = new ArrayList<>();
        /** Every event in order: "warm x" and "write k=v". */
        final List<String> events = new ArrayList<>();
        final List<String> warmed = new ArrayList<>();
        List<IconPackChoices.Entry> packs = Arrays.asList(
            new IconPackChoices.Entry("Alpha", "pack.alpha"),
            new IconPackChoices.Entry("Beta", "pack.beta"),
            new IconPackChoices.Entry("A Pack With A Rather Long Name", "pack.long"));

        @NonNull @Override public List<IconPackChoices.Entry> packs() { return packs; }

        @Override public void warm(@NonNull String pack) {
            warmed.add(pack);
            events.add("warm " + pack);
        }

        @Nullable @Override public Drawable packArt(@NonNull String pack) {
            return new ColorDrawable(0xFF993366);
        }

        @NonNull @Override public String current(@NonNull String key) {
            String v = prefs.get(key);
            return v == null ? "" : v;
        }

        @Override public void apply(@NonNull String key, @NonNull String value) {
            writes.add(key + "=" + value);
            events.add("write " + key + "=" + value);
            prefs.put(key, value);
        }
    }

    private Activity mActivity;
    private ContextThemeWrapper mThemed;
    private FakeBackend mBackend;
    private int mApplied;
    private int mContentChanged;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mThemed = new ContextThemeWrapper(mActivity, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        mBackend = new FakeBackend();
        mApplied = 0;
        mContentChanged = 0;
    }

    /** The background executor, when a test wants to hold it: tasks wait until {@link #runBackground}. */
    private final ArrayDeque<Runnable> mQueued = new ArrayDeque<>();
    private boolean mHoldBackground;

    private void runBackground() {
        while (!mQueued.isEmpty()) mQueued.poll().run();
    }

    @NonNull
    private IconPackPage open() {
        Executor background = task -> {
            if (mHoldBackground) mQueued.add(task);
            else task.run();
        };
        IconPackPage page = new IconPackPage(mThemed, new IconPackPage.Host() {
            @Override public void onApplied() {
                mApplied++;
            }

            @Override public void onContentChanged() {
                mContentChanged++;
            }
        }, mBackend, background, Runnable::run);
        mActivity.setContentView(page.root());
        shadowOf(Looper.getMainLooper()).idle();
        return page;
    }

    private static String label(@NonNull View tile) {
        return ((TextView) tile.findViewById(R.id.icon_pack_tile_label)).getText().toString();
    }

    private static List<String> labels(@NonNull IconPackPage page) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < page.tilesView().getChildCount(); i++) out.add(label(page.tilesView().getChildAt(i)));
        return out;
    }

    @Test
    public void titleIsIconPack() {
        assertEquals("Icon pack", open().title());
    }

    @Test
    public void tilesRunDefaultThenThePacksInListingOrder() {
        IconPackPage page = open();
        assertEquals(Arrays.asList("System", "Alpha", "Beta", "A Pack With A Rather Long Name"),
            labels(page));
        assertEquals("", page.tilesView().getChildAt(0).getTag());
        assertEquals("pack.beta", page.tilesView().getChildAt(2).getTag());
    }

    @Test
    public void nothingStoredStartsOnWithDefaultRinged() {
        IconPackPage page = open();
        assertTrue(page.pinnedOnlySwitch().isChecked());
        assertTrue(page.tilesView().getChildAt(0).isSelected());
        assertFalse(page.tilesView().getChildAt(1).isSelected());
    }

    @Test
    public void theStoredPinnedPackIsRingedAndTheSwitchIsOn() {
        mBackend.prefs.put(IconPackChoices.KEY_PINNED, "pack.beta");
        mBackend.prefs.put(IconPackChoices.KEY_GLOBAL, "pack.alpha");
        IconPackPage page = open();
        assertTrue(page.pinnedOnlySwitch().isChecked());
        assertTrue(page.tilesView().getChildAt(2).isSelected());
        assertEquals(1, selectedCount(page));
    }

    @Test
    public void aGlobalOnlyChoiceStartsOffWithItsTileRingedAndDefaultReadsDefault() {
        mBackend.prefs.put(IconPackChoices.KEY_GLOBAL, "pack.alpha");
        IconPackPage page = open();
        assertFalse(page.pinnedOnlySwitch().isChecked());
        assertTrue(page.tilesView().getChildAt(1).isSelected());
        assertEquals("Default", label(page.tilesView().getChildAt(0)));
    }

    @Test
    public void tappingATileAppliesToThePinnedKeyWhileOn() {
        IconPackPage page = open();
        page.tilesView().getChildAt(2).performClick();
        assertEquals(Arrays.asList(IconPackChoices.KEY_PINNED + "=pack.beta"), mBackend.writes);
        assertEquals("pack.beta", page.selectedPack());
        assertTrue(page.tilesView().getChildAt(2).isSelected());
        assertEquals(1, selectedCount(page));
        assertEquals(1, mApplied);
    }

    @Test
    public void tappingATileWhileOffWritesGlobalAndClearsPinned() {
        mBackend.prefs.put(IconPackChoices.KEY_GLOBAL, "pack.alpha");
        IconPackPage page = open();
        page.tilesView().getChildAt(2).performClick();
        assertEquals(Arrays.asList(IconPackChoices.KEY_GLOBAL + "=pack.beta", IconPackChoices.KEY_PINNED + "="),
            mBackend.writes);
    }

    @Test
    public void switchingOffMovesTheChoiceToGlobalAndClearsPinned() {
        mBackend.prefs.put(IconPackChoices.KEY_PINNED, "pack.beta");
        IconPackPage page = open();
        page.pinnedOnlySwitch().performClick();
        assertEquals(Arrays.asList(IconPackChoices.KEY_GLOBAL + "=pack.beta", IconPackChoices.KEY_PINNED + "="),
            mBackend.writes);
        assertEquals("pack.beta", mBackend.current(IconPackChoices.KEY_GLOBAL));
        assertEquals("", mBackend.current(IconPackChoices.KEY_PINNED));
        assertEquals("Default", label(page.tilesView().getChildAt(0)));
    }

    @Test
    public void switchingOnWritesTheChoiceToPinned() {
        mBackend.prefs.put(IconPackChoices.KEY_GLOBAL, "pack.alpha");
        IconPackPage page = open();
        page.pinnedOnlySwitch().performClick();
        assertEquals(Arrays.asList(IconPackChoices.KEY_PINNED + "=pack.alpha"), mBackend.writes);
        assertEquals("System", label(page.tilesView().getChildAt(0)));
    }

    @Test
    public void aTapParsesThePackBeforeItWritesTheChoice() {
        IconPackPage page = open();
        page.tilesView().getChildAt(1).performClick();
        assertEquals(Arrays.asList("warm pack.alpha", "write " + IconPackChoices.KEY_PINNED + "=pack.alpha"),
            mBackend.events);
    }

    @Test
    public void theDefaultTileNeedsNoParse() {
        mBackend.prefs.put(IconPackChoices.KEY_PINNED, "pack.beta");
        IconPackPage page = open();
        page.tilesView().getChildAt(0).performClick();
        assertEquals(Arrays.asList("write " + IconPackChoices.KEY_PINNED + "="), mBackend.events);
    }

    @Test
    public void thePackIsRingedAtOnceAndWrittenOnlyOnceItsParseLands() {
        IconPackPage page = open();
        mHoldBackground = true;
        page.tilesView().getChildAt(2).performClick();
        assertTrue("ringed before the parse", page.tilesView().getChildAt(2).isSelected());
        assertTrue("nothing written while it parses", mBackend.writes.isEmpty());
        runBackground();
        assertEquals(Arrays.asList(IconPackChoices.KEY_PINNED + "=pack.beta"), mBackend.writes);
    }

    @Test
    public void rapidTapsCollapseToTheLastOne() {
        IconPackPage page = open();
        mHoldBackground = true;
        page.tilesView().getChildAt(1).performClick();
        page.tilesView().getChildAt(2).performClick();
        page.tilesView().getChildAt(3).performClick();
        runBackground();
        assertEquals("only the last tap parses", Arrays.asList("pack.long"), mBackend.warmed);
        assertEquals("and only the last is written",
            Arrays.asList(IconPackChoices.KEY_PINNED + "=pack.long"), mBackend.writes);
        assertEquals("pack.long", page.selectedPack());
        assertEquals(1, mApplied);
    }

    @Test
    public void aTapStillWaitingWhenThePageIsReleasedIsWrittenNotLost() {
        IconPackPage page = open();
        mHoldBackground = true;
        page.tilesView().getChildAt(1).performClick();
        page.release();
        assertEquals(Arrays.asList(IconPackChoices.KEY_PINNED + "=pack.alpha"), mBackend.writes);
        runBackground();
        assertEquals("the late parse writes nothing more", 1, mBackend.writes.size());
    }

    /**
     * Coming back into view with the same packs and the same choice keeps the very tiles that are
     * there: nothing is torn down and inflated again, so the sheet does not blink.
     */
    @Test
    public void aSecondShowWithNothingChangedKeepsTheSameTiles() {
        IconPackPage page = open();
        List<View> before = tiles(page);
        int changed = mContentChanged;
        page.onHidden();
        page.onShown();
        assertEquals("the same tile views", before, tiles(page));
        assertEquals("the sheet is not told to measure again", changed, mContentChanged);
        assertTrue(page.tilesView().getChildAt(0).isSelected());
    }

    /** A tap moves the ring on the tiles already there. */
    @Test
    public void aTapMovesTheRingInPlace() {
        IconPackPage page = open();
        List<View> before = tiles(page);
        page.tilesView().getChildAt(2).performClick();
        assertEquals("the same tile views", before, tiles(page));
        assertTrue(page.tilesView().getChildAt(2).isSelected());
        assertFalse(page.tilesView().getChildAt(0).isSelected());
        assertEquals(1, selectedCount(page));
    }

    /**
     * A listing that lands with a different set of packs builds the row again and tells the host,
     * so the sheet can take the row's new height; the first listing is such a change.
     */
    @Test
    public void aListingWithNewPacksRebuildsTheRowAndTellsTheHost() {
        mHoldBackground = true;
        IconPackPage page = open();
        assertEquals("only Default before the listing lands", 1, page.tilesView().getChildCount());
        runBackground();
        assertEquals(4, page.tilesView().getChildCount());
        assertEquals("the first listing changed the row", 1, mContentChanged);

        mBackend.packs = Arrays.asList(new IconPackChoices.Entry("Alpha", "pack.alpha"),
            new IconPackChoices.Entry("Gamma", "pack.gamma"));
        page.onHidden();
        page.onShown();
        runBackground();
        assertEquals(Arrays.asList("System", "Alpha", "Gamma"), labels(page));
        assertEquals(2, mContentChanged);
    }

    /**
     * The Appearance surface's memory of the row's scroll comes back once the row is laid out
     * with the packs in it, and is reported back as the row's own scroll.
     */
    @Test
    public void aRememberedScrollComesBackOnceTheRowIsLaidOut() {
        List<IconPackChoices.Entry> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) many.add(new IconPackChoices.Entry("Pack " + i, "pack." + i));
        mBackend.packs = many;
        IconPackPage page = open();
        page.restoreTileScrollX(120);
        View root = page.root();
        root.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        root.layout(0, 0, 360, root.getMeasuredHeight());
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(120, page.tileScrollX());
    }

    private static List<View> tiles(@NonNull IconPackPage page) {
        List<View> out = new ArrayList<>();
        for (int i = 0; i < page.tilesView().getChildCount(); i++) out.add(page.tilesView().getChildAt(i));
        return out;
    }

    private static int selectedCount(@NonNull IconPackPage page) {
        int n = 0;
        for (int i = 0; i < page.tilesView().getChildCount(); i++) {
            if (page.tilesView().getChildAt(i).isSelected()) n++;
        }
        return n;
    }
}
