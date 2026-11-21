package com.termux.app.terminal.extrakeys;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.google.android.material.color.utilities.Hct;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeyColorRole;
import com.termux.shared.termux.extrakeys.ExtraKeysClient;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.extrakeys.PlaceSwitchGlyph;
import com.termux.shared.termux.extrakeys.SpecialButton;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * How a key page draws what it was told (a key that cannot act here is dead, a key that was given a
 * colour wears it, place switches wear their glyph colour) and how a touch on its keys turns into
 * keys sent.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {android.os.Build.VERSION_CODES.P}, application = android.app.Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ExtraKeysPageViewTest {

    private static final String ROW =
        "[['ESC', {key: 'TAB', color: 'primary_container'},"
            + " {key: 'tool:wall.widgets', color: 'primary'},"
            + " {key: 'tool:wall.terminal', color: 'secondary'},"
            + " {key: 'tool:wall.display', color: 'tertiary'}]]";

    private ExtraKeysPageView view;
    private android.content.Context context;

    @Before
    public void setUp() throws Exception {
        // The role colours are read off the theme, so the page is built in a Material one.
        context = new android.view.ContextThemeWrapper(RuntimeEnvironment.getApplication(),
            com.google.android.material.R.style.Theme_Material3_DayNight);
        view = new ExtraKeysPageView(context);
        view.setKeys(info(ROW));
        layout(view, 500, 40);
    }

    // ---- Look.

    @Test
    public void aKeyWithNoColourKeepsTheFlatCap() {
        assertTrue(cap(0).getBackground() instanceof ColorDrawable);
    }

    @Test
    public void aColouredKeyWearsItsRoleAndTheMatchingLabel() {
        ExtraKeyCapView tab = cap(1);
        Drawable background = tab.getBackground();
        assertTrue("a coloured cap sits inset from its cell", background instanceof InsetDrawable);
        GradientDrawable shape = shapeOf(background);
        assertEquals(ExtraKeyColorRole.PRIMARY_CONTAINER.background(context),
            shape.getColor().getDefaultColor());
        assertEquals(ExtraKeyColorRole.PRIMARY_CONTAINER.label(context), tab.getCurrentTextColor());
        assertTrue(shape.getCornerRadius() > 0f);
        assertNull("a coloured key carries no tint that would paint over its fill",
            tab.getBackgroundTintList());
        assertNull(shape.getColorFilter());
    }

    @Test
    public void anUnusableKeyIsDeadAtThirtyEightPercentAndTakesNoTaps() {
        view.setKeyUsabilityPolicy(value -> value.startsWith("tool:wall."));

        ExtraKeyCapView esc = cap(0);
        assertFalse(esc.isEnabled());
        assertTrue(esc.isDead());
        assertEquals(97, Color.alpha(esc.getCurrentTextColor()));
        assertTrue(esc.getBackground() instanceof ColorDrawable);

        // A coloured key that cannot act loses its colour too, rather than reading as live.
        assertFalse(cap(1).isEnabled());
        assertTrue(cap(1).getBackground() instanceof ColorDrawable);

        assertTrue(cap(2).isEnabled());
        assertEquals(255, Color.alpha(cap(2).getCurrentTextColor()));
    }

    @Test
    public void aDeadKeyComesBackWhenThePlaceMovesUnderIt() {
        view.setKeyUsabilityPolicy(value -> false);
        assertFalse(cap(0).isEnabled());
        view.setKeyUsabilityPolicy(value -> true);
        assertTrue(cap(0).isEnabled());
        view.setKeyUsabilityPolicy(null);
        assertTrue(cap(0).isEnabled());
    }

    @Test
    public void aThemeRefreshRepaintsWithoutRebuildingThePage() {
        ExtraKeyCapView tab = cap(1);
        Drawable before = tab.getBackground();
        view.refreshKeyStyles();
        assertTrue(tab.getBackground() instanceof InsetDrawable);
        assertEquals(shapeOf(before).getColor().getDefaultColor(),
            shapeOf(tab.getBackground()).getColor().getDefaultColor());
        assertSame(tab, cap(1));
    }

    @Test
    public void thePageStillAnswersForItsKeysByIndexAndByValue() {
        assertNotNull(view.keyAt(0));
        assertEquals("ESC", view.keyAt(0).getKey());
        assertEquals("TAB", view.keyAt(1).getKey());
        assertNull(view.keyAt(9));
        assertSame(cap(1), view.capForKey("TAB"));
        assertNull(view.capForKey("NOPE"));
    }

    @Test
    public void aDefaultKeyPaintsThePagesFlatColour() {
        assertEquals(view.getButtonBackgroundColor(), centerPixel(cap(0).getBackground()));
    }

    @Test
    public void aDeadKeyPaintsThePagesFlatColour() {
        view.setKeyUsabilityPolicy(value -> false);
        assertEquals(view.getButtonBackgroundColor(), centerPixel(cap(0).getBackground()));
    }

    @Test
    public void aPlaceSwitchCarriesNoCapAndWearsItsAccentOnTheGlyph() {
        showing("tool:wall.terminal");
        for (int index : new int[] {2, 3, 4}) {
            assertTrue(cap(index).getBackground() instanceof ColorDrawable);
            assertEquals(view.getButtonBackgroundColor(), centerPixel(cap(index).getBackground()));
        }
        assertEquals("the place in front is at full strength", vivid(1), cap(3).getCurrentTextColor());
        assertEquals("the ones behind keep the colour and lose the brightness", dimmed(0),
            cap(2).getCurrentTextColor());
        assertEquals(dimmed(2), cap(4).getCurrentTextColor());
    }

    @Test
    public void theThreePlaceGlyphsAreThreeColoursRatherThanThreeShadesOfOne() {
        showing("tool:wall.terminal");
        int[] glyph = {cap(2).getCurrentTextColor(), cap(3).getCurrentTextColor(),
            cap(4).getCurrentTextColor()};
        for (int first = 0; first < glyph.length; first++) {
            for (int second = first + 1; second < glyph.length; second++) {
                double apart = PlaceSwitchGlyph.hueDistance(
                    Hct.fromInt(opaque(glyph[first])).getHue(),
                    Hct.fromInt(opaque(glyph[second])).getHue());
                assertTrue("switches " + first + " and " + second + " are " + apart + " degrees apart",
                    apart >= PlaceSwitchGlyph.MIN_HUE_SEPARATION - 1d);
            }
        }
    }

    @Test
    public void onlyTheFocusedPlaceSwitchWearsASoftHalo() {
        showing("tool:wall.terminal");
        for (int index = 0; index < 5; index++) {
            if (index == 3) continue;
            assertEquals("key " + index + " does not glow", 0f, cap(index).getShadowRadius(), 0f);
        }
        assertTrue("the focused switch glows", cap(3).getShadowRadius() > 0f);
        assertEquals("in its own colour", vivid(1) & 0x00FFFFFF, cap(3).getShadowColor() & 0x00FFFFFF);
        assertEquals(PlaceSwitchGlyph.GLOW_ALPHA, cap(3).getShadowColor() >>> 24);
    }

    @Test
    public void theBrightSwitchFollowsTheWallToItsNextPlace() {
        showing("tool:wall.terminal");
        assertEquals(dimmed(2), cap(4).getCurrentTextColor());
        showing("tool:wall.display");
        assertEquals(vivid(2), cap(4).getCurrentTextColor());
        assertEquals(dimmed(1), cap(3).getCurrentTextColor());
        assertEquals(dimmed(0), cap(2).getCurrentTextColor());
    }

    @Test
    public void thePagePublishesEachPlacesColourAgainstTheKeyThatSwitchesToIt() {
        showing("tool:wall.terminal");
        Map<String, Integer> published = new LinkedHashMap<>();
        view.setPlaceGlyphColorListener(published::putAll);

        assertEquals("only the place switches", 3, published.size());
        assertEquals(Integer.valueOf(vivid(0)), published.get("tool:wall.widgets"));
        assertEquals(Integer.valueOf(vivid(1)), published.get("tool:wall.terminal"));
        assertEquals(Integer.valueOf(vivid(2)), published.get("tool:wall.display"));
        assertEquals(published.get("tool:wall.terminal").intValue(), cap(3).getCurrentTextColor());
        assertEquals(opaque(cap(2).getCurrentTextColor()), opaque(published.get("tool:wall.widgets")));
    }

    @Test
    public void thePublishedColoursArriveAgainWhenThePageRestatesItsKeys() {
        showing("tool:wall.terminal");
        final List<Map<String, Integer>> seen = new ArrayList<>();
        view.setPlaceGlyphColorListener(seen::add);
        assertEquals("setting the listener says what they are now", 1, seen.size());

        showing("tool:wall.display");
        assertTrue(seen.size() > 1);
        assertEquals(Integer.valueOf(vivid(2)), seen.get(seen.size() - 1).get("tool:wall.display"));

        view.setPlaceSwitchPolicy(null);
        assertTrue(seen.get(seen.size() - 1).isEmpty());
    }

    @Test
    public void aColouredKeyThatIsNotAPlaceSwitchStillWearsItsCap() {
        showing("tool:wall.terminal");
        assertTrue(cap(1).getBackground() instanceof InsetDrawable);
        assertEquals(ExtraKeyColorRole.PRIMARY_CONTAINER.background(context),
            shapeOf(cap(1).getBackground()).getColor().getDefaultColor());
        assertEquals(ExtraKeyColorRole.PRIMARY_CONTAINER.label(context), cap(1).getCurrentTextColor());
        assertEquals(0f, cap(1).getShadowRadius(), 0f);
    }

    // ---- Keys and layout.

    @Test
    public void capsCarryStableKeyIdsAndKeyAtAnswersByIndex() throws Exception {
        String rows = "[['A', {key: 'B', popup: 'C'}, 'A'],['A', {key: 'B', popup: 'C'}]]";
        ExtraKeysPageView page = new ExtraKeysPageView(context);
        page.setKeys(info(rows));
        assertEquals(5, page.keyCount());
        assertEquals(new KeyId("A", null, 0), page.capAt(0).keyId());
        assertEquals(new KeyId("B", "C", 0), page.capAt(1).keyId());
        assertEquals(new KeyId("A", null, 1), page.capAt(2).keyId());
        assertEquals(new KeyId("A", null, 2), page.capAt(3).keyId());
        assertEquals(new KeyId("B", "C", 1), page.capAt(4).keyId());
        assertEquals("C", page.keyAt(1).getPopup().getKey());

        // Another build of the same keys gives the same ids, which is how a key is found again.
        ExtraKeysPageView other = new ExtraKeysPageView(context);
        other.setKeys(info(rows));
        for (int i = 0; i < 5; i++) assertEquals(page.capAt(i).keyId(), other.capAt(i).keyId());
        assertEquals(new KeyId("A", null, 0).hashCode(), new KeyId("A", null, 0).hashCode());
    }

    @Test
    public void aNullKeySetClearsThePageWithoutFailing() {
        view.setKeys(null);
        assertEquals(0, view.getChildCount());
        assertEquals(0, view.rowCount());
        assertNull(view.keyAt(0));
    }

    @Test
    @Config(qualifiers = "ar")
    public void aRightToLeftLocaleKeepsTheFirstKeyOnTheLeft() throws Exception {
        ExtraKeysPageView page = new ExtraKeysPageView(RuntimeEnvironment.getApplication());
        page.setKeys(info("[['A','B','C']]"));
        layout(page, 300, 40);
        assertEquals(View.LAYOUT_DIRECTION_LTR, page.getLayoutDirection());
        assertEquals(0, page.getChildAt(0).getLeft());
        assertTrue(page.getChildAt(1).getLeft() > page.getChildAt(0).getLeft());
        assertTrue(page.getChildAt(2).getLeft() > page.getChildAt(1).getLeft());
    }

    @Test
    public void aVerticalPageTransposesTheMatrix() throws Exception {
        ExtraKeysPageView page = new ExtraKeysPageView(context);
        page.setKeys(info("[['A','B','C'],['D','E','F']]"));
        page.setVertical(true);
        layout(page, 200, 300);
        assertEquals("the configured rows are still two", 2, page.rowCount());
        // A, B, C run down the first column; D, E, F down the second.
        assertEquals(0, page.getChildAt(0).getLeft());
        assertEquals(0, page.getChildAt(1).getLeft());
        assertEquals(0, page.getChildAt(2).getLeft());
        assertEquals(0, page.getChildAt(0).getTop());
        assertEquals(100, page.getChildAt(1).getTop());
        assertEquals(200, page.getChildAt(2).getTop());
        assertEquals(100, page.getChildAt(3).getLeft());
        assertEquals(0, page.getChildAt(3).getTop());
        assertEquals(100, page.getChildAt(0).getWidth());
        assertEquals(100, page.getChildAt(0).getHeight());
    }

    @Test
    public void rowsFillTheHeightEvenly() throws Exception {
        ExtraKeysPageView page = new ExtraKeysPageView(context);
        page.setKeys(info("[['A','B'],['C','D'],['E','F']]"));
        layout(page, 200, 90);
        assertEquals(3, page.rowCount());
        assertEquals(30, page.getChildAt(0).getHeight());
        assertEquals(60, page.getChildAt(4).getTop());
        assertEquals(100, page.getChildAt(1).getLeft());
    }

    // ---- Touch.

    @Test
    public void aTapOnAPlainKeySendsItOnceOnRelease() {
        RecordingClient client = new RecordingClient();
        view.setClient(client);
        touch(cap(0), MotionEvent.ACTION_DOWN, 0, 0, view);
        assertTrue("nothing is sent on the way down", client.clicked.isEmpty());
        assertTrue(cap(0).glowLevel() > 0f);
        touch(cap(0), MotionEvent.ACTION_UP, 0, 0, view);
        assertEquals(1, client.clicked.size());
        assertEquals("ESC", client.clicked.get(0));
    }

    @Test
    public void aDeadKeyFiresNothing() {
        RecordingClient client = new RecordingClient();
        view.setClient(client);
        view.setKeyUsabilityPolicy(value -> false);
        touch(cap(0), MotionEvent.ACTION_DOWN, 0, 0, view);
        touch(cap(0), MotionEvent.ACTION_UP, 0, 0, view);
        assertTrue(client.clicked.isEmpty());
        assertEquals("a dead key shows no glow", 0f, cap(0).glowLevel(), 0f);
    }

    @Test
    public void aHeldRepeatKeyRepeatsAndTheReleaseDoesNotFireAgain() throws Exception {
        ExtraKeysPageView page = new ExtraKeysPageView(context);
        page.setKeys(info("[['UP','ESC']]"));
        layout(page, 200, 40);
        RecordingClient client = new RecordingClient();
        page.setClient(client);
        View up = page.getChildAt(0);
        touch(up, MotionEvent.ACTION_DOWN, 0, 0, page);
        ShadowLooper.idleMainLooper(1000, TimeUnit.MILLISECONDS);
        int whileHeld = client.clicked.size();
        assertTrue("a hold repeats: " + whileHeld, whileHeld >= 3);
        touch(up, MotionEvent.ACTION_UP, 0, 0, page);
        assertEquals(whileHeld, client.clicked.size());
        ShadowLooper.idleMainLooper(500, TimeUnit.MILLISECONDS);
        assertEquals("nothing runs after the release", whileHeld, client.clicked.size());
    }

    @Test
    public void aSwipeUpPastTheFireDistanceSendsTheSecondKeyAndShortOfItThePrimary() throws Exception {
        ExtraKeysPageView page = new ExtraKeysPageView(context);
        page.setKeys(info("[[{key: 'A', popup: 'B'}]]"));
        layout(page, 100, 40);
        RecordingClient client = new RecordingClient();
        page.setClient(client);
        View a = page.getChildAt(0);
        float fire = ExtraKeyPressPolicy.SWIPE_FIRE_DP * context.getResources().getDisplayMetrics().density;

        touch(a, MotionEvent.ACTION_DOWN, 0, 0, page);
        touch(a, MotionEvent.ACTION_MOVE, 0, -(fire + 6f), page);
        touch(a, MotionEvent.ACTION_UP, 0, -(fire + 6f), page);
        assertEquals(1, client.clicked.size());
        assertEquals("B", client.clicked.get(0));

        touch(a, MotionEvent.ACTION_DOWN, 0, 0, page);
        touch(a, MotionEvent.ACTION_MOVE, 0, -(fire / 2f), page);
        touch(a, MotionEvent.ACTION_UP, 0, -(fire / 2f), page);
        assertEquals(2, client.clicked.size());
        assertEquals("A", client.clicked.get(1));
    }

    @Test
    public void aCancelFiresNothingAndLetsTheGlowGo() {
        RecordingClient client = new RecordingClient();
        view.setClient(client);
        touch(cap(0), MotionEvent.ACTION_DOWN, 0, 0, view);
        touch(cap(0), MotionEvent.ACTION_CANCEL, 0, 0, view);
        assertTrue(client.clicked.isEmpty());
        ShadowLooper.idleMainLooper(400, TimeUnit.MILLISECONDS);
        assertEquals(0f, cap(0).glowLevel(), 0f);
    }

    @Test
    public void aSwipeUpToAModifierSecondKeyArmsItInsteadOfTypingItsName() throws Exception {
        ExtraKeysPageView page = new ExtraKeysPageView(context);
        page.setKeys(info("[[{key: 'ESC', popup: 'CTRL'}]]"));
        layout(page, 100, 40);
        ExtraKeyModifiers modifiers = new ExtraKeyModifiers();
        page.setModifiers(modifiers);
        RecordingClient client = new RecordingClient();
        page.setClient(client);
        View esc = page.getChildAt(0);
        float fire = ExtraKeyPressPolicy.SWIPE_FIRE_DP * context.getResources().getDisplayMetrics().density;
        touch(esc, MotionEvent.ACTION_DOWN, 0, 0, page);
        touch(esc, MotionEvent.ACTION_MOVE, 0, -(fire + 6f), page);
        touch(esc, MotionEvent.ACTION_UP, 0, -(fire + 6f), page);
        assertTrue(client.clicked.isEmpty());
        assertEquals(ExtraKeyModifiers.State.ARMED, modifiers.state(SpecialButton.CTRL));
    }

    @Test
    public void anAccessibilityClickDoesWhatATapDoes() throws Exception {
        ExtraKeysPageView page = new ExtraKeysPageView(context);
        page.setKeys(info("[['ESC','CTRL']]"));
        layout(page, 200, 40);
        ExtraKeyModifiers modifiers = new ExtraKeyModifiers();
        page.setModifiers(modifiers);
        RecordingClient client = new RecordingClient();
        page.setClient(client);
        ExtraKeyCapView esc = (ExtraKeyCapView) page.getChildAt(0);
        ExtraKeyCapView ctrl = (ExtraKeyCapView) page.getChildAt(1);
        assertTrue(esc.isClickable());
        assertTrue(esc.performAccessibilityAction(
            android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, null));
        assertEquals("ESC", client.clicked.get(0));
        assertTrue(ctrl.performAccessibilityAction(
            android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK, null));
        assertEquals(ExtraKeyModifiers.State.ARMED, modifiers.state(SpecialButton.CTRL));
        assertEquals(1, client.clicked.size());
        assertTrue(ctrl.performAccessibilityAction(
            android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK, null));
        assertEquals(ExtraKeyModifiers.State.OFF, modifiers.state(SpecialButton.CTRL));
    }

    // ---- Modifiers.

    @Test
    public void aModifierTappedOnOnePageShowsArmedOnTheOthers() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        ExtraKeysPageView first = new ExtraKeysPageView(context);
        ExtraKeysPageView second = new ExtraKeysPageView(context);
        first.setKeys(info("[['CTRL','A']]"));
        second.setKeys(info("[['B','CTRL']]"));
        ExtraKeyModifiers modifiers = new ExtraKeyModifiers();
        first.setModifiers(modifiers);
        second.setModifiers(modifiers);
        RecordingClient client = new RecordingClient();
        first.setClient(client);
        second.setClient(client);
        root.addView(first);
        root.addView(second);
        layout(first, 200, 40);
        layout(second, 200, 40);

        View ctrlOnSecond = second.getChildAt(1);
        touch(ctrlOnSecond, MotionEvent.ACTION_DOWN, 0, 0, second);
        touch(ctrlOnSecond, MotionEvent.ACTION_UP, 0, 0, second);

        assertEquals(ExtraKeyModifiers.State.ARMED, modifiers.state(SpecialButton.CTRL));
        assertEquals(ExtraKeyModifiers.State.ARMED,
            ((ExtraKeyCapView) first.getChildAt(0)).modifierState());
        assertEquals(ExtraKeyModifiers.State.ARMED, ((ExtraKeyCapView) ctrlOnSecond).modifierState());
        assertEquals(ExtraKeyModifiers.State.OFF,
            ((ExtraKeyCapView) first.getChildAt(1)).modifierState());
        assertTrue("a modifier never reaches the client", client.clicked.isEmpty());

        // Consuming it, as the terminal does on the next key, clears it everywhere.
        assertTrue(modifiers.read(SpecialButton.CTRL, true));
        assertEquals(ExtraKeyModifiers.State.OFF,
            ((ExtraKeyCapView) first.getChildAt(0)).modifierState());
    }

    // ---- Helpers.

    private ExtraKeysInfo info(String rows) throws Exception {
        return new ExtraKeysInfo(rows, ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
            ExtraKeysConstants.CONTROL_CHARS_ALIASES);
    }

    private ExtraKeyCapView cap(int index) {
        return (ExtraKeyCapView) view.getChildAt(index);
    }

    private static void layout(View page, int w, int h) {
        page.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        page.layout(0, 0, w, h);
    }

    /** Sends an event at the cap's centre (plus an offset) through the cap's page. */
    private static void touch(View cap, int action, float dx, float dy, ExtraKeysPageView page) {
        long now = SystemClock.uptimeMillis();
        float x = cap.getLeft() + cap.getWidth() / 2f + dx;
        float y = cap.getTop() + cap.getHeight() / 2f + dy;
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        page.dispatchTouchEvent(event);
        event.recycle();
    }

    private void showing(final String focusedKeyValue) {
        view.setPlaceSwitchPolicy(value -> {
            if (!value.startsWith("tool:wall.")) return ExtraKeysPageView.PlaceFocus.NOT_A_PLACE;
            return value.equals(focusedKeyValue)
                ? ExtraKeysPageView.PlaceFocus.FOCUSED
                : ExtraKeysPageView.PlaceFocus.UNFOCUSED;
        });
    }

    private int vivid(int switchIndex) {
        return PlaceSwitchGlyph.vividRow(new int[] {
            ExtraKeyColorRole.PRIMARY.background(context),
            ExtraKeyColorRole.SECONDARY.background(context),
            ExtraKeyColorRole.TERTIARY.background(context)
        }, PlaceSwitchGlyph.isDarkGlass(view.getButtonTextColor()))[switchIndex];
    }

    private int dimmed(int switchIndex) {
        return (145 << 24) | (vivid(switchIndex) & 0x00FFFFFF);
    }

    private static int opaque(int color) {
        return color | 0xFF000000;
    }

    @Test
    public void thePageSwitcherCapIsBuiltWithTheCurrentGlyphAndNeverItsLabel() throws Exception {
        com.termux.app.wall.pageswitch.PageGlyphSource.install(() ->
            new com.termux.app.wall.pageswitch.PageGlyphSource.Glyph(
                com.termux.app.page.PageKind.HOME, 2));
        try {
            ExtraKeysPageView page = new ExtraKeysPageView(context);
            page.setKeys(info("[['ESC', 'tool:app.page_switcher']]"));
            layout(page, 500, 40);
            ExtraKeyCapView switcher = (ExtraKeyCapView) page.getChildAt(1);
            assertEquals(com.termux.app.page.PageKind.HOME,
                switcher.pageGlyphKind());
            assertEquals(2, switcher.pageGlyphNumber());
            assertEquals("", switcher.getText().toString());
            // A key-set swap or a relabel leaves it the same.
            page.setKeys(info("[['TAB', 'tool:app.page_switcher']]"));
            switcher = (ExtraKeyCapView) page.getChildAt(1);
            assertEquals(2, switcher.pageGlyphNumber());
            assertEquals("", switcher.getText().toString());
            page.setButtonTextAllCaps(false);
            assertEquals("", switcher.getText().toString());
        } finally {
            com.termux.app.wall.pageswitch.PageGlyphSource.install(null);
        }
    }

    private static int centerPixel(Drawable drawable) {
        int size = 40;
        drawable.setBounds(0, 0, size, size);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        drawable.draw(new Canvas(bitmap));
        return bitmap.getPixel(size / 2, size / 2);
    }

    private static GradientDrawable shapeOf(Drawable background) {
        Drawable drawable = background instanceof InsetDrawable
            ? ((InsetDrawable) background).getDrawable() : background;
        return (GradientDrawable) drawable;
    }

    private static final class RecordingClient implements ExtraKeysClient {
        final List<String> clicked = new ArrayList<>();

        @Override
        public void onExtraKeyClick(View view, ExtraKeyButton key) {
            clicked.add(key.getKey());
        }

        @Override
        public boolean performExtraKeyHapticFeedback(View view, ExtraKeyButton key) {
            return true;
        }
    }
}
