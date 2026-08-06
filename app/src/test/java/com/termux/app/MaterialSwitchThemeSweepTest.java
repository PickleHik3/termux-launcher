package com.termux.app;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.view.View;

import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;

/**
 * Guards the whole family of the "Customize status appearance" crash.
 *
 * <p>{@link MaterialSwitch} hands {@code materialSwitchStyle} to
 * {@link androidx.appcompat.widget.SwitchCompat} as its only style source and passes no fallback
 * style resource. Only Material 3 themes define that attribute. Under any theme that does not, no
 * style resolves, SwitchCompat falls back to its own {@code showText = true} default with null
 * on/off text, and measuring it builds a StaticLayout over null and throws.
 *
 * <p>So every theme an activity can run under has to be Material 3, in dark mode and light, at the
 * oldest API the app supports as well as the newest. A theme drifting back to a Material 2 parent
 * is a crash, not a cosmetic change, and it only shows on the configurations that resolve that
 * variant — which is what made the original report unreproducible on a modern device.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class MaterialSwitchThemeSweepTest {

    /** Every theme the manifest attaches to an activity. */
    private static final int[] ACTIVITY_THEMES = {
        R.style.Theme_TermuxActivity_DayNight_NoActionBar,
        R.style.Theme_TermuxApp_DayNight_NoActionBar,
        R.style.Theme_TermuxApp_DayNight_DarkActionBar,
        com.termux.shared.R.style.Theme_MarkdownViewActivity_DayNight,
        R.style.Theme_TermuxCropImageActivity,
    };

    @Test
    @Config(qualifiers = "notnight")
    public void everyActivityTheme_measuresAMaterialSwitch_inLightMode() {
        assertEveryThemeMeasuresASwitch();
    }

    @Test
    @Config(qualifiers = "night")
    public void everyActivityTheme_measuresAMaterialSwitch_inDarkMode() {
        assertEveryThemeMeasuresASwitch();
    }

    @Test
    @Config(qualifiers = "night", sdk = Build.VERSION_CODES.S)
    public void everyActivityTheme_measuresAMaterialSwitch_inDarkModeOnApi31() {
        assertEveryThemeMeasuresASwitch();
    }

    /** Dialogs get their own theme overlay, so it has to carry the attribute too. */
    @Test
    @Config(qualifiers = "night")
    public void dialogOverlay_measuresAMaterialSwitch_inDarkMode() {
        Context activityThemed = new ContextThemeWrapper(
            RuntimeEnvironment.getApplication(), R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        assertMeasures(new ContextThemeWrapper(activityThemed,
            com.termux.shared.R.style.ThemeOverlay_BaseDialog_DayNight), "BaseDialog overlay");
    }

    private void assertEveryThemeMeasuresASwitch() {
        for (int theme : ACTIVITY_THEMES) {
            assertMeasures(new ContextThemeWrapper(RuntimeEnvironment.getApplication(), theme),
                RuntimeEnvironment.getApplication().getResources().getResourceEntryName(theme));
        }
    }

    private void assertMeasures(Context context, String label) {
        MaterialSwitch materialSwitch = new MaterialSwitch(context);
        // Throws NullPointerException from StaticLayout when no switch style resolves.
        materialSwitch.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        assertFalse(label + " must keep showText off; the switch carries no on/off text",
            materialSwitch.getShowText());
    }
}
