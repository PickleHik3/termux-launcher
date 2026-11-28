package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.Process;
import android.service.notification.StatusBarNotification;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.notifications.LauncherNotificationBadgeStore;
import com.termux.app.launcher.popup.AnchoredMenu;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.util.ReflectionHelpers;

/**
 * #458 (Terl decisions 2026-11-28): Reply on a notification in the held-icon card types in the card
 * itself. Pressed through the real card: the reply field has the focus in the card's own window,
 * the launcher took the system keyboard's hold first, and the notification stack never opened.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "w411dp-h891dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
public class SuggestionBarHeldReplyTest {

    private static final String PACKAGE = "com.example.chat";

    @After
    public void tearDown() {
        LauncherNotificationBadgeStore.clear();
    }

    @Test
    public void replyInTheHeldCardTypesInTheCardAndTheStackStaysClosed() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout root = new FrameLayout(activity);
        activity.setContentView(root);
        SuggestionBarView bar = new SuggestionBarView(activity, null);
        root.addView(bar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 200));
        View anchor = new View(activity);
        root.addView(anchor, new FrameLayout.LayoutParams(80, 80));
        idle();
        int[] imeRequests = new int[1];
        bar.setNotificationPopupInteractionListener(new SuggestionBarView.NotificationPopupInteractionListener() {
            @Override public void onNotificationPopupShown() { }
            @Override public void onNotificationPopupDismissed() { }
            @Override public void onSystemImeRequested() { imeRequests[0]++; }
        });
        bar.setNotificationDotsEverywhere(true);
        LauncherNotificationBadgeStore.onNotificationPosted(chat(activity), null);

        bar.showAppMenuFor(anchor, new LauncherAppEntry(new AppRef(PACKAGE, "Main"), "Chat", null));
        idle();
        AnchoredMenu menu = ReflectionHelpers.getField(bar, "appContextMenu");
        PopupWindow card = menu.window();
        assertNotNull("the held-icon card is up", card);
        Button reply = findButton(card.getContentView(), "Reply");
        assertNotNull("the card lists the notification with its Reply", reply);

        reply.performClick();
        idle();

        EditText field = findEditText(card.getContentView());
        assertNotNull("the reply field stands in the card", field);
        assertTrue("the field has the focus", field.hasFocus());
        assertTrue("the card's window takes the keyboard", card.isFocusable());
        assertEquals(PopupWindow.INPUT_METHOD_NEEDED, card.getInputMethodMode());
        assertTrue("the system keyboard's hold was taken first", imeRequests[0] >= 1);
        assertNull("no hop to the notification stack",
            ReflectionHelpers.getField(bar, "notificationPopupWindow"));
        assertTrue("the card is still the one up", card.isShowing());
    }

    private static StatusBarNotification chat(Activity activity) {
        Notification notification = new Notification();
        notification.extras = new Bundle();
        notification.extras.putCharSequence(Notification.EXTRA_TITLE, "Ann");
        notification.extras.putCharSequence(Notification.EXTRA_TEXT, "lunch?");
        RemoteInput input = new RemoteInput.Builder("reply").setAllowFreeFormInput(true).build();
        PendingIntent intent = PendingIntent.getBroadcast(activity, 1,
            new Intent("com.termux.test.REPLY"), PendingIntent.FLAG_UPDATE_CURRENT);
        notification.actions = new Notification.Action[] {
            new Notification.Action.Builder(0, "Reply", intent).addRemoteInput(input).build()};
        return new StatusBarNotification(PACKAGE, PACKAGE, 7, "tag", Process.myUid(), 0, 0,
            notification, Process.myUserHandle(), System.currentTimeMillis());
    }

    private static void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static Button findButton(View view, String title) {
        if (view instanceof Button && title.contentEquals(((Button) view).getText())) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findButton(group.getChildAt(i), title);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static EditText findEditText(View view) {
        if (view instanceof EditText) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = findEditText(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
