package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

/** The row decides the A-Z focus; the sink it is given writes it. The row writes nothing itself. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarAzFocusSinkTest {

    private static final class Recording implements SuggestionBarView.AzFocusSink {
        View focused;
        boolean bounce;
        int focusCalls;
        int clearCalls;

        @Override public void focus(@NonNull View target, boolean bounce) {
            focused = target;
            this.bounce = bounce;
            focusCalls++;
        }

        @Override public void clear() {
            clearCalls++;
        }
    }

    private static SuggestionBarView attachedBar(FrameLayout[] hostOut) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout host = new FrameLayout(activity);
        activity.setContentView(host, new ViewGroup.LayoutParams(1058, 140));
        SuggestionBarView bar = new SuggestionBarView(host.getContext(), null);
        host.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        hostOut[0] = host;
        return bar;
    }

    private static SuggestionBarView.AzDragFocusResult resultFor(View target) {
        com.termux.app.launcher.model.LauncherAppEntry entry =
            new com.termux.app.launcher.model.LauncherAppEntry(
                new com.termux.app.launcher.model.AppRef("com.example", "com.example.Main"),
                "Example", null);
        return new SuggestionBarView.AzDragFocusResult(entry, null, null, null, target,
            SuggestionBarView.AZ_EDGE_NONE, false, false);
    }

    @Test
    public void aFocusUpdateHandsTheSinkTheTargetAndWritesNothingItself() {
        FrameLayout[] host = new FrameLayout[1];
        SuggestionBarView bar = attachedBar(host);
        Recording sink = new Recording();
        bar.setAzFocusSink(sink);
        View target = new View(host[0].getContext());
        host[0].addView(target, new FrameLayout.LayoutParams(20, 20));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        bar.updateAzFocusedEntry(resultFor(target));

        assertSame(target, sink.focused);
        assertEquals(1, sink.focusCalls);
        assertEquals(1f, target.getAlpha(), 0f);
        assertEquals(1f, target.getScaleX(), 0f);
        assertEquals(0f, target.getTranslationY(), 0f);

        bar.clearAzFocusedEntry();
        assertEquals(1, sink.clearCalls);
        assertNotNull(sink.focused);
    }

    @Test
    public void withNoSinkTheRowWritesNothing() {
        FrameLayout[] host = new FrameLayout[1];
        SuggestionBarView bar = attachedBar(host);
        View target = new View(host[0].getContext());
        host[0].addView(target, new FrameLayout.LayoutParams(20, 20));
        Shadows.shadowOf(Looper.getMainLooper()).idle();

        bar.updateAzFocusedEntry(resultFor(target));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));

        assertEquals(1f, target.getAlpha(), 0f);
    }
}
