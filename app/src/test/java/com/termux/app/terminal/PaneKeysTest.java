package com.termux.app.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

/**
 * The one hand-back rule after a window switch: the shown window's pane ends up holding the keys
 * whether the focus sat on a window chip, on the pane of the window that left, or the pane only
 * claims a focus it does not hold.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class PaneKeysTest {

    /** View#PFLAG_FOCUSED: a view's own "I am focused" flag. */
    private static final int PFLAG_FOCUSED = 0x00000002;

    private Activity activity;
    private Button chip;
    private View oldPane;
    private View pane;

    @Before
    public void setUp() {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        LinearLayout root = new LinearLayout(activity);
        chip = new Button(activity);
        chip.setFocusable(true);
        oldPane = focusablePane();
        pane = focusablePane();
        FrameLayout host = new FrameLayout(activity);
        host.addView(oldPane);
        host.addView(pane);
        root.addView(chip);
        root.addView(host);
        activity.setContentView(root);
    }

    private View focusablePane() {
        View view = new View(activity);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        return view;
    }

    @Test
    public void withTheFocusOnAChipTheShownPaneTakesTheKeys() {
        assertTrue(chip.requestFocus());
        assertFalse(PaneKeys.holds(pane));

        assertTrue(PaneKeys.handTo(pane, true));
        assertSame(pane, pane.getRootView().findFocus());
    }

    @Test
    public void withTheKeysOnTheWindowThatLeftTheShownPaneTakesThem() {
        assertTrue(oldPane.requestFocus());

        assertTrue(PaneKeys.handTo(pane, false));
        assertTrue(PaneKeys.holds(pane));
        assertFalse(PaneKeys.holds(oldPane));
    }

    @Test
    public void aStaleFocusClaimIsClearedAndThePaneTakesTheKeys() {
        assertTrue(chip.requestFocus());
        // The pane says it is focused while the chip holds the window's focus: a plain request
        // is refused, and a "not focused yet" check skips it.
        int flags = ReflectionHelpers.getField(pane, "mPrivateFlags");
        ReflectionHelpers.setField(pane, "mPrivateFlags", flags | PFLAG_FOCUSED);
        assertTrue(pane.isFocused());
        assertFalse(PaneKeys.holds(pane));

        assertTrue(PaneKeys.handTo(pane, true));
        assertTrue(PaneKeys.holds(pane));
    }

    @Test
    public void aQuietHandBackKeepsThePaneFocusListenerOutOfIt() {
        boolean[] heard = {false};
        View.OnFocusChangeListener listener = (v, hasFocus) -> heard[0] = true;
        pane.setOnFocusChangeListener(listener);
        assertTrue(chip.requestFocus());

        assertTrue(PaneKeys.handTo(pane, true));
        assertFalse(heard[0]);
        assertSame(listener, pane.getOnFocusChangeListener());
    }

    @Test
    public void aPaneThatAlreadyHoldsTheKeysIsLeftAlone() {
        assertTrue(pane.requestFocus());
        boolean[] heard = {false};
        pane.setOnFocusChangeListener((v, hasFocus) -> heard[0] = true);

        assertTrue(PaneKeys.handTo(pane, false));
        assertFalse(heard[0]);
    }

    @Test
    public void noPaneHoldsNothing() {
        assertFalse(PaneKeys.handTo(null, true));
    }
}
