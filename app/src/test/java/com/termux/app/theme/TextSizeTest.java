package com.termux.app.theme;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.TextView;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The shared Text size (Appearance › Base): one percent on the terminal font, the key legends and
 * the chrome labels, each drawn at its own size times it. An install that has never touched the
 * slider reads the default, 100, which is the identity, so it keeps every size it had: pong's
 * 18 px terminal font (about 7 dp at 2.625) among them.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "420dpi")
public class TextSizeTest {

    private SharedPreferences store;
    private TermuxAppSharedPreferences preferences;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        store = context.getSharedPreferences("text-size-test", Context.MODE_PRIVATE);
        store.edit().clear().commit();
        preferences = new TermuxAppSharedPreferences(context, store, null);
        TextScale.set(1f);
    }

    @After
    public void tearDown() {
        TextScale.set(1f);
    }

    /** The start-up sequence TermuxApplication runs over the store. */
    private void launch() {
        preferences.migrateSurfaceInheritance();
        preferences.migrateLayoutStyle();
    }

    @Test
    public void anExistingInstallKeepsEverySizeItHad() {
        assertEquals("pong's density", 2.625f,
            RuntimeEnvironment.getApplication().getResources().getDisplayMetrics().density, 0f);
        // An install from before the slider: pong's terminal font, a pinned scratchpad size, Home's
        // own label size and a keyboard height, and no Text size.
        store.edit()
            .putString(TERMUX_APP.KEY_FONTSIZE, "18")
            .putString(TERMUX_APP.KEY_FONTSIZE + "_scratchpad", "24")
            .putString("app_launcher_home_label_size", "large")
            .putFloat(TERMUX_APP.KEY_IN_APP_KEYBOARD_HEIGHT_SCALE, 1.2f)
            .putInt(TERMUX_APP.KEY_LOG_LEVEL, 1)
            .commit();

        launch();

        assertFalse("nothing written for the new slider",
            store.contains(TERMUX_APP.KEY_TEXT_SIZE_PERCENT));
        assertEquals(TERMUX_APP.DEFAULT_TEXT_SIZE_PERCENT, preferences.getTextSizePercent());
        assertEquals(1f, preferences.getTextSizeScale(), 0f);
        // The terminal: 18 px, about 7 dp, exactly as before.
        assertEquals(18, preferences.getFontSize());
        assertEquals(18, preferences.textScaledFontSize(preferences.getFontSize()));
        assertEquals(7, Math.round(preferences.textScaledFontSize(preferences.getFontSize())
            / 2.625f));
        // The scratchpad and any pane's own zoom.
        assertEquals(24, preferences.textScaledFontSize(preferences.getScratchpadFontSize()));
        assertEquals(30, preferences.textScaledFontSize(30));
        // The chrome and the key legends: a factor of exactly one.
        assertFalse("the chrome's factor does not move", TextScale.sync(preferences));
        assertEquals(1f, TextScale.get(), 0f);
        assertEquals(11f, TextScale.sp(11f), 0f);
        // Home's own label size is untouched; it stays the offset it was.
        assertEquals("large", store.getString("app_launcher_home_label_size", null));
    }

    @Test
    public void aFreshInstallReadsTheSameIdentity() {
        launch();
        assertEquals(TERMUX_APP.DEFAULT_TEXT_SIZE_PERCENT, preferences.getTextSizePercent());
        int own = preferences.getFontSize();
        assertEquals(own, preferences.textScaledFontSize(own));
    }

    @Test
    public void everyOwnSizeMovesByTheSharedSize() {
        store.edit().putString(TERMUX_APP.KEY_FONTSIZE, "18").commit();
        preferences.setTextSizePercent(150);
        assertEquals(27, preferences.textScaledFontSize(preferences.getFontSize()));
        assertEquals("a pane's zoom moves with it", 45, preferences.textScaledFontSize(30));
        assertEquals("the stored own size stays", 18, preferences.getFontSize());

        preferences.setTextSizePercent(80);
        assertEquals(14, preferences.textScaledFontSize(18));
        // Held inside the display's limits: 4 dp is 10 px here.
        assertEquals(10, preferences.textScaledFontSize(10));

        assertTrue(TextScale.sync(preferences));
        assertEquals(0.8f, TextScale.get(), 1e-6f);
        assertEquals(8.8f, TextScale.sp(11f), 1e-4f);
    }

    @Test
    public void theStoredSizeStaysInItsRange() {
        preferences.setTextSizePercent(300);
        assertEquals(TERMUX_APP.MAX_TEXT_SIZE_PERCENT, preferences.getTextSizePercent());
        preferences.setTextSizePercent(10);
        assertEquals(TERMUX_APP.MIN_TEXT_SIZE_PERCENT, preferences.getTextSizePercent());
        store.edit().putInt(TERMUX_APP.KEY_TEXT_SIZE_PERCENT, 999).commit();
        assertEquals(TERMUX_APP.MAX_TEXT_SIZE_PERCENT, preferences.getTextSizePercent());
    }

    @Test
    public void aLabelTakesTheSharedSizeOnlyWhenItMoves() {
        TextView label = new TextView(RuntimeEnvironment.getApplication());
        float density = label.getResources().getDisplayMetrics().scaledDensity;
        TextScale.apply(label, 10f);
        assertEquals(10f * density, label.getTextSize(), 0.01f);

        assertTrue(TextScale.set(1.2f));
        assertFalse("the same factor again is no change", TextScale.set(1.2f));
        TextScale.apply(label, 10f);
        assertEquals(12f * density, label.getTextSize(), 0.01f);

        int[] follows = {0};
        android.widget.FrameLayout root =
            new android.widget.FrameLayout(RuntimeEnvironment.getApplication());
        android.widget.FrameLayout inner =
            new android.widget.FrameLayout(RuntimeEnvironment.getApplication());
        root.addView(inner);
        inner.addView(new FollowerView(follows));
        TextScale.dispatch(root);
        assertEquals("every follower under the root hears it", 1, follows[0]);
    }

    private static final class FollowerView extends android.view.View
            implements TextScale.Follower {
        private final int[] mCount;

        FollowerView(int[] count) {
            super(RuntimeEnvironment.getApplication());
            mCount = count;
        }

        @Override public void onTextScaleChanged() {
            mCount[0]++;
        }
    }
}
