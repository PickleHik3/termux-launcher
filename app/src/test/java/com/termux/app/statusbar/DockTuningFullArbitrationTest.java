package com.termux.app.statusbar;

import android.app.Application;
import android.os.Build;

import androidx.annotation.NonNull;

import com.termux.app.TermuxActivity;
import com.termux.app.surfaces.SurfaceEditorController;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class DockTuningFullArbitrationTest {
    @Test public void everyDockTuningEntryRejectsWhileFullOwnsTheSurface() {
        TermuxActivity activity = Robolectric.buildActivity(TermuxActivity.class).get();
        FullStatusBarController full = new FullStatusBarController(new ImmediateHost());
        full.restoreFullImmediate(TopStatusBarState.EXPANDED);
        ReflectionHelpers.setField(activity, "mFullStatusBarController", full);

        SurfaceEditorController editor = ReflectionHelpers.getField(activity, "mSurfaceEditor");
        editor.enter("status");

        assertTrue(full.isEngaged());
        assertFalse(editor.isActive());
    }

    private static final class ImmediateHost implements FullStatusBarController.Host {
        int height = 96;
        @Override public int currentHeight() { return height; }
        @Override public int normalHeight(@NonNull TopStatusBarState state) { return 96; }
        @Override public int parentMeasuredHeight() { return 600; }
        @Override public int parentPaddingTop() { return 0; }
        @Override public int parentPaddingBottom() { return 0; }
        @Override public int hostTopMargin() { return 0; }
        @Override public boolean reducedMotion() { return true; }
        @Override public void cancelNormalAnimatorKeepingCurrent() { }
        @Override public void beginTerminalResize() { }
        @Override public void applyFrame(int value, float progress) { height = value; }
        @Override public void finishTerminalResizeAfterLayout() { }
        @Override public void applyNormalState(@NonNull TopStatusBarState state) { }
        @Override public void onEngagementChanged(boolean engaged,
                                                   @NonNull TopStatusBarState target) { }
    }
}
