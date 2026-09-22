package com.termux.app;

import android.app.Application;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;

import com.termux.R;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

/**
 * One owner for the dock plank's tint.
 *
 * <p>The appearance pass used to flat-colour {@code extrakeys_background} opaque and leave the
 * glass to the chrome apply its callers made straight afterwards. The content view's insets
 * listener stopped making that apply when the insets had not moved, and every relayout
 * re-dispatches the same insets — so the plank was painted one solid colour over its own blurred
 * backdrop with nothing behind to put the glass back, while the keyboard below it stayed glass.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TermuxActivityDockGlassOwnershipTest {

    @Test
    public void theAppearancePassLeavesTheDockPlanksTintAlone() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        activity.setContentView(R.layout.activity_termux);
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(activity, false);
        assertNotNull("no preferences means the pass returns before it paints anything", preferences);
        ReflectionHelpers.setField(activity, "mPreferences", preferences);

        View dockTint = activity.findViewById(R.id.extrakeys_background);
        View bottomSpace = activity.findViewById(R.id.activity_termux_bottom_space_background);
        assertNotNull(dockTint);
        assertNotNull(bottomSpace);
        // Stand in for the layered glass doApplyChromeSpec builds: anything but this exact drawable
        // afterwards means a second writer repainted the plank. Deliberately not a ColorDrawable —
        // setBackgroundColor() recolours one of those in place and would keep the same instance.
        Drawable dockGlass = new GradientDrawable();
        Drawable controlGlass = new GradientDrawable();
        dockTint.setBackground(dockGlass);
        bottomSpace.setBackground(controlGlass);

        ReflectionHelpers.callInstanceMethod(activity, "applyTerminalSurfaceAppearance");

        // Control: the pass really did run past its early return and flat-colour what it does own.
        assertNotSame("the appearance pass did not run — the assertion below would prove nothing",
            controlGlass, bottomSpace.getBackground());
        assertSame("the appearance pass repainted the dock plank; only the chrome apply may",
            dockGlass, dockTint.getBackground());
    }
}
