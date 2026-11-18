package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsAnimation;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What {@link SystemImeAnimation} feeds the ride and the host as the system keyboard animates,
 * and that it ignores every animation that is not the keyboard's. The platform callback the
 * compat library registers on the content view is captured and driven directly.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.R, application = Application.class)
public class SystemImeAnimationTest {

    /** A view that keeps the platform callback registered on it. */
    private static final class CapturingView extends View {
        WindowInsetsAnimation.Callback callback;

        CapturingView(Context context) {
            super(context);
        }

        @Override
        public void setWindowInsetsAnimationCallback(WindowInsetsAnimation.Callback callback) {
            this.callback = callback;
        }
    }

    private static final class FakeHost implements SystemImeAnimation.Host {
        int laidForImePx;
        boolean suppressed;
        int laidForCalls;
        final List<Integer> writes = new ArrayList<>();

        @Override public int laidForImePx() {
            laidForCalls++;
            return laidForImePx;
        }

        @Override public void writeRide(int offsetPx) {
            writes.add(offsetPx);
        }

        @Override public boolean suppressed() {
            return suppressed;
        }

        int lastWrite() {
            assertFalse("nothing was written", writes.isEmpty());
            return writes.get(writes.size() - 1);
        }
    }

    private final CapturingView mView = new CapturingView(RuntimeEnvironment.getApplication());
    private final SystemImeRide mRide = new SystemImeRide();
    private final FakeHost mHost = new FakeHost();

    private WindowInsetsAnimation mIme = animation(WindowInsets.Type.ime());
    private WindowInsetsAnimation mBars = animation(WindowInsets.Type.statusBars());

    private static WindowInsetsAnimation animation(int typeMask) {
        return new WindowInsetsAnimation(typeMask, new LinearInterpolator(), 250L);
    }

    private static WindowInsetsAnimation.Bounds bounds() {
        try {
            Constructor<WindowInsetsAnimation.Bounds> ctor =
                WindowInsetsAnimation.Bounds.class.getDeclaredConstructor(Insets.class, Insets.class);
            ctor.setAccessible(true);
            return ctor.newInstance(Insets.NONE, Insets.of(0, 0, 0, 400));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot build animation bounds", e);
        }
    }

    private static WindowInsets imeInsets(int bottomPx) {
        return new WindowInsets.Builder()
            .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, bottomPx)).build();
    }

    private WindowInsetsAnimation.Callback installed() {
        SystemImeAnimation.install(mView, mRide, mHost);
        assertNotNull("install registered no callback", mView.callback);
        return mView.callback;
    }

    private static List<WindowInsetsAnimation> running(WindowInsetsAnimation... animations) {
        List<WindowInsetsAnimation> list = new ArrayList<>();
        Collections.addAll(list, animations);
        return list;
    }

    @Test
    public void installRegistersACallbackOnTheContentView() {
        assertNull(mView.callback);
        installed();
    }

    @Test
    public void startOfAnImeAnimationHoldsTheStackWhereTheKeyboardStarted() {
        WindowInsetsAnimation.Callback callback = installed();
        mHost.laidForImePx = 0;
        callback.onPrepare(mIme);
        // The layout answered for the shown keyboard before the animation starts.
        mHost.laidForImePx = 400;
        callback.onStart(mIme, bounds());
        assertTrue(mRide.owns());
        assertEquals(-400, mRide.offsetPx());
        assertEquals(Collections.singletonList(-400), mHost.writes);
    }

    @Test
    public void progressWritesTheOffsetFromTheAnimatedKeyboardHeight() {
        WindowInsetsAnimation.Callback callback = installed();
        mHost.laidForImePx = 0;
        callback.onPrepare(mIme);
        mHost.laidForImePx = 400;
        callback.onStart(mIme, bounds());
        int[] heights = {100, 250, 400};
        for (int height : heights) {
            callback.onProgress(imeInsets(height), running(mIme));
            assertEquals(height - 400, mHost.lastWrite());
        }
    }

    @Test
    public void aHidingKeyboardRidesUpwardFromItsLaidOutEnd() {
        WindowInsetsAnimation.Callback callback = installed();
        mHost.laidForImePx = 400;
        callback.onPrepare(mIme);
        mHost.laidForImePx = 0;
        callback.onStart(mIme, bounds());
        assertEquals(400, mHost.lastWrite());
        callback.onProgress(imeInsets(150), running(mIme));
        assertEquals(150, mHost.lastWrite());
    }

    @Test
    public void endOfAnImeAnimationStopsTheRideAndWritesZero() {
        WindowInsetsAnimation.Callback callback = installed();
        mHost.laidForImePx = 400;
        callback.onStart(mIme, bounds());
        callback.onProgress(imeInsets(100), running(mIme));
        callback.onEnd(mIme);
        assertEquals(0, mRide.offsetPx());
        assertEquals(0, mHost.lastWrite());
    }

    @Test
    public void aSuppressedKeyboardNeverMovesTheStack() {
        WindowInsetsAnimation.Callback callback = installed();
        mHost.suppressed = true;
        mHost.laidForImePx = 400;
        callback.onStart(mIme, bounds());
        callback.onProgress(imeInsets(100), running(mIme));
        for (int write : mHost.writes) assertEquals(0, write);
        assertFalse(mHost.writes.isEmpty());
    }

    @Test
    public void anAnimationThatIsNotTheKeyboardsReachesNothing() {
        WindowInsetsAnimation.Callback callback = installed();
        mHost.laidForImePx = 400;
        callback.onPrepare(mBars);
        callback.onStart(mBars, bounds());
        callback.onProgress(imeInsets(100), running(mBars));
        callback.onEnd(mBars);
        assertTrue(mHost.writes.isEmpty());
        assertEquals(0, mHost.laidForCalls);
        assertEquals(SystemImeRide.NONE, mRide.state());
        assertEquals(0, mRide.offsetPx());
    }

    @Test
    public void progressWithTheKeyboardAmongTheRunningAnimationsStillRides() {
        WindowInsetsAnimation.Callback callback = installed();
        mHost.laidForImePx = 400;
        callback.onStart(mIme, bounds());
        mHost.writes.clear();
        callback.onProgress(imeInsets(300), running(mBars, mIme));
        assertEquals(Collections.singletonList(-100), mHost.writes);
    }

    @Test
    public void theEndOfAnotherAnimationDuringTheKeyboardsLeavesTheRideRunning() {
        WindowInsetsAnimation.Callback callback = installed();
        mHost.laidForImePx = 400;
        callback.onStart(mIme, bounds());
        callback.onProgress(imeInsets(100), running(mIme));
        int writes = mHost.writes.size();
        callback.onEnd(mBars);
        assertEquals(writes, mHost.writes.size());
        assertEquals(-300, mRide.offsetPx());
    }
}
