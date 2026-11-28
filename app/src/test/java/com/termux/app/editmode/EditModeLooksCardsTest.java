package com.termux.app.editmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.editmode.EditModeController.Card;
import com.termux.app.look.MemoryLookStore;
import com.termux.app.look.StoredTerlLookStore;
import com.termux.app.look.TerlLookStore;
import com.termux.app.look.TerlLooks;
import com.termux.app.look.TerlProperty;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The developer's decisions of 2026-11-28 on the All glass and Home cards, through the real
 * controller on a 360 dp wide screen: the filmstrip holds only the shipped Looks and Custom, each
 * drawn as its glass, in one row that never scrolls; Tint offers Auto and the wallpaper's colours;
 * Depth, Shine, the icon weight and All settings stand on the card; the Home card has Grid columns
 * 3 / 4 / 5, Labels, Icon size and Monochrome icons.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w360dp-h740dp")
public class EditModeLooksCardsTest {

    private interface TestHost extends EditModeController.WindowsHost, EditModeController.LooksHost { }

    private EditModeController controller;
    private FrameLayout layer;
    private TerlLookStore looks;
    private final Bitmap frame = Bitmap.createBitmap(36, 72, Bitmap.Config.ARGB_8888);
    private boolean settingsOpened;
    private final Map<String, Object> keyed = new HashMap<>();
    private int columns = 4;

    private final EditSession.Home home = new EditSession.Home() {
        @Override public boolean available() { return true; }
        @Override public int columns() { return columns; }
        @Override public void setColumns(int count) { columns = count; }
        @NonNull @Override public String labels() { return LABEL_DEFAULT; }
        @Override public void setLabels(@NonNull String size) { }
        @Override public boolean pageDots() { return false; }
        @Override public void setPageDots(boolean on) { }
        @NonNull @Override public String azEdge() { return AZ_OFF; }
        @Override public void setAzEdge(@NonNull String edge) { }
        @Override public void onEmptyTap(@Nullable Runnable tap) { }
        @Override public void change() { }
    };

    private String theme = "system";

    private final EditSession.Wallpaper wallpaper = new EditSession.Wallpaper() {
        @Override public void changePicture() { }
        @NonNull @Override public String theme() { return theme; }
        @Override public void setTheme(@NonNull String to) { theme = to; }
        @Override public int dim() { return 0; }
        @Override public void setDim(int percent) { }
        @Override public boolean drift() { return false; }
        @Override public void setDrift(boolean on) { }
        @Override public boolean coloursFromWallpaper() { return true; }
        @Override public void setColoursFromWallpaper(boolean on) { }
    };

