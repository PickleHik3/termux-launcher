package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.widget.HomeAppDrop;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** A lifted app goes to Home at once outside edit mode, and never while the drawer is edited. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerEditDragStaysTest {

    private static final class Probe implements AppDrawerDragController.Host, HomeAppDrop.Target {
        boolean mayLeave = true;
        int handedOver;
        @Override public boolean isFrozenPickupEligible(@NonNull String id) { return true; }
        @Override public boolean claimPickupContext(@NonNull String id) { return true; }
        @Override public boolean claimPickupDrag(@NonNull String id) { return true; }
        @Override public void armTerminalDispatchDragLatch() { }
        @Override public void onDragStateChanged(boolean dragging) { }
        @Override public void onDragLocation(@NonNull android.view.View t, float x, float y) { }
        @Override public void onDragTargetExited() { }
        @Override public void onAcceptedDrop() { }
        @Override public boolean canDropOnCurrentTarget() { return true; }
        @Override public AppDrawerItem resolveCurrentDropTarget(@NonNull String id) { return null; }
        @NonNull @Override public AppDrawerViewType frozenSourceViewType() {
            return AppDrawerViewType.VERTICAL;
        }
        @NonNull @Override public String drawerAnchorFor(String id) { return ""; }
        @Override public HomeAppDrop.Target homeAppDropTarget() { return this; }
        @Override public float handOverTopRawY() { return 100f; }
        @Override public boolean dragMayLeaveDrawer() { return mayLeave; }
        @Override public boolean canTakeAppDrop() { return true; }
        @Override public void onAppDropStarted(@NonNull HomeAppDrop drop, float x, float y) {
            handedOver++;
        }
    }

    private static int lift(boolean mayLeave) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        SuggestionBarView dock = new SuggestionBarView(activity, null);
        Probe host = new Probe();
        host.mayLeave = mayLeave;
        AppDrawerDragController drag = new AppDrawerDragController(dock,
            new AppDrawerDragOverlayView(activity), host);
        ImageView icon = new ImageView(activity);
        icon.setImageDrawable(new ColorDrawable(Color.RED));
        FrameLayout root = new FrameLayout(activity);
        root.addView(icon, new FrameLayout.LayoutParams(96, 96));
        activity.setContentView(root);
        LauncherAppEntry entry = new LauncherAppEntry(new AppRef("com.example.a", "Main"), "A",
            new ColorDrawable(Color.RED));
        drag.startPickup(icon, entry);
        // A move far above the drawer's top edge, held past any dwell.
        drag.onDragMoved(50f, -500f);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2));
        return host.handedOver;
    }

    @Test public void outsideEditModeTheLiftGoesToHome() {
        assertEquals(1, lift(true));
    }

    @Test public void inEditModeTheLiftNeverGoesToHome() {
        assertEquals(0, lift(false));
    }
}
