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
        controller = new EditModeController(new EditModeController.Host() {
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
            @Nullable @Override public EditSession.Wallpaper wallpaper() { return null; }
            @Nullable @Override public EditSession.Home home() { return null; }
            @Override public void onWallpaperTap(@Nullable Runnable tap) { }
            @Nullable @Override public LegibilityMeter.Reading legibility() { return null; }
            @Override public void onLeft() { }
        });
    }

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

    @Test public void theChipsRuleStandsBesideAnyOpenCard() {
        for (Card open : new Card[] {null, Card.HOME, Card.GLOBAL, Card.WALLPAPER, Card.BAR, Card.WINDOWS}) {
            assertTrue(EditStripChips.shown(Card.GLOBAL, open, false, true));
            assertTrue(EditStripChips.shown(Card.HOME, open, false, true));
        }
        assertFalse("the keyboard's outline is on screen", EditStripChips.shown(Card.KEYBOARD, null, false, true));
        assertTrue("its own card brought the keyboard up", EditStripChips.shown(Card.KEYBOARD, Card.KEYBOARD, false, true));
        assertFalse(EditStripChips.shown(Card.BAR, Card.HOME, true, false));
        assertTrue(EditStripChips.lit(Card.HOME, Card.HOME));
        assertFalse(EditStripChips.lit(Card.HOME, Card.GLOBAL));
        assertFalse(EditStripChips.lit(Card.HOME, null));
    }
}