    private final EditSession.Keyed windows = new EditSession.Keyed() {
        @Nullable @Override public Object get(@NonNull String key) { return keyed.get(key); }
        @Override public void set(@NonNull String key, @NonNull Object value) { keyed.put(key, value); }
    };

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight);
        layer = new FrameLayout(activity);
        activity.setContentView(layer);
        looks = new StoredTerlLookStore(new MemoryLookStore());
        keyed.put(EditSession.Keyed.GAP, 8);
        keyed.put(EditSession.Keyed.UNFOCUSED_DIM, 0);
        keyed.put(EditSession.Keyed.RIM_COLOUR, "accent");
        keyed.put(EditSession.Keyed.CONTRAST, "default");
        keyed.put(EditSession.Keyed.EFFECT, "none");
        keyed.put(EditSession.Keyed.FONT, "");
        keyed.put(EditSession.Keyed.CURSOR_TRAIL, "off:comet");
        keyed.put(EditSession.Keyed.DEPTH, 40);
        keyed.put(EditSession.Keyed.SHINE, 50);
        keyed.put(EditSession.Keyed.ICON_SIZE, "m");
        keyed.put(EditSession.Keyed.MONOCHROME, EditSession.OFF);
        keyed.put(EditSession.Keyed.LABELS_SHOWN, EditSession.ON);
        controller = new EditModeController(new TestHost() {
            @NonNull @Override public Context context() { return activity; }
            @Nullable @Override public ViewGroup editorLayer() { return layer; }
            @NonNull @Override public TerlLookStore looks() { return looks; }
            @Nullable @Override public View keyboardView() { return null; }
            @Override public boolean isKeyboardShown() { return false; }
            @Override public boolean showKeyboard() { return false; }
            @Override public void hideKeyboard() { }
            @Nullable @Override public EditSession.Launcher launcher() { return null; }
            @NonNull @Override public List<com.termux.app.launcher.data.IconPackChoices.Entry> iconPacks() {
                return Collections.emptyList();
            }
            @Override public void warmIconPack(@NonNull String pack) { }
            @Nullable @Override public EditSession.Wallpaper wallpaper() { return wallpaper; }
            @Nullable @Override public EditSession.Home home() { return home; }
            @Override public void onWallpaperTap(@Nullable Runnable tap) { }
            @Nullable @Override public LegibilityMeter.Reading legibility() { return null; }
            @Override public void onLeft() { }
            @Nullable @Override public EditSession.Bar bar() { return null; }
            @Nullable @Override public View barView() { return null; }
            @Override public boolean raiseBar() { return false; }
            @Override public void lowerBar() { }
            @Override public void onBarTap(@Nullable Runnable tap) { }
            @Nullable @Override public EditSession.Keyed windows() { return windows; }
            @Override public void onWindowTap(@Nullable Runnable tap) { }
            @NonNull @Override public List<String[]> installedFonts() { return Collections.emptyList(); }
            @Override public void openFonts() { }
            @Nullable @Override public Bitmap wallpaperFrame(int blurDp) { return frame; }
            @Override public void openSettings() { settingsOpened = true; }
        });
    }

    private void idle() {
        int width = Math.round(360 * layer.getResources().getDisplayMetrics().density);
        int height = Math.round(740 * layer.getResources().getDisplayMetrics().density);
        layer.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        layer.layout(0, 0, width, height);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @NonNull
    private static List<LookTileView> tiles(@NonNull View view) {
        List<LookTileView> out = new ArrayList<>();
        collect(view, out);
        return out;
    }

    private static void collect(@NonNull View view, @NonNull List<LookTileView> out) {
        if (view instanceof LookTileView && view.isShown()) out.add((LookTileView) view);
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), out);
    }

    /** The shown view whose content description or text is {@code label}, clickable; null for none. */
    @Nullable
    private static View find(@NonNull View view, @NonNull String label) {
        if (view.isShown() && view.isClickable() && (label.contentEquals(
                view.getContentDescription() == null ? "" : view.getContentDescription())
                || (view instanceof TextView && label.contentEquals(((TextView) view).getText())))) {
            return view;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = find(group.getChildAt(i), label);
            if (found != null) return found;
        }
        return null;
    }

    @Nullable
    private static <T extends View> T findByDescription(@NonNull View view, @NonNull Class<T> type,
                                                         @NonNull String description) {
        if (type.isInstance(view) && view.isShown()
                && description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) {
            return type.cast(view);
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            T found = findByDescription(group.getChildAt(i), type, description);
            if (found != null) return found;
        }
        return null;
    }

    private String label(int res) {
        return layer.getContext().getString(res);
    }

    private static int screenLeft(@NonNull View view) {
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        return at[0];
    }

    private static int screenTop(@NonNull View view) {
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        return at[1];
    }

    @Test public void theFilmstripIsTheShippedLooksAndCustomInOneRowThatNeverScrolls() {
        controller.open(Card.GLOBAL);
        idle();
        List<LookTileView> tiles = tiles(layer);
        assertEquals("Clear, Frosted, Tinted, Solid, Liquid and Custom", 6, tiles.size());
        int top = screenTop(tiles.get(0));
        int right = screenLeft(layer) + layer.getWidth();
        int previousRight = Integer.MIN_VALUE;
        for (LookTileView tile : tiles) {
            assertEquals("every tile stands in the one row", top, screenTop(tile));
            assertTrue("a tile is wide enough to read as glass", tile.getWidth() > 0);
            assertTrue("each tile stands after the one before", screenLeft(tile) >= previousRight);
            previousRight = screenLeft(tile) + tile.getWidth();
            assertTrue("the last tile ends on the screen at 360 dp", previousRight <= right);
            for (View up = tile; up != null && up.getParent() instanceof View; up = (View) up.getParent()) {
                assertFalse("no tile scrolls sideways",
                    up instanceof HorizontalScrollView || up.getClass().getName().contains("RecyclerView"));
            }
        }
        for (String name : new String[] {"Clear", "Frosted", "Tinted", "Solid", "Liquid", "Custom"}) {
            assertNotNull(name + " is on the filmstrip", find(layer, name));
        }
    }

    @Test public void eachTileIsDrawnAsItsGlassOverTheWallpaper() {
        controller.open(Card.GLOBAL);
        idle();
        List<LookTileView> tiles = tiles(layer);
        List<String> ids = looks.lookIds();
        for (int i = 0; i < tiles.size(); i++) {
            LookTileView tile = tiles.get(i);
            assertSame("the tile draws the wallpaper as the glass samples it", frame, tile.frame());
            assertEquals("its pane is the Look's opacity", looks.lookValue(ids.get(i), TerlProperty.OPACITY),
                tile.opacityPercent());
        }
        assertTrue("Solid's pane hides more of the wallpaper than Clear's",
            LookTileView.paneAlpha(tiles.get(ids.indexOf(TerlLooks.SOLID)).opacityPercent())
                > LookTileView.paneAlpha(tiles.get(ids.indexOf(TerlLooks.CLEAR)).opacityPercent()));
        assertTrue("the worn Look is ringed", tiles.get(ids.indexOf(looks.look())).worn());
        // Drawing never throws, the frame held or let go.
        LookTileView first = tiles.get(0);
        android.graphics.Canvas canvas = new android.graphics.Canvas(Bitmap.createBitmap(
            Math.max(1, first.getWidth()), Math.max(1, first.getHeight()), Bitmap.Config.ARGB_8888));
        first.draw(canvas);
        frame.recycle();
        first.draw(canvas);
    }

    @Test public void aTapOnATileWearsItsLook() {
        controller.open(Card.GLOBAL);
        idle();
        View solid = find(layer, "Solid");
        assertNotNull(solid);
        solid.performClick();
        idle();
        assertEquals(TerlLooks.SOLID, looks.look());
        List<String> ids = looks.lookIds();
        assertTrue(tiles(layer).get(ids.indexOf(TerlLooks.SOLID)).worn());
        assertFalse(tiles(layer).get(ids.indexOf(TerlLooks.CLEAR)).worn());
    }

    @Test public void tintOffersAutoAndTheWallpapersColours() {
        controller.open(Card.GLOBAL);
        idle();
        View auto = find(layer, label(R.string.edit_mode_tint_auto));
        assertNotNull("Auto leads the Tint row", auto);
        assertTrue("Auto is the Tint worn at first", auto.isSelected());
        View second = find(layer, label(R.string.edit_mode_tint_secondary));
        assertNotNull("a dot for each of the wallpaper's colours", second);
        assertNotNull(find(layer, label(R.string.edit_mode_tint_primary)));
        assertNotNull(find(layer, label(R.string.edit_mode_tint_tertiary)));
        second.performClick();
        idle();
        assertEquals(TerlLookStore.TINT_SECONDARY, looks.tint());
        assertTrue(second.isSelected());
        assertFalse(auto.isSelected());
    }

    @Test public void depthShineAndTheIconWeightStandOnTheCardAndWrite() {
        controller.open(Card.GLOBAL);
        idle();
        SeekBar depth = findByDescription(layer, SeekBar.class, label(R.string.edit_mode_depth));
        SeekBar shine = findByDescription(layer, SeekBar.class, label(R.string.edit_mode_shine));
        assertNotNull("Depth is on the All glass card", depth);
        assertNotNull("Shine is on the All glass card", shine);
        assertEquals(40, depth.getProgress());
        assertEquals(50, shine.getProgress());
        SeekBar.OnSeekBarChangeListener listener = Shadows.shadowOf(depth).getOnSeekBarChangeListener();
        listener.onStartTrackingTouch(depth);
        listener.onProgressChanged(depth, 70, true);
        listener.onStopTrackingTouch(depth);
        assertEquals(70, keyed.get(EditSession.Keyed.DEPTH));

        View bold = find(layer, label(R.string.edit_mode_icon_bold));
        assertNotNull(find(layer, label(R.string.edit_mode_icon_line)));
        assertNotNull(find(layer, label(R.string.edit_mode_icon_thin)));
        assertNotNull(bold);
        bold.performClick();
        idle();
        assertEquals(TerlLookStore.ICON_BOLD, looks.iconWeight());
    }

    @Test public void allSettingsOpensSettings() {
        controller.open(Card.GLOBAL);
        idle();
        View all = find(layer, label(R.string.edit_mode_all_settings));
        assertNotNull("the card ends on All settings", all);
        all.performClick();
        assertTrue(settingsOpened);
    }

    @Test public void theHomeCardHasGridColumnsLabelsIconSizeAndMonochrome() {
        controller.open(Card.HOME);
        idle();
        for (String count : new String[] {"3", "4", "5"}) assertNotNull(count + " columns", find(layer, count));
        assertNull("no column count beyond the prototype's", find(layer, "6"));
        assertTrue(find(layer, "4").isSelected());
        find(layer, "5").performClick();
        idle();
        assertEquals(5, columns);
        assertTrue(find(layer, "5").isSelected());

        Switch labels = findByDescription(layer, Switch.class, label(R.string.edit_mode_labels));
        assertNotNull("Labels is a switch", labels);
        assertTrue(labels.isChecked());
        labels.performClick();
        assertEquals(EditSession.OFF, keyed.get(EditSession.Keyed.LABELS_SHOWN));

        View xl = find(layer, label(R.string.edit_mode_icon_size_xl));
        assertNotNull("Icon size offers S to XL", xl);
        assertTrue(find(layer, label(R.string.edit_mode_icon_size_m)).isSelected());
        xl.performClick();
        idle();
        assertEquals("xl", keyed.get(EditSession.Keyed.ICON_SIZE));

        Switch monochrome = findByDescription(layer, Switch.class, label(R.string.edit_mode_monochrome));
        assertNotNull("Monochrome icons is a switch", monochrome);
        assertFalse(monochrome.isChecked());
        monochrome.performClick();
        assertEquals(EditSession.ON, keyed.get(EditSession.Keyed.MONOCHROME));
    }

    /** Theme offers System, Light, Dark and Black; Black is stored as night-mode's "black". */
    @Test public void theThemeRowOffersBlack() {
        controller.open(Card.WALLPAPER);
        idle();
        for (int name : new int[] {R.string.appearance_theme_system, R.string.appearance_theme_light,
                R.string.appearance_theme_dark}) {
            assertNotNull(find(layer, label(name)));
        }
        View black = find(layer, label(R.string.edit_mode_theme_black));
        assertNotNull("Black is a Theme", black);
        black.performClick();
        idle();
        assertEquals("black", theme);
        assertTrue(black.isSelected());
    }
}
