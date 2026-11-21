package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

/**
 * A settings row that is off (a Preference with setEnabled(false), which disables every view in
 * the row) reads as off: dimmed title and summary and no chevron. The row layouts once used flat
 * colours, so Edit pages behind the Home lock kept full brightness and a chevron.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SettingsRowDisabledStateTest {

    private static final int[] DISABLED = {-android.R.attr.state_enabled};
    private static final int[] ENABLED = {android.R.attr.state_enabled};

    @Test public void anOffRowDimsItsTextAndHidesItsChevron() {
        ContextThemeWrapper context = new ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(), R.style.Theme_TermuxApp_DayNight_NoActionBar);
        LayoutInflater inflater = LayoutInflater.from(context);
        ViewGroup row = (ViewGroup) inflater.inflate(R.layout.preference_settings_row,
            new FrameLayout(context), false);
        ImageView chevron = (ImageView) inflater.inflate(R.layout.preference_widget_chevron,
            (ViewGroup) row.findViewById(android.R.id.widget_frame), false);

        ColorStateList title = ((TextView) row.findViewById(android.R.id.title)).getTextColors();
        ColorStateList summary = ((TextView) row.findViewById(android.R.id.summary)).getTextColors();
        ColorStateList tint = chevron.getImageTintList();

        assertDimmed(title);
        assertDimmed(summary);
        assertTrue(tint != null);
        assertEquals(0, Color.alpha(tint.getColorForState(DISABLED, Color.BLACK)));
        assertTrue(Color.alpha(tint.getColorForState(ENABLED, 0)) > 0);
    }

    private static void assertDimmed(ColorStateList colors) {
        int alpha = Color.alpha(colors.getColorForState(DISABLED, Color.BLACK));
        assertTrue("disabled alpha " + alpha, alpha > 80 && alpha < 115);
    }
}
