package com.termux.app.editmode;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.editmode.EditModeController.Card;
import com.termux.app.look.MemoryLookStore;
import com.termux.app.look.StoredTerlLookStore;
import com.termux.app.look.TerlLookStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

import java.util.Collections;
import java.util.List;

/**
 * Edit mode's strip switches cards (terl-integration-1): with any card open its chips still stand,
 * and each opens its own card over the one open, lit while it is. Round 1 found the chips gone
 * beside an open card, so every tap where they had stood left the Home card up.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w411dp-h891dp")
public class EditModeStripTest {

    private EditModeController controller;
    private FrameLayout layer;

    @Before public void setUp() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.google.android.material.R.style.Theme_MaterialComponents_DayNight);
        layer = new FrameLayout(activity);
        activity.setContentView(layer);
        TerlLookStore looks = new StoredTerlLookStore(new MemoryLookStore());
        controller = new EditModeController(new TestHost() {
            @Override public void copyAsConfig(@NonNull String id) { copied.add(id); }
            @NonNull @Override public Context context() { return activity; }
            @Nullable @Override public ViewGroup editorLayer() { return layer; }
            @NonNull @Override public TerlLookStore looks() { return looks; }
            @Nullable @Override public View keyboardView() { return keyboardUp ? keys : null; }
            @Override public boolean isKeyboardShown() { return keyboardUp; }
            @Override public boolean showKeyboard() { return false; }
            @Override public void hideKeyboard() { }
            @Nullable @Override public EditSession.Launcher launcher() { return null; }
            @NonNull @Override public List<com.termux.app.launcher.data.IconPackChoices.Entry> iconPacks() {
                return Collections.emptyList();
            }
            @Override public void warmIconPack(@NonNull String pack) { }
            @Nullable @Override public EditSession.Wallpaper wallpaper() { return null; }
            @Nullable @Override public EditSession.Home home() { return null; }
            @Override public void onWallpaperTap(@Nullable Runnable tap) { }
            @Nullable @Override public LegibilityMeter.Reading legibility() { return null; }
            @Override public void onLeft() { }
            @Nullable @Override public EditSession.Bar bar() { return withBar ? bar : null; }
            @Nullable @Override public View barView() { return barStand; }
            @Override public void liftBar(int px) {
                lifted = px;
                if (barStand != null) barStand.setTranslationY(ride - px);
            }
            @Override public boolean raiseBar() { return false; }
            @Override public void lowerBar() { }
            @Override public void onBarTap(@Nullable Runnable tap) { }
            @Nullable @Override public EditSession.Keyed windows() { return windows; }
            @Override public void onWindowTap(@Nullable Runnable tap) { }
            @NonNull @Override public List<String[]> installedFonts() { return Collections.emptyList(); }
            @Override public void openFonts() { }
            @NonNull @Override public List<EditOutlines.Window> windowsOnScreen() { return onScreen; }
            @Nullable @Override public EditOutlines.Box homeOnScreen() { return homeOnScreen; }
            @Override public void onWindowsAtRest(@Nullable Runnable atRest) { windowsAtRest = atRest; }
        });
    }

    /** The windows the host reports on screen (screen px), and Home's page when it is in front. */
    @NonNull private List<EditOutlines.Window> onScreen = Collections.emptyList();
    @Nullable private EditOutlines.Box homeOnScreen;
    /** What edit mode asked to hear when the windows come to rest. */
    @Nullable private Runnable windowsAtRest;

    /** The launcher keyboard's view, on screen at the layer's bottom while {@link #keyboardUp}. */
    @Nullable private View keys;
    private boolean keyboardUp;

    /** The host as the activity's is: the Windows card's and Copy as config's sub-interfaces. */
    private interface TestHost extends EditModeController.WindowsHost, EditModeController.ConfigHost,
        EditModeController.OutlineHost { }

    /** The ids Copy as config was asked for. */
    private final List<String> copied = new java.util.ArrayList<>();

    /** Whether the host has a control bar; the round-1 tests ran with none. */
    private boolean withBar;
    /** The control bar's view, on screen at the layer's bottom when a test puts it there. */
    @Nullable private View barStand;
    private int lifted;
    /** The keyboard's ride on the bar, a translation as ControlBar.ride writes it. */
    private float ride;

    private final EditSession.Bar bar = new EditSession.Bar() {
        int margin = 6;
        boolean reading = true;
        String rail = RAIL_RIGHT;
        @Override public int marginDp() { return margin; }
        @Override public void setMarginDp(int dp) { margin = dp; }
        @Override public boolean reading() { return reading; }
        @Override public void setReading(boolean on) { reading = on; }
        @NonNull @Override public String railSide() { return rail; }
        @Override public void setRailSide(@NonNull String side) { rail = side; }
        @NonNull @Override public List<String> toolOptions(@NonNull String place) {
            return java.util.Arrays.asList("keyboard", "split");
        }
        @NonNull @Override public String tools(@NonNull String place) { return "keyboard,split"; }
        @Override public void setTools(@NonNull String place, @NonNull String tools) { }
    };

    private final EditSession.Keyed windows = new EditSession.Keyed() {
        final java.util.Map<String, Object> values = new java.util.HashMap<>();
        {
            values.put(GAP, 8);
            values.put(UNFOCUSED_DIM, 0);
            values.put(RIM_COLOUR, "accent");
            values.put(CONTRAST, "default");
            values.put(EFFECT, "none");
            values.put(FONT, "");
            values.put(CURSOR_TRAIL, "off:comet");
        }
        @Nullable @Override public Object get(@NonNull String key) { return values.get(key); }
        @Override public void set(@NonNull String key, @NonNull Object value) { values.put(key, value); }
    };

    private void idle() {
        layer.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY));
        layer.layout(0, 0, 1080, 2340);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    /** The strip's chip that reads {@code label}, or null when none shows. */
    @Nullable
    private TextView chip(@NonNull String label) {
        return find(layer, label);
    }

    @Nullable
    private static TextView find(@NonNull View view, @NonNull String label) {
        // A hidden card's rows are not on screen (the Text tab's cursor Bar is not the strip's Bar).
        if (view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())
                && view.isClickable() && view.getVisibility() == View.VISIBLE) {
            return (TextView) view;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = group.getChildCount() - 1; i >= 0; i--) {
            TextView found = find(group.getChildAt(i), label);
            if (found != null) return found;
        }
        return null;
    }

    private String label(int res) {
        return layer.getContext().getString(res);
    }

    @Test public void fromTheHomeCardEachChipOpensItsOwnCard() {
        controller.open(Card.HOME);
        idle();
        assertEquals(Card.HOME, controller.openCard());

        TextView keyboard = chip(label(com.termux.R.string.edit_mode_card_keyboard));
        assertNotNull("the Keyboard chip stands beside the open Home card", keyboard);
        keyboard.performClick();
        idle();
        assertEquals(Card.KEYBOARD, controller.openCard());

        chip(label(com.termux.R.string.edit_mode_card_global)).performClick();
        idle();
        assertEquals(Card.GLOBAL, controller.openCard());

        chip(label(com.termux.R.string.edit_mode_wallpaper_chip)).performClick();
        idle();
        assertEquals(Card.WALLPAPER, controller.openCard());

        chip(label(com.termux.R.string.edit_mode_home_chip)).performClick();
        idle();
        assertEquals(Card.HOME, controller.openCard());
    }

    @Test public void theOpenCardsChipIsLitAndTheOthersAreNot() {
        controller.open(Card.HOME);
        idle();
        chip(label(com.termux.R.string.edit_mode_card_global)).performClick();
        idle();
        assertTrue(chip(label(com.termux.R.string.edit_mode_card_global)).isSelected());
        assertFalse(chip(label(com.termux.R.string.edit_mode_home_chip)).isSelected());
    }

    @Test public void undoRevertAndDoneStayInReachWithACardOpen() {
        controller.open(Card.HOME);
        idle();
        TextView done = chip(label(com.termux.R.string.edit_mode_done));
        assertNotNull(done);
        assertTrue("Done is on screen", done.getWidth() > 0 && done.getRight() <= 1080);
        assertNotNull(chip(label(com.termux.R.string.edit_mode_undo)));
        done.performClick();
        assertFalse(controller.isOpen());
        assertNull(controller.openCard());
    }

    @Test public void withoutABarThereIsNoBarChip() {
        controller.open(null);
        idle();
        assertNull(chip(label(com.termux.R.string.edit_mode_bar_chip)));
    }

    @Test public void everyCardsChipStandsWholeFromEveryDoor() {
        // Round 2: from Overview's Edit the chips were crushed to "Keyboard" and a clipped "Al",
        // the count printed over them, and Bar, Wallpaper, Home and Windows were out of reach.
        withBar = true;
        int[] chips = {com.termux.R.string.edit_mode_card_keyboard, com.termux.R.string.edit_mode_card_global,
            com.termux.R.string.edit_mode_wallpaper_chip, com.termux.R.string.edit_mode_home_chip,
            com.termux.R.string.edit_mode_bar_chip, com.termux.R.string.edit_mode_card_windows};
        // Overview's Edit (no card), a hold on empty Home, a window menu's Edit look.
        for (Card door : new Card[] {null, Card.HOME, Card.WINDOWS}) {
            controller.open(door);
            idle();
            TextView done = chip(label(com.termux.R.string.edit_mode_done));
            assertNotNull(done);
            View strip = (View) done.getParent();
            for (int res : chips) {
                TextView chip = chip(label(res));
                assertNotNull(label(res) + " from " + door, chip);
                int natural = (int) Math.ceil(chip.getPaint().measureText(chip.getText().toString()))
                    + chip.getPaddingLeft() + chip.getPaddingRight();
                assertTrue(label(res) + " whole from " + door + ": " + chip.getWidth() + " < " + natural,
                    chip.getWidth() >= natural);
                assertTrue(label(res) + " inside the strip", chip.getLeft() >= 0 && chip.getRight() <= strip.getWidth());
                for (int other : new int[] {com.termux.R.string.edit_mode_undo, com.termux.R.string.edit_mode_revert,
                        com.termux.R.string.edit_mode_done}) {
                    TextView button = chip(label(other));
                    assertNotNull(button);
                    boolean apart = chip.getRight() <= button.getLeft() || button.getRight() <= chip.getLeft()
                        || chip.getBottom() <= button.getTop() || button.getBottom() <= chip.getTop();
                    assertTrue(label(res) + " clear of " + label(other), apart);
                }
                chip.performClick();
                idle();
            }
            controller.leave();
            idle();
        }
    }

    @Test public void theKeyboardChipStandsWhenEditModeOpensWithTheKeyboardUp() {
        // Review of round 2: with the keyboard already up the Keyboard card was out of the strip.
        keys = new View(layer.getContext());
        layer.addView(keys, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 700,
            android.view.Gravity.BOTTOM));
        keyboardUp = true;
        controller.open(Card.HOME);
        idle();

        TextView keyboard = chip(label(com.termux.R.string.edit_mode_card_keyboard));
        assertNotNull("the Keyboard chip stands with the keyboard up", keyboard);
        keyboard.performClick();
        idle();
        assertEquals(Card.KEYBOARD, controller.openCard());
    }

    @Test public void chipsThatDoNotFitBesideTheButtonsTakeLinesAbove() {
        EditStripChips.Flow flow = EditStripChips.flow(360, new int[] {90, 80, 90, 60, 50, 80}, 200, 80, 132);

        assertEquals(0, flow.chipLine[0]);
        assertEquals(0, flow.chipLine[3]);
        assertEquals("the fifth chip starts a line", 1, flow.chipLine[4]);
        assertEquals(0, flow.chipX[4]);
        assertEquals("the buttons fit beside the second line's chips", 1, flow.actionsLine);
        assertEquals("the count starts after them", 130, flow.countX);
        assertEquals("and takes only what the buttons leave", 30, flow.countWidth);

        EditStripChips.Flow full = EditStripChips.flow(360, new int[] {90, 80, 90, 60, 50, 80, 100}, 200, 80, 132);
        assertEquals("no room beside the chips: the buttons take a line of their own", 2, full.actionsLine);
        assertEquals(0, full.countX);
        assertEquals(80, full.countWidth);
    }

    @Test public void theCountYieldsToChipsThatShareTheButtonsLine() {
        EditStripChips.Flow fits = EditStripChips.flow(400, new int[] {80, 60}, 200, 80, 132);
        assertEquals("one line", 0, fits.actionsLine);
        assertEquals(140, fits.countX);
        assertEquals("only what is left", 60, fits.countWidth);

        EditStripChips.Flow none = EditStripChips.flow(400, new int[] {120, 80}, 200, 80, 132);
        assertEquals(0, none.actionsLine);
        assertEquals("nothing left: no count", 0, none.countWidth);

        EditStripChips.Flow capped = EditStripChips.flow(800, new int[] {80}, 200, 400, 132);
        assertEquals(132, capped.countWidth);
    }

    @Test public void aChipWiderThanTheRowIsNarrowedToIt() {
        EditStripChips.Flow flow = EditStripChips.flow(100, new int[] {150}, 90, 10, 132);
        assertEquals(100, flow.chipWidth[0]);
        assertEquals(1, flow.actionsLine);
    }

    @Test public void theChipsRuleStandsBesideAnyOpenCard() {
        for (Card open : new Card[] {null, Card.HOME, Card.GLOBAL, Card.WALLPAPER, Card.BAR, Card.WINDOWS}) {
            assertTrue(EditStripChips.shown(Card.GLOBAL, true));
            assertTrue(EditStripChips.shown(Card.HOME, true));
        }
        // Updated for the review of round 2: the Keyboard chip stands with the keyboard up too.
        assertTrue(EditStripChips.shown(Card.KEYBOARD, true));
        assertFalse(EditStripChips.shown(Card.BAR, false));
        assertTrue(EditStripChips.lit(Card.HOME, Card.HOME));
        assertFalse(EditStripChips.lit(Card.HOME, Card.GLOBAL));
        assertFalse(EditStripChips.lit(Card.HOME, null));
    }

    @Nullable
    private static EditStripLayout strip(@NonNull View view) {
        if (view instanceof EditStripLayout) return (EditStripLayout) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            EditStripLayout found = strip(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private static int screenTop(@NonNull View view) {
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        return at[1];
    }

    /** Round 3: the strip jumped up when the Bar card opened; it keeps the bottom and the bar stands above it. */
    @Test public void theStripKeepsTheBottomAndTheBarStandsAboveItWhenItsCardOpens() {
        withBar = true;
        barStand = new View(layer.getContext());
        layer.addView(barStand, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120,
            android.view.Gravity.BOTTOM));
        controller.open(null);
        idle();
        idle();
        EditStripLayout strip = strip(layer);
        assertNotNull(strip);
        int stripTop = screenTop(strip);
        assertTrue("the bar is lifted clear of the strip", lifted > 0);
        assertTrue(screenTop(barStand) + barStand.getHeight() <= stripTop);

        chip(label(com.termux.R.string.edit_mode_bar_chip)).performClick();
        idle();
        idle();
        assertEquals(Card.BAR, controller.openCard());
        assertEquals("the strip stays where it was", stripTop, screenTop(strip));
        assertTrue(screenTop(barStand) + barStand.getHeight() <= stripTop);

        controller.leave();
        assertEquals("leaving puts the bar back", 0, lifted);
    }

    /** Round 3: the strip's chips are pills, the open card's lit in the accent. */
    @Test public void theStripsChipsArePillsAndTheOpenCardsIsLit() {
        controller.open(Card.HOME);
        idle();
        TextView home = chip(label(com.termux.R.string.edit_mode_home_chip));
        TextView global = chip(label(com.termux.R.string.edit_mode_card_global));
        assertTrue(home.getBackground() instanceof android.graphics.drawable.RippleDrawable);
        assertTrue(global.getBackground() instanceof android.graphics.drawable.RippleDrawable);
        assertTrue(home.isSelected());
        assertTrue("lit and plain chips read in different inks",
            home.getCurrentTextColor() != global.getCurrentTextColor());
    }

    @Nullable
    private static TextView labelled(@NonNull View view, @NonNull String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = labelled(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

    /** #454's remainder: a hold on a card's row offers Copy as config, the control's registry id. */
    @Test public void aHoldOnACardsRowOffersCopyAsConfig() {
        withBar = true;
        controller.open(Card.BAR);
        idle();
        TextView margin = labelled(layer, label(com.termux.R.string.edit_mode_margin));
        assertNotNull(margin);
        View head = (View) margin.getParent();
        assertTrue(head.performLongClick());
        android.widget.PopupMenu menu = controller.copyMenu();
        assertNotNull(menu);
        assertEquals(label(com.termux.R.string.terl_settings_copy_as_config),
            menu.getMenu().getItem(0).getTitle().toString());
        assertTrue(menu.getMenu().performIdentifierAction(menu.getMenu().getItem(0).getItemId(), 0));
        assertEquals(java.util.Collections.singletonList("bar.margin"), copied);
    }

    /** Review of round 3: the menu was kept after edit mode left, holding the closed card. */
    @Test public void leavingEditModeTakesTheCopyMenuWithIt() {
        withBar = true;
        controller.open(Card.BAR);
        idle();
        View head = (View) labelled(layer, label(com.termux.R.string.edit_mode_margin)).getParent();
        assertTrue(head.performLongClick());
        assertNotNull(controller.copyMenu());
        controller.leave();
        assertNull(controller.copyMenu());

        controller.open(Card.BAR);
        idle();
        head = (View) labelled(layer, label(com.termux.R.string.edit_mode_margin)).getParent();
        assertTrue(head.performLongClick());
        controller.carry();
        assertNull("a recreate takes it too", controller.copyMenu());
    }

    @Test public void aRowWithNoRegistryIdOffersNothing() {
        controller.open(Card.WINDOWS);
        idle();
        TextView font = labelled(layer, label(com.termux.R.string.edit_mode_font));
        assertNotNull("the Windows card's Font row is there", font);
        assertFalse(font.isLongClickable());
        TextView gap = labelled(layer, label(com.termux.R.string.edit_mode_gap));
        assertNotNull(gap);
        assertTrue("a row with an id offers it", ((View) gap.getParent()).isLongClickable());
    }

    /** Round 4: the pill's insets took the chips' padding: they ran together and Done was a circle. */
    @Test public void everyStripChipKeepsRoomAroundItsTextInsideItsPill() {
        controller.open(Card.HOME);
        idle();
        float density = layer.getResources().getDisplayMetrics().density;
        int room = Math.round((EditModeController.STRIP_CHIP_GAP_DP + EditModeController.STRIP_CHIP_PAD_DP) * density);
        for (int res : new int[] {com.termux.R.string.edit_mode_home_chip, com.termux.R.string.edit_mode_card_global,
                com.termux.R.string.edit_mode_done, com.termux.R.string.edit_mode_undo}) {
            TextView chip = chip(label(res));
            assertNotNull(label(res), chip);
            assertEquals(label(res), room, chip.getPaddingLeft());
            assertEquals(label(res), room, chip.getPaddingRight());
        }
    }

    @Test public void buttonsWiderThanTheRowShareItAndFitTheyStandAsTheyAre() {
        assertEquals(0, EditStripChips.buttonShare(300, 240, 3));
        assertEquals(100, EditStripChips.buttonShare(300, 360, 3));
        assertEquals(0, EditStripChips.buttonShare(300, 360, 0));
    }

    /** Review of round 3: the lift was read off the bar mid-ride; a ride never changes it. */
    @Test public void theBarsRideNeverChangesItsLiftAboveTheStrip() {
        withBar = true;
        barStand = new View(layer.getContext());
        layer.addView(barStand, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120,
            android.view.Gravity.BOTTOM));
        controller.open(null);
        idle();
        idle();
        int settled = lifted;
        assertTrue(settled > 0);

        // The keyboard comes up: the bar rides up by a translation, mid-slide, and a layout runs.
        ride = -400f;
        barStand.setTranslationY(ride - lifted);
        layer.requestLayout();
        idle();
        idle();
        assertEquals("the lift is the laid-out bar's, whatever the ride", settled, lifted);
    }

    // ---------------------------------------------------------------- live labels (round 5)

    @Nullable
    private static EditOutlinesView outlinesView(@NonNull View view) {
        if (view instanceof EditOutlinesView) return (EditOutlinesView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            EditOutlinesView found = outlinesView(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    @Nullable
    private static EditOutlines.Outline outline(@NonNull EditOutlinesView view, @NonNull EditOutlines.Surface surface) {
        for (EditOutlines.Outline outline : view.outlines()) if (outline.surface == surface) return outline;
        return null;
    }

    /** A window at {@code left..right, top..bottom} of the layer, reported in screen px. */
    @NonNull
    private EditOutlines.Window windowAt(int left, int top, int right, int bottom, @NonNull String name) {
        int[] at = new int[2];
        layer.getLocationOnScreen(at);
        return new EditOutlines.Window(new EditOutlines.Box(at[0] + left, at[1] + top, at[0] + right,
            at[1] + bottom), 30f, name);
    }

    @Test
    public void editModeLabelsTheWorkspacesWindowsTheFrameAndTheBar() {
        withBar = true;
        barStand = new View(layer.getContext());
        layer.addView(barStand, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120,
            android.view.Gravity.BOTTOM));
        idle();
        onScreen = java.util.Arrays.asList(windowAt(20, 100, 200, 700, "fish"),
            windowAt(210, 100, 400, 700, "vim"));
        controller.open(null);
        idle();
        EditOutlinesView view = outlinesView(layer);
        assertNotNull("edit mode draws its live labels", view);
        EditOutlines.Outline frame = outline(view, EditOutlines.Surface.WALLPAPER);
        assertNotNull(frame);
        assertEquals(label(com.termux.R.string.edit_mode_wallpaper_chip), frame.label);
        int windows = 0;
        for (EditOutlines.Outline outline : view.outlines()) {
            if (outline.surface != EditOutlines.Surface.WINDOW) continue;
            windows++;
            if (outline.label.equals("fish")) assertEquals(new EditOutlines.Box(20, 100, 200, 700), outline.box);
        }
        assertEquals(2, windows);
        EditOutlines.Outline bar = outline(view, EditOutlines.Surface.BAR);
        assertNotNull(bar);
        assertEquals(label(com.termux.R.string.edit_mode_card_control_bar), bar.label);
        assertNull("no Home on a Workspace", outline(view, EditOutlines.Surface.HOME));
    }

    @Test
    public void theOpenCardsSurfaceIsLitAndTheWallpaperChipOpensItsCard() {
        int[] at = new int[2];
        layer.getLocationOnScreen(at);
        homeOnScreen = new EditOutlines.Box(at[0], at[1], at[0] + 400, at[1] + 700);
        controller.open(null);
        idle();
        EditOutlinesView view = outlinesView(layer);
        assertNotNull(view);
        EditOutlines.Outline home = outline(view, EditOutlines.Surface.HOME);
        assertNotNull(home);
        assertFalse(home.lit);
        controller.openCard(Card.HOME);
        idle();
        assertTrue("Home's card lights Home's outline", outline(view, EditOutlines.Surface.HOME).lit);
        controller.closeCard();
        idle();

        // The chip is drawn, then tapped.
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(Math.max(1, view.getWidth()),
            Math.max(1, view.getHeight()),
            android.graphics.Bitmap.Config.ARGB_8888);
        view.draw(new android.graphics.Canvas(bitmap));
        android.graphics.RectF chip = view.chipRect();
        assertFalse("the Wallpaper chip is drawn", chip.isEmpty());
        long now = android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent down = android.view.MotionEvent.obtain(now, now,
            android.view.MotionEvent.ACTION_DOWN, chip.centerX(), chip.centerY(), 0);
        android.view.MotionEvent up = android.view.MotionEvent.obtain(now, now + 50,
            android.view.MotionEvent.ACTION_UP, chip.centerX(), chip.centerY(), 0);
        assertTrue(view.onTouchEvent(down));
        view.onTouchEvent(up);
        assertEquals(Card.WALLPAPER, controller.openCard());
        // Away from the chip it takes no touch: the scene under it keeps its own.
        android.view.MotionEvent elsewhere = android.view.MotionEvent.obtain(now, now,
            android.view.MotionEvent.ACTION_DOWN, 200f, 400f, 0);
        assertFalse(view.onTouchEvent(elsewhere));
    }

    /** Review P2-3: a Workspace scrolled with no layout after it; at rest the outlines follow. */
    @Test
    public void theOutlinesFollowTheWindowsWhenTheyComeToRest() {
        onScreen = java.util.Arrays.asList(windowAt(20, 100, 200, 700, "fish"));
        controller.open(null);
        idle();
        EditOutlinesView view = outlinesView(layer);
        assertNotNull(view);
        assertNotNull("edit mode listens for the windows at rest", windowsAtRest);
        // The columns scrolled: the window stands elsewhere, and nothing is laid out.
        onScreen = java.util.Arrays.asList(windowAt(120, 100, 300, 700, "fish"));
        windowsAtRest.run();
        EditOutlines.Outline fish = null;
        for (EditOutlines.Outline outline : view.outlines()) if ("fish".equals(outline.label)) fish = outline;
        assertNotNull(fish);
        assertEquals(new EditOutlines.Box(120, 100, 300, 700), fish.box);
        controller.leave();
        assertNull("leaving stops listening", windowsAtRest);
    }
}
