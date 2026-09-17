package com.termux.app.x11;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.termux.R;

import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.junit.runner.RunWith;

/**
 * Item 03's staleness itself: {@link X11PaneFrame#applyRunning} was the only thing that ever
 * touched the empty state's message, and nothing on the resume or place-change path called it —
 * only a display starting or stopping did. {@link X11PaneFrame#refreshEmptyStateReadiness()} is
 * the fix, called from {@code TermuxActivity} on both those arrivals; this is where its contract
 * is pinned down: a stale message left behind by anything else is put back to whatever the
 * policy currently says, without a running-state transition in sight. The message-to-input
 * mapping itself is {@link DisplayEmptyStatePolicyTest}'s job, not this one.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class X11PaneFrameEmptyStateTest {

    /**
     * The frame with just the empty state's own views wired, as inflation wires them, but
     * without the display view its layout carries: that view binds the native server, so it
     * cannot exist on the JVM. Ends inflated and not running, exactly as a freshly built page is.
     */
    private static X11PaneFrame page(Activity activity) {
        X11PaneFrame page = new X11PaneFrame(activity);
        page.addView(textView(activity, R.id.x11_pane_empty_message));
        page.addView(textView(activity, R.id.x11_pane_start));
        page.addView(textView(activity, R.id.x11_pane_guide));
        page.onFinishInflate();
        return page;
    }

    private static TextView textView(Activity activity, int id) {
        TextView view = new TextView(activity);
        view.setId(id);
        view.setLayoutParams(new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return view;
    }

    @Test
    public void arrivingAtThePlaceReDerivesTheStaleMessage() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        X11PaneFrame page = page(activity);
        TextView message = page.findViewById(R.id.x11_pane_empty_message);
        String derived = message.getText().toString();

        // What staleness looked like before this fix: some earlier reading is still sitting
        // there, and nothing between then and now has called applyRunning to replace it — a
        // start/stop is the only thing that ever did.
        message.setText("left over from an earlier reading");
        assertNotEquals(derived, message.getText().toString());

        page.refreshEmptyStateReadiness();

        assertEquals("arrival re-ran the policy and put the current answer back",
            derived, message.getText().toString());
    }

    @Test
    public void arrivingWhileADisplayIsRunningTouchesNothing() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        X11PaneFrame page = page(activity);
        page.applyRunning(true);
        TextView message = page.findViewById(R.id.x11_pane_empty_message);
        message.setText("whatever a running display left on the empty state");

        page.refreshEmptyStateReadiness();

        assertEquals("there is no empty state to refresh while a server is up",
            "whatever a running display left on the empty state", message.getText().toString());
    }
}
