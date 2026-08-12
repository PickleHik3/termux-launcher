package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.termux.R;
import com.termux.view.TerminalView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPickerInputFocusIntegrationTest {
    @Test public void realPlusOpensAndClosesWithoutChangingTerminalEditorOrWindow() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        FrameLayout root = new FrameLayout(activity);
        TerminalView terminal = new TerminalView(activity, null); terminal.setFocusableInTouchMode(true);
        root.addView(terminal, new FrameLayout.LayoutParams(-1, -1));
        WidgetPaneView pane = new WidgetPaneView(activity); root.addView(pane, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(root); pane.setFullProgress(1f);
        root.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 800, 900);
        pane.setListener(new WidgetPaneView.Listener() {
            @Override public void onAddRequested() { pane.picker().setReducedMotion(true); pane.picker().open(); }
        }, item -> { });
        assertTrue(terminal.requestFocus()); assertSame(terminal, root.findFocus());
        assertTrue(terminal.onCheckIsTextEditor());
        android.view.Window window = activity.getWindow();
        assertTrue(pane.findViewById(R.id.widget_add_large).performClick());
        assertTrue(pane.picker().isOpen()); assertSame(window, activity.getWindow());
        assertSame(terminal, root.findFocus()); assertFalse(hasEditor(pane.picker()));
        assertTrue(pane.onBackPressed()); assertSame(terminal, root.findFocus());
    }
    private static boolean hasEditor(View view) {
        if (view instanceof EditText || view.onCheckIsTextEditor()) return true;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            if (hasEditor(((ViewGroup) view).getChildAt(i))) return true;
        return false;
    }
}
