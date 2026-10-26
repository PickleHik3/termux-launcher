package com.termux.app.surfaces.cards;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.termux.R;
import com.termux.app.place.PlaceLayout;
import com.termux.app.statusbar.StatusNotificationsMark;
import com.termux.app.surfaces.EditorRows;
import com.termux.app.surfaces.EditorRows.Row;
import com.termux.app.terminal.TerminalClockWidget;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

/**
 * The Status bar and Terminal cards (editor-redesign §2): their tables, the policy that maps
 * stored values to their segments and tiles, and their own rows laid out on pong's width at the
 * default text size and at Large (1.3), with native graphics so words are measured with real
 * font metrics.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = android.os.Build.VERSION_CODES.P, qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StatusTerminalCardsTest {

    private static final float[] SCALES = {1f, 1.3f};
    private static final EditorRows.Offers ALL = new EditorRows.Offers(true, true);
    private static final EditorRows.Offers NONE = new EditorRows.Offers(false, false);

    @After
    public void resetFontScale() {
        RuntimeEnvironment.setFontScale(1f);
    }

    // ------------------------------------------------------------------------------ tables

    @Test
    public void theStatusBarCardReadsPositionGlassClockOptionsIndicators() {
        assertEquals(Arrays.asList(Row.STATUS_POSITION, Row.GLASS_SECTION, Row.FROST, Row.OPACITY,
            Row.TINT, Row.CORNERS, Row.MARGIN, Row.CLOCK_FACE, Row.CLOCK_POSITION,
            Row.STATUS_OPTIONS, Row.STATUS_INDICATORS), rows(StatusBarCard.rows(ALL)));
        List<Row> rows = rows(StatusBarCard.rows(ALL));
        assertFalse("the Visible switch is the header's eye", rows.contains(Row.VISIBLE));
        assertFalse("12-hour is an Options tile", rows.contains(Row.CLOCK_12H));
    }

    @Test
    public void theTerminalCardReadsGlassContrastTrailEffectAndFont() {
        assertEquals(Arrays.asList(Row.GLASS_SECTION, Row.FROST, Row.OPACITY, Row.TINT,
            Row.TEXT_SIZE, Row.CORNERS, Row.MARGIN, Row.SPLIT_GAP, Row.CONTRAST, Row.CURSOR_TRAIL,
            Row.EFFECT, Row.LINK_FONT), rows(TerminalCard.rows(ALL)));
        assertFalse("no Screen effect where its shader does not run",
            rows(TerminalCard.rows(NONE)).contains(Row.EFFECT));
        assertTrue("the Terminal card owns Text size",
            rows(TerminalCard.rows(NONE)).contains(Row.TEXT_SIZE));
    }

    // ------------------------------------------------------------------------------ policy

    @Test
    public void positionIsTopOrBottomAndAColumnChoosesNeither() {
        assertEquals(0, StatusBarCard.positionIndex(PlaceLayout.Edge.TOP));
        assertEquals(1, StatusBarCard.positionIndex(PlaceLayout.Edge.BOTTOM));
        assertEquals(-1, StatusBarCard.positionIndex(PlaceLayout.Edge.LEFT));
        assertEquals(-1, StatusBarCard.positionIndex(PlaceLayout.Edge.RIGHT));
        assertEquals("no session yet reads Top", 0, StatusBarCard.positionIndex(null));
    }

    @Test
    public void theFaceTilesFollowTheStoredStyleAndMinimalKeepsItsWeight() {
        assertEquals(6, StatusBarCard.FACES.length);
        assertEquals("Slab is the default", 5, StatusBarCard.faceIndex(null));
        assertEquals(1, StatusBarCard.faceIndex(TERMUX_APP.TOP_PANE_CLOCK_STYLE_LCD));
        assertEquals("Minimal's medium weight is Minimal's tile", 2,
            StatusBarCard.faceIndex(TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM));
        assertEquals("an unknown face reads Flip, as the bar draws it", 0,
            StatusBarCard.faceIndex("nope"));
        assertEquals(TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM, StatusBarCard.faceStyle(2,
            TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM));
        assertEquals(TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL, StatusBarCard.faceStyle(2,
            TERMUX_APP.TOP_PANE_CLOCK_STYLE_SLAB));
        assertEquals(TERMUX_APP.TOP_PANE_CLOCK_STYLE_TAPE, StatusBarCard.faceStyle(4,
            TERMUX_APP.TOP_PANE_CLOCK_STYLE_MINIMAL_MEDIUM));
    }

    @Test
    public void clockPositionAndTheCameraGapReadTheStoredValues() {
        assertEquals(0, StatusBarCard.alignmentIndex(TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_LEFT));
        assertEquals(1, StatusBarCard.alignmentIndex(TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_CENTER));
        assertEquals(2, StatusBarCard.alignmentIndex(TERMUX_APP.TOP_PANE_CLOCK_ALIGNMENT_RIGHT));
        assertEquals(0, StatusBarCard.alignmentIndex(null));
        assertTrue(StatusBarCard.cameraGapOn(TERMUX_APP.STATUS_BAR_CUTOUT_AUTO));
        assertTrue(StatusBarCard.cameraGapOn(TERMUX_APP.STATUS_BAR_CUTOUT_CUSTOM));
        assertFalse(StatusBarCard.cameraGapOn(TERMUX_APP.STATUS_BAR_CUTOUT_OFF));
    }

    @Test
    public void theTrailAndEffectTilesFollowTheStoredValues() {
        assertEquals(7, TerminalCard.TRAILS.size());
        assertEquals("off", 0, TerminalCard.trailIndex(false, "comet"));
        assertEquals(6, TerminalCard.trailIndex(true, "comet"));
        assertEquals(2, TerminalCard.trailIndex(true, "motion_blur"));
        assertEquals("an unknown style reads Default", 1, TerminalCard.trailIndex(true, "nope"));
        assertEquals("a stored off is never a style", 1, TerminalCard.trailIndex(true, "off"));
        assertEquals(Arrays.asList("none", "crt", "crt_green", "crt_amber", "crt_material", "tft"),
            TerminalCard.EFFECTS);
        assertEquals(4, TerminalCard.effectIndex("crt_material"));
        assertEquals(0, TerminalCard.effectIndex("nope"));
        assertEquals(0, TerminalCard.effectIndex(null));
    }

    @Test
    public void theNotificationsMarkStandsOnlyWhileSomethingWaits() {
        assertFalse(StatusNotificationsMark.shown(true, 0));
        assertFalse(StatusNotificationsMark.shown(false, 3));
        assertTrue(StatusNotificationsMark.shown(true, 1));
        assertEquals("", StatusNotificationsMark.label(0));
        assertEquals("3", StatusNotificationsMark.label(3));
        assertEquals("9", StatusNotificationsMark.label(9));
        assertEquals("9+", StatusNotificationsMark.label(12));
    }

    @Test
    public void aFacePreviewShrinksToItsTileAndNeverGrows() {
        assertEquals(0.5f, StatusBarCard.FitFrame.fitScale(50, 30, 100, 30), 1e-6f);
        assertEquals(1f, StatusBarCard.FitFrame.fitScale(100, 40, 50, 20), 1e-6f);
        assertEquals(0.5f, StatusBarCard.FitFrame.fitScale(100, 15, 50, 30), 1e-6f);
        Calendar at = Calendar.getInstance();
        at.setTimeInMillis(StatusBarCard.previewTime());
        assertEquals(7, at.get(Calendar.HOUR_OF_DAY));
        assertEquals(48, at.get(Calendar.MINUTE));
    }

    // ----------------------------------------------------------------------- status rows

    @Test
    public void theStatusRowsStandInsideTheGutterAndKeepEveryWordWhole() {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale);
            FakeStatus host = new FakeStatus();
            String at = "at " + scale;
            for (Row row : new Row[] {Row.STATUS_POSITION, Row.GLASS_SECTION, Row.CLOCK_FACE,
                Row.CLOCK_POSITION, Row.STATUS_OPTIONS, Row.STATUS_INDICATORS}) {
                CardRowView built = StatusBarCard.view(f.themed, row, f.width, host);
                assertNotNull(row + " is the card's own", built);
                f.layOut(built.view);
                for (MaterialButton button : buttons(built.view)) {
                    if (button.getText() != null && button.getText().length() > 0)
                        assertNotCut(button, row + " " + at);
                    assertTrue(row + " " + at + ": a 48dp target",
                        button.getHeight() >= f.dp(48) - 1);
                    assertInsideGutter(f, button, row + " " + at);
                }
            }
            for (Row row : new Row[] {Row.FROST, Row.CORNERS, Row.CLOCK_12H})
                assertNull(row + " is the sheet's", StatusBarCard.view(f.themed, row, f.width, host));
        }
    }

    @Test
    public void theStatusRowsWriteThroughTheHostAndRestateFromIt() {
        Fixture f = new Fixture(1f);
        FakeStatus host = new FakeStatus();
        CardRowView position = StatusBarCard.view(f.themed, Row.STATUS_POSITION, f.width, host);
        f.layOut(position.view);
        List<MaterialButton> segments = buttons(position.view);
        assertEquals("Top and Bottom", 2, segments.size());
        assertEquals("Top", String.valueOf(segments.get(0).getContentDescription()));
        segments.get(1).performClick();
        assertEquals(1, host.position);
        host.position = 0;
        position.restate();
        assertTrue("restated to Top", segments.get(0).isChecked());

        CardRowView clock = StatusBarCard.view(f.themed, Row.CLOCK_FACE, f.width, host);
        f.layOut(clock.view);
        List<View> faces = tiles(clock.view);
        assertEquals(6, faces.size());
        faces.get(1).performClick();
        assertEquals("LCD", 1, host.face);
        List<TerminalClockWidget> clocks = new ArrayList<>();
        collect(clock.view, TerminalClockWidget.class, clocks);
        assertEquals("every tile draws its face", 6, clocks.size());
        for (TerminalClockWidget face : clocks) {
            assertFalse("a tile reads 07:48 with no seconds", face.isShowingSeconds());
            assertTrue("drawn whole in its tile",
                face.getWidth() * face.getScaleX() <= ((View) face.getParent()).getWidth() + 1);
        }

        CardRowView options = StatusBarCard.view(f.themed, Row.STATUS_OPTIONS, f.width, host);
        f.layOut(options.view);
        List<MaterialButton> optionTiles = buttons(options.view);
        assertEquals(4, optionTiles.size());
        optionTiles.get(StatusBarCard.OPTION_SECONDS).performClick();
        assertTrue("Seconds on", host.options[StatusBarCard.OPTION_SECONDS]);
        optionTiles.get(StatusBarCard.OPTION_CAMERA_GAP).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("Camera gap opens its setting", 1, host.cameraGapOpened);
        assertFalse("and never reads on: it is a shortcut, not a switch",
            optionTiles.get(StatusBarCard.OPTION_CAMERA_GAP).isChecked());

        CardRowView indicators = StatusBarCard.view(f.themed, Row.STATUS_INDICATORS, f.width, host);
        f.layOut(indicators.view);
        List<MaterialButton> indicatorTiles = buttons(indicators.view);
        assertEquals(5, indicatorTiles.size());
        assertFalse("Notifications starts off",
            indicatorTiles.get(StatusBarCard.INDICATOR_NOTIFICATIONS).isChecked());
        indicatorTiles.get(StatusBarCard.INDICATOR_NOTIFICATIONS).performClick();
        assertTrue(host.indicators[StatusBarCard.INDICATOR_NOTIFICATIONS]);
        host.indicators[StatusBarCard.INDICATOR_CPU] = false;
        indicators.restate();
        assertFalse(indicatorTiles.get(StatusBarCard.INDICATOR_CPU).isChecked());
    }

    // --------------------------------------------------------------------- terminal rows

    @Test
    public void theTerminalRowsStandInsideTheGutterAndKeepEveryWordWhole() {
        for (float scale : SCALES) {
            Fixture f = new Fixture(scale);
            FakeTerminal host = new FakeTerminal();
            String at = "at " + scale;
            for (Row row : new Row[] {Row.GLASS_SECTION, Row.CONTRAST, Row.CURSOR_TRAIL,
                Row.EFFECT, Row.LINK_FONT}) {
                CardRowView built = TerminalCard.view(f.themed, row, f.width, host);
                assertNotNull(row + " is the card's own", built);
                f.layOut(built.view);
                for (MaterialButton button : buttons(built.view)) {
                    assertNotCut(button, row + " " + at);
                    assertTrue(row + " " + at + ": a 48dp target",
                        button.getHeight() >= f.dp(48) - 1);
                    assertInsideGutter(f, button, row + " " + at);
                }
            }
            assertNull(TerminalCard.view(f.themed, Row.TEXT_SIZE, f.width, host));
        }
    }

    @Test
    public void theTerminalRowsWriteThroughTheHost() {
        Fixture f = new Fixture(1f);
        FakeTerminal host = new FakeTerminal();
        CardRowView contrast = TerminalCard.view(f.themed, Row.CONTRAST, f.width, host);
        f.layOut(contrast.view);
        List<MaterialButton> words = buttons(contrast.view);
        assertEquals(Arrays.asList("Softer", "Default", "Harder"), Arrays.asList(
            words.get(0).getText().toString(), words.get(1).getText().toString(),
            words.get(2).getText().toString()));
        assertTrue("Default at open", words.get(1).isChecked());
        words.get(2).performClick();
        assertEquals(2, host.contrast);

        CardRowView trails = TerminalCard.view(f.themed, Row.CURSOR_TRAIL, f.width, host);
        f.layOut(trails.view);
        List<View> trailTiles = tiles(trails.view);
        assertEquals(7, trailTiles.size());
        trailTiles.get(6).performClick();
        assertEquals("Comet", 6, host.trail);

        CardRowView effects = TerminalCard.view(f.themed, Row.EFFECT, f.width, host);
        f.layOut(effects.view);
        List<View> effectTiles = tiles(effects.view);
        assertEquals(6, effectTiles.size());
        assertEquals("Material", String.valueOf(effectTiles.get(4).getContentDescription()));
        effectTiles.get(4).performClick();
        assertEquals(4, host.effect);
        host.effect = 0;
        effects.restate();
        assertTrue(effectTiles.get(0).isSelected());

        CardRowView more = TerminalCard.view(f.themed, Row.LINK_FONT, f.width, host);
        f.layOut(more.view);
        List<com.google.android.material.chip.Chip> chips = new ArrayList<>();
        collect(more.view, com.google.android.material.chip.Chip.class, chips);
        assertEquals(1, chips.size());
        assertEquals("Font", chips.get(0).getText().toString());
        chips.get(0).performClick();
        assertEquals(1, host.fontOpened);
    }

    // ------------------------------------------------------------------------------ fakes

    private static final class FakeStatus implements StatusBarCard.Host {
        int position;
        int face = 5;
        int align;
        final boolean[] options = {false, false, false, true};
        final boolean[] indicators = {true, true, true, false, true};
        int cameraGapOpened;

        @Override public int position() { return position; }
        @Override public void setPosition(int index) { position = index; }
        @Override public int clockFace() { return face; }
        @Override public void setClockFace(int index) { face = index; }
        @Override public int clockPosition() { return align; }
        @Override public void setClockPosition(int index) { align = index; }
        @Override public boolean option(int index) { return options[index]; }
        @Override public void setOption(int index, boolean on) { options[index] = on; }
        @Override public boolean indicator(int index) { return indicators[index]; }
        @Override public void setIndicator(int index, boolean on) { indicators[index] = on; }
        @Override public void openCameraGap() { cameraGapOpened++; }
    }

    private static final class FakeTerminal implements TerminalCard.Host {
        int contrast = 1;
        int trail = 1;
        int effect;
        int fontOpened;

        @Override public int contrast() { return contrast; }
        @Override public void setContrast(int index) { contrast = index; }
        @Override public int trail() { return trail; }
        @Override public void setTrail(int index) { trail = index; }
        @Override public int effect() { return effect; }
        @Override public void setEffect(int index) { effect = index; }
        @Override public void openFont() { fontOpened++; }
    }

    // ---------------------------------------------------------------------------- helpers

    @NonNull
    private static List<Row> rows(@NonNull List<EditorRows.Spec> specs) {
        List<Row> out = new ArrayList<>();
        for (EditorRows.Spec spec : specs)
            out.add(spec.row);
        return out;
    }

    @NonNull
    private static List<MaterialButton> buttons(@NonNull View root) {
        List<MaterialButton> out = new ArrayList<>();
        collect(root, MaterialButton.class, out);
        return out;
    }

    /** A preview row's tiles: the clickable boxes over each preview. */
    @NonNull
    private static List<View> tiles(@NonNull View root) {
        List<View> out = new ArrayList<>();
        collectClickable(root, out);
        return out;
    }

    private static void collectClickable(@NonNull View view, @NonNull List<View> out) {
        if (view.isClickable() && view instanceof ViewGroup && !(view instanceof MaterialButton)) {
            out.add(view);
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                collectClickable(group.getChildAt(i), out);
        }
    }

    private static <T extends View> void collect(@NonNull View view, @NonNull Class<T> type,
                                                 @NonNull List<T> out) {
        if (type.isInstance(view))
            out.add(type.cast(view));
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++)
                collect(group.getChildAt(i), type, out);
        }
    }

    private static void assertNotCut(@NonNull TextView view, @NonNull String at) {
        Layout layout = view.getLayout();
        if (layout == null)
            return;
        for (int i = 0; i < layout.getLineCount(); i++)
            assertEquals(at + ": \"" + view.getText() + "\" is cut", 0, layout.getEllipsisCount(i));
    }

    private static void assertInsideGutter(@NonNull Fixture f, @NonNull View view,
                                           @NonNull String at) {
        int[] where = new int[2];
        view.getLocationInWindow(where);
        int[] parent = new int[2];
        f.parent.getLocationInWindow(parent);
        int left = where[0] - parent[0];
        assertTrue(at + ": starts inside the gutter (" + left + ")", left >= f.dp(16) - 1);
        assertTrue(at + ": ends inside the gutter (" + (left + view.getWidth()) + " of "
            + f.width + ")", left + view.getWidth() <= f.width - f.dp(16) + 1);
    }

    private static final class Fixture {
        final Context themed;
        final FrameLayout parent;
        final int width;
        final int height;

        Fixture(float fontScale) {
            RuntimeEnvironment.setFontScale(fontScale);
            Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
            themed = new ContextThemeWrapper(activity,
                R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            float density = themed.getResources().getDisplayMetrics().density;
            width = Math.round(411 * density);
            height = Math.round(914 * density);
            parent = new FrameLayout(themed);
            activity.setContentView(parent, new ViewGroup.LayoutParams(width, height));
        }

        int dp(float value) {
            return Math.round(value * themed.getResources().getDisplayMetrics().density);
        }

        void layOut(@NonNull View row) {
            parent.removeAllViews();
            parent.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
            parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            parent.layout(0, 0, width, height);
        }
    }
}
