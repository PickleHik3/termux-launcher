package com.termux.app.launcher.popup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLooper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The dock's long-press App menu: every row is a real view, so what a screen reader needs is that
 * each reads as a button with its label, and that the window that holds them is named.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, qualifiers = "w411dp-h891dp-mdpi", application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AppMenuAccessibilityTest {

    private ActivityController<Activity> controller;

    @After
    public void tearDown() {
        if (controller != null) controller.close();
    }

    private static final class FixedTheme implements AnchoredMenuTheme {
        @Override public int textColor() { return 0xFFFFFFFF; }
        @Override public int selectedTextColor() { return 0xFFFFFFFF; }
        @Override public int opacityPercent() { return 100; }
        @Override public boolean blurEnabled() { return false; }
        @Override public int blurRadiusDp() { return 0; }
    }

    @Test
    public void rowsReadAsLabelledButtons_andClickingOneRunsItsAction() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        MenuRowFactory factory = new MenuRowFactory(activity, new FixedTheme());
        List<String> ran = new ArrayList<>();
        AppMenuCard card = AppMenuCard.build(activity, factory, Arrays.asList(
            Collections.singletonList(
                new AppMenuCard.Item("New tab", 0, () -> ran.add("new tab"))),
            Collections.singletonList(
                new AppMenuCard.Item("Uninstall", 0, () -> ran.add("uninstall")))));
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        root.addView(card.view, new FrameLayout.LayoutParams(400, 800));
        card.view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        card.view.layout(0, 0, 400, 800);

        View row = card.rows.get(1).rowView;
        AccessibilityNodeInfo info = row.createAccessibilityNodeInfo();

        assertEquals("Uninstall", info.getText().toString());
        assertEquals("android.widget.Button", info.getClassName().toString());
        assertTrue(info.isClickable());
        assertTrue(info.isImportantForAccessibility());
        assertTrue(row.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null));
        ShadowLooper.shadowMainLooper().idleFor(Duration.ofMillis(500));
        assertEquals(Collections.singletonList("uninstall"), ran);
    }
}
