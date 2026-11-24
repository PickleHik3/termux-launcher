package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.termux.app.chrome.HomeExitPolicy.Step;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class HomeExitControllerTest {

    private static final class FakeFrames implements HomeExit.Frames {
        long now;
        Runnable frame;
        @Override public long nowMs() { return now; }
        @Override public void requestFrame(@NonNull Runnable cb) { frame = cb; }
        @Override public void removeFrame(@NonNull Runnable cb) { if (frame == cb) frame = null; }
        @Override public void postDelayed(@NonNull Runnable r, long ms) { }
        @Override public void removeDelayed(@NonNull Runnable r) { }

        void frameAt(long at) {
            now = at;
            Runnable cb = frame;
            frame = null;
            if (cb != null) cb.run();
        }
    }

    private static final class FakeHost implements HomeExitController.Host {
        final List<String> log = new ArrayList<>();
        final View member;
        boolean open = true;
        boolean draft;
        boolean inFront = true;
        boolean reduced;
        int members = 1;
        Runnable leave;
        Runnable stay;

        FakeHost(View member) { this.member = member; }

        @Override public boolean anyOpen() { return open; }
        @Override public boolean anyHoldsUnsavedChanges() { return draft; }
        @Override public boolean goesHome() { return inFront; }
        @Override public boolean reducedMotion() { return reduced; }
        @Override public void askBeforeLeaving(@NonNull Runnable l, @NonNull Runnable s) {
            log.add("ask");
            leave = l;
            stay = s;
        }
        @Override public int beginExit(@NonNull List<View> out) {
            log.add("begin");
            if (members > 0) out.add(member);
            return members;
        }
        @Override public void endExit() {
            log.add("end");
            open = false;
        }
        @Override public void closeAllForHome() {
            log.add("closeAll");
            open = false;
        }
        @Override public void settle(@NonNull Step step) { log.add("settle:" + step); }
    }

    private FakeFrames frames;
    private FakeHost host;
    private HomeExitController controller;

    @Before public void setUp() {
        frames = new FakeFrames();
        host = new FakeHost(new View(ApplicationProvider.getApplicationContext()));
        controller = new HomeExitController(host, new HomeExit(frames));
    }

    @Test public void askThenLeaveFadesTheSurfacesAndSettlesAtTheEnd() {
        host.draft = true;
        controller.onHomePress();
        assertEquals(List.of("ask"), host.log);
        assertFalse(controller.isRunning());

        host.leave.run();
        assertEquals(List.of("ask", "begin"), host.log);
        assertTrue(controller.isRunning());
        frames.frameAt(0);
        frames.frameAt(HomeExit.DURATION_MS + 1);
        assertEquals(List.of("ask", "begin", "end", "settle:" + Step.CLOSE_STAY), host.log);
        assertFalse(controller.isRunning());
        assertEquals(1f, host.member.getAlpha(), 0f);
    }

    @Test public void askThenStayChangesNothing() {
        host.draft = true;
        controller.onHomePress();
        host.stay.run();
        assertEquals(List.of("ask"), host.log);
        assertFalse(controller.isRunning());
    }

    @Test public void aSecondPressDuringTheExitFinishesItAtOnce() {
        controller.onHomePress();
        frames.frameAt(0);
        assertTrue(controller.isRunning());
        controller.onHomePress();
        assertEquals(List.of("begin", "end", "settle:" + Step.CLOSE_STAY), host.log);
        assertFalse(controller.isRunning());
    }

    @Test public void reducedMotionClosesEverythingAtOnce() {
        host.reduced = true;
        controller.onHomePress();
        assertEquals(List.of("closeAll", "settle:" + Step.CLOSE_STAY), host.log);
    }

    @Test public void noMembersSettlesAtOnce() {
        host.members = 0;
        controller.onHomePress();
        assertEquals(List.of("begin", "settle:" + Step.CLOSE_STAY), host.log);
    }

    @Test public void nothingOpenGoesHomeOrReturns() {
        host.open = false;
        controller.onHomePress();
        host.inFront = false;
        controller.onHomePress();
        assertEquals(List.of("closeAll", "settle:" + Step.GO_HOME,
            "closeAll", "settle:" + Step.RETURN), host.log);
    }
}
