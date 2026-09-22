package com.termux.app.terminal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeyColorRole;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.extrakeys.PlaceSwitchGlyph;

import com.google.android.material.color.utilities.Hct;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;

import com.google.android.material.button.MaterialButton;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

/**
 * How the key row draws what it was told: a key that cannot act here is dead, a key that was given
 * a colour wears it, and while the editor is picking neither of those gets in the way.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, application = android.app.Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ExtraKeysViewKeyStyleTest {

    private static final String ROW =
        "[['ESC', {key: 'TAB', color: 'primary_container'},"
            + " {key: 'tool:wall.widgets', color: 'primary'},"
            + " {key: 'tool:wall.terminal', color: 'secondary'},"
            + " {key: 'tool:wall.display', color: 'tertiary'}]]";

    private ExtraKeysView view;
    private android.content.Context context;

    @Before
    public void setUp() throws Exception {
        // MaterialButton refuses to build under anything that is not an AppCompat descendant, and
        // the role colours are read off the theme, so the row is built in a Material one.
        context = new android.view.ContextThemeWrapper(RuntimeEnvironment.getApplication(),
            com.google.android.material.R.style.Theme_Material3_DayNight);
        view = new ExtraKeysView(context, null);
        view.reload(new ExtraKeysInfo(ROW,
            ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES), 40f);
    }

    @Test
    public void aKeyWithNoColourKeepsTheRowsFlatCap() {
        assertTrue(button(0).getBackground() instanceof ColorDrawable);
    }

    @Test
    public void aColouredKeyWearsItsRoleAndTheMatchingLabel() {
        MaterialButton tab = button(1);
        Drawable background = tab.getBackground();
        assertTrue("a coloured cap sits inset from its cell",
            background instanceof InsetDrawable);
        GradientDrawable cap = capOf(background);
        int expected = ExtraKeyColorRole.PRIMARY_CONTAINER
            .background(context);
        assertEquals(expected, cap.getColor().getDefaultColor());
        assertEquals(ExtraKeyColorRole.PRIMARY_CONTAINER.label(context),
            tab.getCurrentTextColor());
        assertTrue(cap.getCornerRadius() > 0f);
        // MaterialButton re-applies its own (transparent, borderless-style) backgroundTint onto
        // any background it is first given, the moment that background is set
        // (MaterialButtonHelper#setBackgroundOverwritten -> setSupportBackgroundTintList) — that
        // tint is realised as a colour filter over the drawable, which is what actually painted
        // over the fill and left only the label showing the role. Both must be clear for the cap's
        // own colour to be what renders.
        assertNull("a coloured key must not carry a tint that would paint over its fill",
            tab.getBackgroundTintList());
        assertNull("nor an already-applied colour filter doing the same thing",
            cap.getColorFilter());
    }

    @Test
    public void anUnusableKeyIsDeadAtThirtyEightPercentAndTakesNoTaps() {
        // Everything but the place switch is unusable here.
        view.setKeyUsabilityPolicy(value -> value.startsWith("tool:wall."));

        MaterialButton esc = button(0);
        assertFalse("a key that cannot act must not take a tap", esc.isEnabled());
        assertEquals(97, Color.alpha(esc.getCurrentTextColor()));
        assertTrue("a dead key carries no background tint",
            esc.getBackground() instanceof ColorDrawable);

        // A coloured key that cannot act loses its colour too, rather than reading as live.
        MaterialButton tab = button(1);
        assertFalse(tab.isEnabled());
        assertTrue(tab.getBackground() instanceof ColorDrawable);

        MaterialButton wall = button(2);
        assertTrue(wall.isEnabled());
        assertEquals(255, Color.alpha(wall.getCurrentTextColor()));
    }

    @Test
    public void aDeadKeyComesBackWhenThePlaceMovesUnderIt() {
        view.setKeyUsabilityPolicy(value -> false);
        assertFalse(button(0).isEnabled());
        view.setKeyUsabilityPolicy(value -> true);
        assertTrue(button(0).isEnabled());
        view.setKeyUsabilityPolicy(null);
        assertTrue(button(0).isEnabled());
    }

    @Test
    public void aDeadKeyNeverReachesTheClient() {
        RecordingClient client = new RecordingClient();
        view.setExtraKeysViewClient(client);
        view.setKeyUsabilityPolicy(value -> false);
        // A disabled view swallows the touch without it ever reaching the touch listener, which is
        // what "taps ignored" means here: the key eats the press and does nothing with it.
        button(0).dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN));
        button(0).dispatchTouchEvent(motion(MotionEvent.ACTION_UP));
        assertTrue(client.clicked.isEmpty());
    }

    @Test
    public void pickModeReportsTheKeyInsteadOfFiringIt() {
        RecordingClient client = new RecordingClient();
        view.setExtraKeysViewClient(client);
        // Even a key that cannot act here can be dressed, so pick mode revives every one of them.
        view.setKeyUsabilityPolicy(value -> false);
        final List<Integer> picked = new ArrayList<>();
        view.setKeyPickListener((index, info, button) -> picked.add(index));
        view.setPickMode(true);

        assertTrue("pick mode makes every key live again", button(0).isEnabled());
        button(0).dispatchTouchEvent(motion(MotionEvent.ACTION_DOWN));
        button(0).dispatchTouchEvent(motion(MotionEvent.ACTION_UP));
        assertEquals(1, picked.size());
        assertEquals(Integer.valueOf(0), picked.get(0));
        assertTrue("a picked key must not fire", client.clicked.isEmpty());

        view.setPickMode(false);
        assertFalse("the row goes back to what the place allows", button(0).isEnabled());
    }

    @Test
    public void aPreviewedColourStandsInFrontOfTheStoredOneUntilItIsCleared() {
        MaterialButton esc = button(0);
        view.previewKeyColor(esc, ExtraKeyColorRole.ERROR);
        assertEquals(ExtraKeyColorRole.ERROR.background(context),
            capOf(esc.getBackground()).getColor().getDefaultColor());

        // Previewing "no colour" over a key that has one takes its colour off.
        MaterialButton tab = button(1);
        view.previewKeyColor(tab, null);
        assertTrue(tab.getBackground() instanceof ColorDrawable);

        view.clearPreviewColors();
        assertTrue(esc.getBackground() instanceof ColorDrawable);
        assertTrue("the stored colour comes back",
            tab.getBackground() instanceof InsetDrawable);
    }

    @Test
    public void aThemeRefreshRepaintsWithoutRebuildingTheRow() {
        MaterialButton tab = button(1);
        Drawable before = tab.getBackground();
        view.refreshKeyStyles();
        assertNotNull(tab.getBackground());
        assertTrue(tab.getBackground() instanceof InsetDrawable);
        // Same colour, freshly resolved — the point is that the row was not rebuilt.
        assertEquals(capOf(before).getColor().getDefaultColor(),
            capOf(tab.getBackground()).getColor().getDefaultColor());
        assertSame(tab, button(1));
    }

    @Test
    public void reloadingDropsEveryPreviewSoAnEditedRowStartsFromWhatItStores() throws Exception {
        view.previewKeyColor(button(0), ExtraKeyColorRole.ERROR);
        view.reload(new ExtraKeysInfo(ROW,
            ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES), 40f);
        assertTrue(button(0).getBackground() instanceof ColorDrawable);
    }

    @Test
    public void theRowStillAnswersForItsKeysByIndex() {
        assertNotNull(view.definitionForChild(0));
        assertEquals("ESC", view.definitionForChild(0).getKey());
        assertEquals("TAB", view.definitionForChild(1).getKey());
        assertNull(view.definitionForChild(9));
    }

    @Test
    public void aDefaultKeyStillPaintsTheRowsFlatColourAfterTheFix() {
        // A flat ColorDrawable actually rasterises under Robolectric (GradientDrawable's shadow
        // does not, so the coloured-key equivalent above is asserted on the drawable's resolved
        // colour and tint state instead); drawing this one is a real pixel-level check that the
        // fix left the uncoloured look untouched.
        int expected = view.getButtonBackgroundColor();
        assertEquals(expected, centerPixel(button(0).getBackground()));
    }

    @Test
    public void aDisabledKeyStillPaintsTheRowsFlatColourAfterTheFix() {
        view.setKeyUsabilityPolicy(value -> false);
        int expected = view.getButtonBackgroundColor();
        assertEquals(expected, centerPixel(button(0).getBackground()));
    }

    @Test
    public void aPlaceSwitchCarriesNoCapAndWearsItsAccentOnTheGlyph() {
        showing("tool:wall.terminal");

        for (int index : new int[] {2, 3, 4}) {
            assertTrue("a place switch sits on the row's own glass, like a plain key",
                button(index).getBackground() instanceof ColorDrawable);
            assertEquals("and paints nothing of its own behind the glyph",
                view.getButtonBackgroundColor(), centerPixel(button(index).getBackground()));
        }

        assertEquals("the place in front is at full strength",
            vivid(1), button(3).getCurrentTextColor());
        assertEquals("the ones behind it keep the colour and lose the brightness",
            dimmed(0), button(2).getCurrentTextColor());
        assertEquals(dimmed(2), button(4).getCurrentTextColor());
    }

    @Test
    public void theThreePlaceGlyphsAreThreeColoursRatherThanThreeShadesOfOne() {
        showing("tool:wall.terminal");
        // The defaults are primary / secondary / tertiary, and a Material scheme draws all three
        // off one seed: primary and secondary come out on the same hue. Whatever the row does to
        // make them vivid has to leave three glyphs a thumb can tell apart.
        int[] glyph = {button(2).getCurrentTextColor(), button(3).getCurrentTextColor(),
            button(4).getCurrentTextColor()};
        for (int first = 0; first < glyph.length; first++) {
            for (int second = first + 1; second < glyph.length; second++) {
                double apart = PlaceSwitchGlyph.hueDistance(
                    Hct.fromInt(opaque(glyph[first])).getHue(),
                    Hct.fromInt(opaque(glyph[second])).getHue());
                assertTrue("switches " + first + " and " + second + " are " + apart + "° apart",
                    apart >= PlaceSwitchGlyph.MIN_HUE_SEPARATION - 1d);
            }
        }
    }

    /**
     * The focused switch wears a soft, dim halo — a neon tube's spread, 3 dp at about a quarter of
     * its own colour — and nothing else on the row glows. The 7 dp, 60% halo it used to wear read
     * as a smudge; {@link PlaceSwitchGlyph#GLOW_RADIUS_DP} and {@link PlaceSwitchGlyph#GLOW_ALPHA}
     * are the two lines that set it.
     */
    @Test
    public void onlyTheFocusedPlaceSwitchWearsASoftHalo() {
        showing("tool:wall.terminal");

        assertEquals("the halo is soft", 3f, PlaceSwitchGlyph.GLOW_RADIUS_DP, 0f);
        assertEquals("and dim", 60, PlaceSwitchGlyph.GLOW_ALPHA);
        for (int index = 0; index < 5; index++) {
            if (index == 3) continue;
            assertEquals("key " + index + " does not glow", 0f, button(index).getShadowRadius(), 0f);
        }
        assertTrue("the focused switch glows", button(3).getShadowRadius() > 0f);
        assertEquals("in its own colour", vivid(1) & 0x00FFFFFF, button(3).getShadowColor() & 0x00FFFFFF);
        assertEquals("at a quarter", PlaceSwitchGlyph.GLOW_ALPHA, (button(3).getShadowColor() >>> 24));
        // Which leaves the colour to say it, and it does: full strength against two faded ones.
        assertEquals(vivid(1), button(3).getCurrentTextColor());
        assertEquals(dimmed(0), button(2).getCurrentTextColor());
        assertEquals(dimmed(2), button(4).getCurrentTextColor());
    }

    @Test
    public void theBrightSwitchFollowsTheWallToItsNextPlace() {
        showing("tool:wall.terminal");
        assertEquals(dimmed(2), button(4).getCurrentTextColor());

        showing("tool:wall.display");
        assertEquals(vivid(2), button(4).getCurrentTextColor());
        assertEquals(dimmed(1), button(3).getCurrentTextColor());
        assertEquals(dimmed(0), button(2).getCurrentTextColor());
    }

    /**
     * What the bar's place marks are painted in comes from here: the same colour the key wears,
     * against the key value that says which place it switches to. Read across the row in one pass,
     * so a switch that was spread off another's hue reports the hue it is really drawn in.
     */
    @Test
    public void theRowPublishesEachPlacesColourAgainstTheKeyThatSwitchesToIt() {
        showing("tool:wall.terminal");

        java.util.Map<String, Integer> published = new java.util.LinkedHashMap<>();
        view.setPlaceGlyphColorListener(published::putAll);

        assertEquals("only the place switches", 3, published.size());
        assertEquals(Integer.valueOf(vivid(0)), published.get("tool:wall.widgets"));
        assertEquals(Integer.valueOf(vivid(1)), published.get("tool:wall.terminal"));
        assertEquals(Integer.valueOf(vivid(2)), published.get("tool:wall.display"));
        // The colour is the one the key is really painted in, focus aside: the focused key wears
        // it at full alpha and the others the same colour faded.
        assertEquals(published.get("tool:wall.terminal").intValue(),
            button(3).getCurrentTextColor());
        assertEquals(opaque(button(2).getCurrentTextColor()),
            opaque(published.get("tool:wall.widgets")));
    }

    /** And it says so again whenever it restates the keys, which is what carries a place change. */
    @Test
    public void thePublishedColoursArriveAgainWhenTheRowRestatesItsKeys() {
        showing("tool:wall.terminal");
        final List<java.util.Map<String, Integer>> seen = new ArrayList<>();
        view.setPlaceGlyphColorListener(seen::add);
        assertEquals("setting the listener says what they are now", 1, seen.size());

        showing("tool:wall.display");
        assertTrue("and the wall moving says it again", seen.size() > 1);
        assertEquals(Integer.valueOf(vivid(2)),
            seen.get(seen.size() - 1).get("tool:wall.display"));

        // A row with no place switches at all publishes nothing to follow.
        view.setPlaceSwitchPolicy(null);
        assertTrue(seen.get(seen.size() - 1).isEmpty());
    }

    @Test
    public void aColouredKeyThatIsNotAPlaceSwitchStillWearsItsCap() {
        showing("tool:wall.terminal");
        assertTrue("only the place switches lost their cap",
            button(1).getBackground() instanceof InsetDrawable);
        assertEquals("and its colour is the role's own, untouched by the vivid rule",
            ExtraKeyColorRole.PRIMARY_CONTAINER.background(context),
            capOf(button(1).getBackground()).getColor().getDefaultColor());
        assertEquals(ExtraKeyColorRole.PRIMARY_CONTAINER.label(context),
            button(1).getCurrentTextColor());
        assertEquals("nor does it glow", 0f, button(1).getShadowRadius(), 0f);
    }

    /** Tells the row which place switch points at the place in front. */
    private void showing(final String focusedKeyValue) {
        view.setPlaceSwitchPolicy(value -> {
            if (!value.startsWith("tool:wall."))
                return ExtraKeysView.PlaceFocus.NOT_A_PLACE;
            return value.equals(focusedKeyValue)
                ? ExtraKeysView.PlaceFocus.FOCUSED
                : ExtraKeysView.PlaceFocus.UNFOCUSED;
        });
    }

    /**
     * The colour the place switch at {@code switchIndex} (0 = Home, 1 = Terminal, 2 = Display)
     * paints its glyph in: its role made vivid, spread off any other switch sharing its hue.
     */
    private int vivid(int switchIndex) {
        return PlaceSwitchGlyph.vividRow(new int[] {
            ExtraKeyColorRole.PRIMARY.background(context),
            ExtraKeyColorRole.SECONDARY.background(context),
            ExtraKeyColorRole.TERTIARY.background(context)
        }, PlaceSwitchGlyph.isDarkGlass(view.getButtonTextColor()))[switchIndex];
    }

    /** The same colour as an unfocused switch shows it: held back to 57%. */
    private int dimmed(int switchIndex) {
        return (145 << 24) | (vivid(switchIndex) & 0x00FFFFFF);
    }

    private static int opaque(int color) {
        return color | 0xFF000000;
    }

    /** What {@code drawable} actually renders at its centre, tint and all. */
    private static int centerPixel(Drawable drawable) {
        int size = 40;
        drawable.setBounds(0, 0, size, size);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        drawable.draw(new Canvas(bitmap));
        return bitmap.getPixel(size / 2, size / 2);
    }

    /** The coloured cap a key's background is built from, unwrapping the cell inset. */
    private static GradientDrawable capOf(Drawable background) {
        Drawable drawable = background instanceof InsetDrawable
            ? ((InsetDrawable) background).getDrawable()
            : background;
        return (GradientDrawable) drawable;
    }

    private MaterialButton button(int index) {
        return (MaterialButton) view.getChildAt(index);
    }

    private static MotionEvent motion(int action) {
        long now = SystemClock.uptimeMillis();
        return MotionEvent.obtain(now, now, action, 1f, 1f, 0);
    }

    private static final class RecordingClient implements ExtraKeysView.IExtraKeysView {
        final List<String> clicked = new ArrayList<>();

        @Override
        public void onExtraKeyButtonClick(android.view.View view, ExtraKeyButton buttonInfo,
                                          MaterialButton button) {
            clicked.add(buttonInfo.getKey());
        }

        @Override
        public boolean performExtraKeyButtonHapticFeedback(android.view.View view,
                                                           ExtraKeyButton buttonInfo,
                                                           MaterialButton button) {
            return true;
        }
    }
}
