package com.termux.app.chrome.appearance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.launcher.data.IconPackChoices;
import com.termux.app.launcher.model.AppRef;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Icon pack page: the tiles run Default then the installed packs in listing order, the stored
 * pack's tile is ringed, a tap previews and applies at once, the switch writes the choice to the
 * pinned key (on) or to the global key with the pinned override cleared (off), the Default tile
 * reads by scope, the preview draws the previewed pack's icons, and the page fits at 360dp and
 * 411dp with nothing cut off. A fake {@link IconPackPage.Backend} stands in for the launcher, and
 * both executors run inline. Native graphics, so text has real metrics.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, qualifiers = "w360dp-h780dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class IconPackPageTest {

    /** The launcher in memory: two keys, a pack list, two pinned apps. */
    static final class FakeBackend implements IconPackPage.Backend {
        final Map<String, String> prefs = new HashMap<>();
        final List<String> writes = new ArrayList<>();
        /** The pack each icon was asked for, in order. */
        final List<String> iconPacks = new ArrayList<>();
        List<IconPackChoices.Entry> packs = Arrays.asList(
            new IconPackChoices.Entry("Alpha", "pack.alpha"),
            new IconPackChoices.Entry("Beta", "pack.beta"),
            new IconPackChoices.Entry("A Pack With A Rather Long Name", "pack.long"));
        List<AppRef> apps = Arrays.asList(new AppRef("a.one", "a.one.Main"), new AppRef("a.two", "a.two.Main"));

        @NonNull @Override public List<IconPackChoices.Entry> packs() { return packs; }
        @NonNull @Override public List<AppRef> pinnedApps() { return apps; }

        @Nullable @Override public Drawable icon(@NonNull String pack, @NonNull AppRef ref) {
            iconPacks.add(pack);
            return new ColorDrawable(0xFF336699);
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
            prefs.put(key, value);
        }
    }

    private Activity mActivity;
    private ContextThemeWrapper mThemed;
    private FakeBackend mBackend;
    private int mApplied;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(Activity.class).setup().get();
        mThemed = new ContextThemeWrapper(mActivity, R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        mBackend = new FakeBackend();
        mApplied = 0;
    }

    @NonNull
    private IconPackPage open() {
        IconPackPage page = new IconPackPage(mThemed, new IconPackPage.Host() {
            @Nullable @Override public Bitmap homeStill() {
                return Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
            }

            @Override public void onApplied() {
                mApplied++;
            }
        }, mBackend, Runnable::run, Runnable::run);
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
        assertEquals(Arrays.asList("Same as app icons", "Alpha", "Beta", "A Pack With A Rather Long Name"),
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
        assertEquals("Same as app icons", label(page.tilesView().getChildAt(0)));
    }

    @Test
    public void thePreviewDrawsEachPinnedAppFromThePreviewedPack() {
        IconPackPage page = open();
        assertEquals(2, ((ViewGroup) page.previewAppsView().getChildAt(0)).getChildCount());
        mBackend.iconPacks.clear();
        page.tilesView().getChildAt(1).performClick();
        assertEquals(Arrays.asList("pack.alpha", "pack.alpha"), mBackend.iconPacks);
    }

    private static int selectedCount(@NonNull IconPackPage page) {
        int n = 0;
        for (int i = 0; i < page.tilesView().getChildCount(); i++) {
            if (page.tilesView().getChildAt(i).isSelected()) n++;
        }
        return n;
    }

    @Test
    public void fitsAtTheConfiguredWidthWithNothingCutOff() {
        assertFits(open());
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-mdpi")
    public void fitsAt411dp() {
        assertFits(open());
    }

    /** Every shown view lies inside the page sideways and no text is cut; the tile row may scroll. */
    private void assertFits(@NonNull IconPackPage page) {
        View root = page.root();
        root.measure(View.MeasureSpec.makeMeasureSpec(mThemed.getResources().getDisplayMetrics().widthPixels,
            View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
        assertInside(root, root);
        assertTrue("tile row is scrollable content", page.tilesView().getChildCount() > 1);
    }

    private static void assertInside(@NonNull View root, @NonNull View v) {
        if (v.getVisibility() != View.VISIBLE) return;
        Rect r = new Rect(0, 0, v.getWidth(), v.getHeight());
        if (v != root) ((ViewGroup) root).offsetDescendantRectToMyCoords(v, r);
        assertTrue(v.getClass().getSimpleName() + " inside the page: " + r,
            r.left >= 0 && r.right <= root.getWidth());
        if (v instanceof TextView) {
            android.text.Layout layout = ((TextView) v).getLayout();
            if (layout != null && layout.getLineCount() > 0) {
                int last = layout.getLineCount() - 1;
                // Tile labels may wrap to two lines; the switch's own text must be whole.
                if (v.getId() == R.id.icon_pack_pinned_only) assertEquals(0, layout.getEllipsisCount(last));
            }
        }
        if (v instanceof ViewGroup && !(v instanceof HorizontalScrollView)) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) assertInside(root, g.getChildAt(i));
        }
    }
}
