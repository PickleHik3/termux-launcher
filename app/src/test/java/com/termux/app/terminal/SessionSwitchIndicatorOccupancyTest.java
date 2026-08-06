package com.termux.app.terminal;

import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The notice chip has to report the room it takes, or the stack below it cannot follow it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class SessionSwitchIndicatorOccupancyTest {

    @Test
    public void showReportsAHeightAndCancelReportsZero() {
        SessionSwitchIndicatorView chip = new SessionSwitchIndicatorView(
            ApplicationProvider.getApplicationContext());
        List<Integer> reported = new ArrayList<>();
        chip.setOccupancyListener(reported::add);

        setHostParams(chip);
        chip.show("[1] job 12");
        measure(chip);
        assertEquals(View.VISIBLE, chip.getVisibility());
        assertTrue("expected a positive height, got " + reported, lastPositive(reported));

        chip.cancel();
        assertEquals(View.GONE, chip.getVisibility());
        assertEquals(Integer.valueOf(0), reported.get(reported.size() - 1));
    }

    @Test
    public void anUnchangedHeightIsNotReportedTwice() {
        SessionSwitchIndicatorView chip = new SessionSwitchIndicatorView(
            ApplicationProvider.getApplicationContext());
        List<Integer> reported = new ArrayList<>();
        chip.setOccupancyListener(reported::add);

        setHostParams(chip);
        chip.show("[1] job 12");
        measure(chip);
        int afterFirst = reported.size();

        chip.show("[1] job 13");
        measure(chip);
        assertEquals(afterFirst, reported.size());
    }

    private static boolean lastPositive(List<Integer> reported) {
        return !reported.isEmpty() && reported.get(reported.size() - 1) > 0;
    }

    /** The chip lives in a FrameLayout host in the app, so give it the params setText() needs. */
    private static void setHostParams(View view) {
        view.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void measure(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.AT_MOST));
        view.layout(0, 0, view.getMeasuredWidth(), Math.max(1, view.getMeasuredHeight()));
    }
}
