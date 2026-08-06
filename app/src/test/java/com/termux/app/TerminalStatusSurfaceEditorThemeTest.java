package com.termux.app;

import android.app.Application;
import android.os.Build;
import android.view.View;

import androidx.appcompat.view.ContextThemeWrapper;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;

/**
 * The surface editor ("Customize status appearance") measures a {@link MaterialSwitch}. That widget
 * hands {@code materialSwitchStyle} to {@link androidx.appcompat.widget.SwitchCompat} as its only
 * style source — it passes no fallback style resource — and SwitchCompat defaults {@code showText}
 * to true with null on/off text. Under a theme that does not define {@code materialSwitchStyle} no
 * style resolves at all, so measuring the switch builds a StaticLayout over null text and throws.
 *
 * <p>That is what a Material 2 parent on the dark, pre-API-31 theme used to cause: the crash only
 * appeared in dark mode below API 31, which is why it never showed on a modern device.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "night")
public class TerminalStatusSurfaceEditorThemeTest {

    @Test
    public void materialSwitch_measuresUnderTheActivityThemeInDarkModeBelowApi31() {
        ContextThemeWrapper themed = new ContextThemeWrapper(
            org.robolectric.RuntimeEnvironment.getApplication(),
            R.style.Theme_TermuxActivity_DayNight_NoActionBar);

        MaterialSwitch materialSwitch = new MaterialSwitch(themed);

        // Threw NullPointerException from StaticLayout before the theme was moved to Material 3.
        materialSwitch.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        assertFalse("showText must stay off; the switch carries no on/off text to draw",
            materialSwitch.getShowText());
    }
}
