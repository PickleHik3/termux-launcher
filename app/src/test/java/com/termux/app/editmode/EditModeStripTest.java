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
                if (barStand != null) barStand.setTranslationY(-px);
            }
            @Override public boolean raiseBar() { return false; }
            @Override public void lowerBar() { }
            @Override public void onBarTap(@Nullable Runnable tap) { }
            @Nullable @Override public EditSession.Keyed windows() { return windows; }
            @Override public void onWindowTap(@Nullable Runnable tap) { }
            @NonNull @Override public List<String[]> installedFonts() { return Collections.emptyList(); }
            @Override public void openFonts() { }
        });
    }

    /** The launcher keyboard's view, on screen at the layer's bottom while {@link #keyboardUp}. */
    @Nullable private View keys;
    private boolean keyboardUp;

    /** The host as the activity's is: the Windows card's and Copy as config's sub-interfaces. */
    private interface TestHost extends EditModeController.WindowsHost, EditModeController.ConfigHost { }

    /** The ids Copy as config was asked for. */
    private final List<String> copied = new java.util.ArrayList<>();

    /** Whether the host has a control bar; the round-1 tests ran with none. */
    private boolean withBar;
    /** The control bar's view, on screen at the layer's bottom when a test puts it there. */
    @Nullable private View barStand;
    private int lifted;

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

    @Test public void aRowWithNoRegistryIdOffersNothing() {
        controller.open(Card.WINDOWS);
        idle();
        TextView font = labelled(layer, label(com.termux.R.string.edit_mode_font));
        if (font != null) assertFalse(font.isLongClickable());
    }
}
