package com.termux.app.launcher.notifications;

import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.service.notification.StatusBarNotification;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The card surface is the only part of the mirrored-notification stack that can be exercised without
 * a listener service connected: cards, the swipe, the composer and the RemoteInput send all live
 * here, and the host keeps nothing but the window. These pin that behaviour.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, qualifiers = "w411dp-h891dp-xxhdpi",
    application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class NotificationCardSurfaceTest {

    private Context context;
    private RecordingHost host;
    private RecordingListener listener;
    private NotificationCardSurface surface;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        host = new RecordingHost(context);
        listener = new RecordingListener();
        surface = new NotificationCardSurface(host, listener);
    }

    // ------------------------------------------------------------------ building

    @Test
    public void buildContent_headerCountsOnlyWhenThereIsMoreThanOneCard() {
        NotificationCardSurface.Content single = surface.buildContent(
            "Signal", new ColorDrawable(0xFF00FF00),
            Collections.singletonList(notification("Ann", "hi", null, true)));
        assertEquals("Signal", header(single).getText().toString());

        NotificationCardSurface.Content two = surface.buildContent("Signal", null,
            Arrays.asList(notification("Ann", "hi", null, true),
                notification("Bob", "yo", null, true)));
        assertEquals("Signal · 2", header(two).getText().toString());
    }

    @Test
    public void buildContent_showsTitleAndTextAndDividesTheStack() {
        NotificationCardSurface.Content content = surface.buildContent("Signal", null,
            Arrays.asList(notification("Ann", "see you at six", null, true),
                notification("Bob", "yo", null, true)));
        // header + card + divider + card
        assertEquals(4, content.shell.getChildCount());
        List<String> texts = new ArrayList<>();
        collectText(content.shell, texts);
        assertTrue(texts.contains("Ann"));
        assertTrue(texts.contains("see you at six"));
        assertTrue(texts.contains("Bob"));
        assertTrue(texts.contains("yo"));
    }

    @Test
    public void buildContent_onlyClearableCardsOfferDismissAndSwipe() {
        NotificationCardSurface.Content clearable = surface.buildContent("Signal", null,
            Collections.singletonList(notification("Ann", "hi", null, true)));
        assertTrue(buttonTitles(clearable.shell).contains("Dismiss"));

        NotificationCardSurface.Content ongoing = surface.buildContent("Signal", null,
            Collections.singletonList(notification("Ann", "hi", null, false)));
        assertFalse(buttonTitles(ongoing.shell).contains("Dismiss"));
        // Swipe disabled: the frame refuses the stream outright so the card's own content keeps it.
        assertFalse(swipeFrame(ongoing.shell, 0).onTouchEvent(down(0f, 0f)));
    }

    @Test
    public void buildContent_replyTargetIsTheFirstFreeFormActionOfEachCard() {
        StatusBarNotification withReply = notification("Ann", "hi",
            new Notification.Action[]{
                action("Mark read", null),
                action("Reply", freeform("reply_key")),
                action("Reply later", freeform("second_key"))
            }, true);
        NotificationCardSurface.Content content = surface.buildContent("Signal", null,
            Arrays.asList(notification("Bob", "yo", null, true), withReply));
        assertEquals(1, content.replyTargets.size());
        NotificationCardSurface.ReplyTarget target = content.replyTargets.get(0);
        assertEquals("Reply", target.action.title.toString());
        assertEquals("reply_key", target.freeform.getResultKey());
        assertEquals("Ann", target.recipient.toString());
        assertSame(withReply, target.sbn);
    }

    // ------------------------------------------------------------------ dismiss

    @Test
    public void dismissCard_cancelsTheSystemNotificationAndHidesTheCard() {
        StatusBarNotification sbn = notification("Ann", "hi", null, true);
        NotificationCardSurface.Content content = surface.buildContent("Signal", null,
            Collections.singletonList(sbn));
        View card = swipeFrame(content.shell, 0).getChildAt(0);

        surface.dismissCard(card, sbn);

        assertEquals(Collections.singletonList(sbn.getKey()), host.cancelled);
        assertEquals(View.GONE, card.getVisibility());
        assertEquals(Collections.singletonList(sbn), listener.dismissed);
        // The popup only closes once the store has nothing left for the package.
        assertTrue(listener.popupDismissRequests == 0);
        host.activePackages.clear();
        host.runPending();
        assertEquals(1, listener.popupDismissRequests);
    }

    @Test
    public void dismissCard_keepsThePopupWhileOtherNotificationsRemain() {
        StatusBarNotification sbn = notification("Ann", "hi", null, true);
        surface.dismissCard(new View(context), sbn);
        host.runPending();
        assertEquals(0, listener.popupDismissRequests);
    }

    @Test
    public void dismissButton_clearsTheCard() {
        StatusBarNotification sbn = notification("Ann", "hi", null, true);
        NotificationCardSurface.Content content = surface.buildContent("Signal", null,
            Collections.singletonList(sbn));
        button(content.shell, "Dismiss").performClick();
        assertEquals(Collections.singletonList(sbn.getKey()), host.cancelled);
    }

    // ------------------------------------------------------------------ swipe

    @Test
    public void swipe_dismissesPastAThirdOfTheWidthInEitherDirection() {
        assertFalse(SwipeDismissFrame.shouldDismiss(0f, 300));
        assertFalse(SwipeDismissFrame.shouldDismiss(101f, 300));
        assertTrue(SwipeDismissFrame.shouldDismiss(103f, 300));
        assertFalse(SwipeDismissFrame.shouldDismiss(-101f, 300));
        assertTrue(SwipeDismissFrame.shouldDismiss(-103f, 300));
        // A card that has not been laid out yet cannot be swiped away.
        assertFalse(SwipeDismissFrame.shouldDismiss(500f, 0));
    }

    @Test
    public void swipe_pastTheThresholdFiresTheDismissRunnable() {
        int[] fired = new int[1];
        SwipeDismissFrame frame = layoutFrame(300, 100);
        frame.setOnDismiss(() -> fired[0]++);

        frame.onInterceptTouchEvent(down(0f, 0f));
        assertTrue(frame.onInterceptTouchEvent(move(200f, 2f)));
        frame.onTouchEvent(move(200f, 2f));
        assertEquals(200f, frame.getTranslationX(), 0.01f);
        frame.onTouchEvent(up(200f, 2f));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(1, fired[0]);
    }

    @Test
    public void swipe_shortOfTheThresholdSpringsBackWithoutDismissing() {
        int[] fired = new int[1];
        SwipeDismissFrame frame = layoutFrame(300, 100);
        frame.setOnDismiss(() -> fired[0]++);

        frame.onInterceptTouchEvent(down(0f, 0f));
        frame.onInterceptTouchEvent(move(60f, 2f));
        frame.onTouchEvent(move(60f, 2f));
        frame.onTouchEvent(up(60f, 2f));
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals(0, fired[0]);
    }

    @Test
    public void swipe_ignoresAVerticalDrag() {
        SwipeDismissFrame frame = layoutFrame(300, 100);
        frame.onInterceptTouchEvent(down(0f, 0f));
        assertFalse(frame.onInterceptTouchEvent(move(20f, 200f)));
    }

    // ------------------------------------------------------------------ badges

    @Test
    public void badge_showsOnlyForACellWhosePackageHasAnActiveNotification() {
        Set<String> active = new HashSet<>(Arrays.asList("com.signal", "com.mail"));
        assertTrue(NotificationBadgeFrame.hasActiveBadge(true,
            Collections.singleton("com.signal"), active));
        assertFalse(NotificationBadgeFrame.hasActiveBadge(true,
            Collections.singleton("com.other"), active));
        // A folder cell stands for several packages: one badged app is enough.
        assertTrue(NotificationBadgeFrame.hasActiveBadge(true,
            new HashSet<>(Arrays.asList("com.other", "com.mail")), active));
        // Badges off, an empty cell, or nothing active at all: no dot.
        assertFalse(NotificationBadgeFrame.hasActiveBadge(false,
            Collections.singleton("com.signal"), active));
        assertFalse(NotificationBadgeFrame.hasActiveBadge(true,
            Collections.emptySet(), active));
        assertFalse(NotificationBadgeFrame.hasActiveBadge(true,
            Collections.singleton("com.signal"), Collections.emptySet()));
    }

    @Test
    public void badgeFrame_readsTheHostsLiveStateRatherThanASnapshot() {
        RecordingBadgeStyle style = new RecordingBadgeStyle();
        NotificationBadgeFrame frame = new NotificationBadgeFrame(context, style);
        frame.setBadgePackages(Collections.singleton("com.signal"));
        frame.measure(View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY));
        frame.layout(0, 0, 120, 120);

        frame.draw(new android.graphics.Canvas(
            android.graphics.Bitmap.createBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888)));
        assertTrue(style.fillReads > 0);

        style.active = Collections.emptySet();
        style.fillReads = 0;
        frame.draw(new android.graphics.Canvas(
            android.graphics.Bitmap.createBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888)));
        assertEquals(0, style.fillReads);
    }

    // ------------------------------------------------------------------ free-form selection

    @Test
    public void firstFreeformRemoteInput_picksTheFirstFreeFormInputAndNothingElse() {
        assertNull(NotificationCardSurface.firstFreeformRemoteInput(null));
        assertNull(NotificationCardSurface.firstFreeformRemoteInput(new RemoteInput[0]));
        assertNull(NotificationCardSurface.firstFreeformRemoteInput(
            new RemoteInput[]{choicesOnly("choice_key")}));
        assertNull(NotificationCardSurface.firstFreeformRemoteInput(new RemoteInput[]{null}));
        assertEquals("second", NotificationCardSurface.firstFreeformRemoteInput(
            new RemoteInput[]{choicesOnly("first"), freeform("second"), freeform("third")})
            .getResultKey());
    }

    @Test
    public void firstText_prefersTheEarlierKeyAndSkipsEmptyOnes() {
        Bundle extras = new Bundle();
        extras.putCharSequence(Notification.EXTRA_TEXT, "short");
        assertEquals("short", NotificationCardSurface.firstText(extras,
            Notification.EXTRA_BIG_TEXT, Notification.EXTRA_TEXT).toString());
        extras.putCharSequence(Notification.EXTRA_BIG_TEXT, "the long form");
        assertEquals("the long form", NotificationCardSurface.firstText(extras,
            Notification.EXTRA_BIG_TEXT, Notification.EXTRA_TEXT).toString());
        assertNull(NotificationCardSurface.firstText(null, Notification.EXTRA_TEXT));
        assertNull(NotificationCardSurface.firstText(new Bundle(), Notification.EXTRA_TEXT));
    }

    // ------------------------------------------------------------------ reply flow

    @Test
    public void tappingReply_replacesTheActionRowWithAComposerTheHostThenOwns() {
        NotificationCardSurface.Content content = surface.buildContent("Signal", null,
            Collections.singletonList(notification("Ann", "hi",
                new Notification.Action[]{action("Reply", freeform("reply_key"))}, true)));
        button(content.shell, "Reply").performClick();

        assertNotNull(listener.composer);
        assertEquals(1, listener.composerOpenCount);
        assertEquals("Reply to Ann", listener.composer.getHint().toString());
        // The composer took the action row's place inside the same host.
        assertSame(listener.composer.getParent().getParent(),
            content.replyTargets.get(0).actionHost);
    }

    @Test
    public void composer_hintFallsBackToTheRemoteInputLabelWithoutARecipient() {
        NotificationCardSurface.Content content = surface.buildContent("Signal", null,
            Collections.singletonList(notification(null, "hi",
                new Notification.Action[]{action("Reply", freeform("reply_key", "Message"))}, true)));
        surface.beginReply(content.replyTargets.get(0));
        assertEquals("Message", listener.composer.getHint().toString());
    }

    @Test
    public void composer_sendStaysDisabledUntilThereIsSomethingToSend() {
        NotificationCardSurface.Content content = surface.buildContent("Signal", null,
            Collections.singletonList(notification("Ann", "hi",
                new Notification.Action[]{action("Reply", freeform("reply_key"))}, true)));
        surface.beginReply(content.replyTargets.get(0));
        Button send = button(content.shell, "Send");
        assertFalse(send.isEnabled());
        listener.composer.setText("   ");
        assertFalse(send.isEnabled());
        listener.composer.setText("on my way");
        assertTrue(send.isEnabled());
        listener.composer.setText("");
        assertFalse(send.isEnabled());
    }

    @Test
    public void composer_touchAsksTheHostForTheImeAgain() {
        NotificationCardSurface.Content content = surface.buildContent("Signal", null,
            Collections.singletonList(notification("Ann", "hi",
                new Notification.Action[]{action("Reply", freeform("reply_key"))}, true)));
        surface.beginReply(content.replyTargets.get(0));
        assertEquals(0, listener.imeRequests);
        listener.composer.dispatchTouchEvent(down(0f, 0f));
        assertEquals(1, listener.imeRequests);
    }

    @Test
    public void sendReply_fillsTheFreeFormResultAndFiresTheIntent() {
        RemoteInput freeform = freeform("reply_key");
        RemoteInput ignored = choicesOnly("choice_key");
        StatusBarNotification sbn = notification("Ann", "hi",
            new Notification.Action[]{action("Reply", freeform)}, true);
        Notification.Action action = sbn.getNotification().actions[0];

        assertTrue(surface.sendReply(sbn, action,
            new RemoteInput[]{ignored, freeform}, "  on my way  "));

        assertEquals(Collections.singletonList("on my way"), listener.repliesSent);
        assertEquals(1, listener.popupDismissRequests);
        Intent sent = lastBroadcast();
        assertNotNull(sent);
        Bundle results = RemoteInput.getResultsFromIntent(sent);
        assertNotNull(results);
        assertEquals("on my way", results.getCharSequence("reply_key").toString());
        assertNull(results.getCharSequence("choice_key"));
    }

    @Test
    public void sendReply_ignoresBlankText() {
        StatusBarNotification sbn = notification("Ann", "hi",
            new Notification.Action[]{action("Reply", freeform("reply_key"))}, true);
        Notification.Action action = sbn.getNotification().actions[0];
        assertFalse(surface.sendReply(sbn, action, action.getRemoteInputs(), "   "));
        assertFalse(surface.sendReply(sbn, action, action.getRemoteInputs(), null));
        assertTrue(listener.repliesSent.isEmpty());
        assertEquals(0, listener.popupDismissRequests);
    }

    @Test
    public void composerSend_sendsTheTypedReply() {
        NotificationCardSurface.Content content = surface.buildContent("Signal", null,
            Collections.singletonList(notification("Ann", "hi",
                new Notification.Action[]{action("Reply", freeform("reply_key"))}, true)));
        surface.beginReply(content.replyTargets.get(0));
        listener.composer.setText("later");
        button(content.shell, "Send").performClick();
        assertEquals(Collections.singletonList("later"), listener.repliesSent);
    }

    // ------------------------------------------------------------------ other actions

    @Test
    public void plainAction_firesItsIntentWithoutClosingThePopup() {
        StatusBarNotification sbn = notification("Ann", "hi",
            new Notification.Action[]{action("Mark read", null)}, true);
        NotificationCardSurface.Content content = surface.buildContent("Signal", null,
            Collections.singletonList(sbn));
        button(content.shell, "Mark read").performClick();
        assertEquals(1, listener.actionsInvoked);
        assertEquals(0, listener.popupDismissRequests);
        assertNotNull(lastBroadcast());
    }

    @Test
    public void tappingTheBody_sendsTheContentIntentAndClosesThePopup() {
        StatusBarNotification sbn = notification("Ann", "hi", null, true);
        sbn.getNotification().contentIntent = pendingIntent();
        sbn.getNotification().flags |= Notification.FLAG_AUTO_CANCEL;
        NotificationCardSurface.Content content = surface.buildContent("Signal", null,
            Collections.singletonList(sbn));

        View card = swipeFrame(content.shell, 0).getChildAt(0);
        ((ViewGroup) card).getChildAt(0).performClick();

        assertEquals(1, listener.contentIntents);
        // FLAG_AUTO_CANCEL means the card's notification goes with the tap.
        assertEquals(Collections.singletonList(sbn.getKey()), host.cancelled);
        assertEquals(1, listener.popupDismissRequests);
    }

    @Test
    public void tappingTheBody_leavesANonAutoCancelNotificationAlone() {
        StatusBarNotification sbn = notification("Ann", "hi", null, true);
        sbn.getNotification().contentIntent = pendingIntent();
        NotificationCardSurface.Content content = surface.buildContent("Signal", null,
            Collections.singletonList(sbn));
        ((ViewGroup) swipeFrame(content.shell, 0).getChildAt(0)).getChildAt(0).performClick();
        assertTrue(host.cancelled.isEmpty());
        assertEquals(1, listener.popupDismissRequests);
    }

    // ------------------------------------------------------------------ highlight

    @Test
    public void highlightReplyCard_marksTheAutoOpenedCardAndPadsForItsRim() {
        NotificationCardSurface.Content content = surface.buildContent("Signal", null,
            Collections.singletonList(notification("Ann", "hi",
                new Notification.Action[]{action("Reply", freeform("reply_key"))}, true)));
        NotificationCardSurface.ReplyTarget target = content.replyTargets.get(0);
        int leftBefore = target.card.getPaddingLeft();
        int bottomBefore = target.card.getPaddingBottom();
        assertNull(target.card.getBackground());

        surface.highlightReplyCard(target);

        assertNotNull(target.card.getBackground());
        assertEquals(leftBefore + host.dp(4), target.card.getPaddingLeft());
        assertEquals(bottomBefore + host.dp(3), target.card.getPaddingBottom());
    }

    // ------------------------------------------------------------------ rules

    @Test
    public void autoOpenReply_appliesToTheNewestReplyCapableCard() {
        assertFalse(NotificationCardSurface.shouldAutoOpenReply(0));
        assertTrue(NotificationCardSurface.shouldAutoOpenReply(1));
        assertTrue(NotificationCardSurface.shouldAutoOpenReply(5));
    }

    @Test
    public void keyChange_neverThrowsAwayAHalfTypedReply() {
        assertTrue(NotificationCardSurface.shouldDismissOnKeyChange(true, false));
        assertFalse(NotificationCardSurface.shouldDismissOnKeyChange(true, true));
        assertFalse(NotificationCardSurface.shouldDismissOnKeyChange(false, false));
        assertFalse(NotificationCardSurface.shouldDismissOnKeyChange(false, true));
    }

    @Test
    public void adaptiveWidth_growsForActionsAndStaysWithinTheMenuBounds() {
        assertEquals(500, NotificationCardSurface.adaptiveWidth(500, 320, 240, 900));
        assertEquals(680, NotificationCardSurface.adaptiveWidth(500, 680, 240, 900));
        assertEquals(900, NotificationCardSurface.adaptiveWidth(500, 1100, 240, 900));
        assertEquals(240, NotificationCardSurface.adaptiveWidth(100, 100, 240, 900));
    }

    @Test
    public void preferredWidth_isHalfTheScreenForOneQuietCardAndWiderForAStack() {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int single = surface.preferredWidth(
            Collections.singletonList(notification("Ann", "hi", null, false)));
        assertEquals(screenWidth / 2, single);

        int stack = surface.preferredWidth(Arrays.asList(
            notification("Ann", "hi", null, false), notification("Bob", "yo", null, false)));
        assertTrue(stack > single);

        // A single conversation with a reply action gets a wider composer.
        int replyable = surface.preferredWidth(Collections.singletonList(
            notification("Ann", "hi",
                new Notification.Action[]{action("Reply", freeform("reply_key"))}, false)));
        assertTrue(replyable > single);
    }

    // ------------------------------------------------------------------ fixtures

    private StatusBarNotification notification(@androidx.annotation.Nullable String title,
                                               String text,
                                               @androidx.annotation.Nullable
                                                   Notification.Action[] actions,
                                               boolean clearable) {
        Notification notification = new Notification();
        notification.extras = new Bundle();
        if (title != null) notification.extras.putCharSequence(Notification.EXTRA_TITLE, title);
        notification.extras.putCharSequence(Notification.EXTRA_TEXT, text);
        notification.actions = actions;
        if (!clearable) notification.flags |= Notification.FLAG_NO_CLEAR;
        StatusBarNotification sbn = new StatusBarNotification("com.signal", "com.signal",
            nextId++, "tag" + nextId, Process.myUid(), 0, 0, notification,
            Process.myUserHandle(), System.currentTimeMillis());
        host.activePackages.add("com.signal");
        return sbn;
    }

    private int nextId = 1;

    private Notification.Action action(String title,
                                      @androidx.annotation.Nullable RemoteInput remoteInput) {
        Notification.Action.Builder builder =
            new Notification.Action.Builder(0, title, pendingIntent());
        if (remoteInput != null) builder.addRemoteInput(remoteInput);
        return builder.build();
    }

    private PendingIntent pendingIntent() {
        return PendingIntent.getBroadcast(context, nextId++,
            new Intent("com.termux.test.NOTIFICATION_ACTION"), PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static RemoteInput freeform(String key) {
        return new RemoteInput.Builder(key).setAllowFreeFormInput(true).build();
    }

    private static RemoteInput freeform(String key, String label) {
        return new RemoteInput.Builder(key).setLabel(label).setAllowFreeFormInput(true).build();
    }

    private static RemoteInput choicesOnly(String key) {
        return new RemoteInput.Builder(key)
            .setChoices(new CharSequence[]{"ok"})
            .setAllowFreeFormInput(false)
            .build();
    }

    @androidx.annotation.Nullable
    private Intent lastBroadcast() {
        List<Intent> broadcasts =
            org.robolectric.shadows.ShadowApplication.getInstance().getBroadcastIntents();
        return broadcasts.isEmpty() ? null : broadcasts.get(broadcasts.size() - 1);
    }

    private SwipeDismissFrame layoutFrame(int width, int height) {
        SwipeDismissFrame frame = new SwipeDismissFrame(context);
        frame.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        frame.layout(0, 0, width, height);
        return frame;
    }

    private static MotionEvent down(float x, float y) {
        return MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, x, y, 0);
    }

    private static MotionEvent move(float x, float y) {
        return MotionEvent.obtain(0L, 16L, MotionEvent.ACTION_MOVE, x, y, 0);
    }

    private static MotionEvent up(float x, float y) {
        return MotionEvent.obtain(0L, 32L, MotionEvent.ACTION_UP, x, y, 0);
    }

    private static TextView header(NotificationCardSurface.Content content) {
        return (TextView) content.shell.getChildAt(0);
    }

    private static SwipeDismissFrame swipeFrame(LinearLayout shell, int index) {
        int seen = 0;
        for (int i = 0; i < shell.getChildCount(); i++) {
            View child = shell.getChildAt(i);
            if (child instanceof SwipeDismissFrame) {
                if (seen++ == index) return (SwipeDismissFrame) child;
            }
        }
        throw new AssertionError("no swipe frame at " + index);
    }

    private static void collectText(View view, List<String> out) {
        if (view instanceof TextView) out.add(((TextView) view).getText().toString());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectText(group.getChildAt(i), out);
        }
    }

    private static List<String> buttonTitles(View view) {
        List<String> titles = new ArrayList<>();
        collectButtons(view, titles);
        return titles;
    }

    private static void collectButtons(View view, List<String> out) {
        if (view instanceof Button) out.add(((Button) view).getText().toString());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectButtons(group.getChildAt(i), out);
        }
    }

    private static Button button(View root, String title) {
        Button found = findButton(root, title);
        if (found == null) throw new AssertionError("no button titled " + title);
        return found;
    }

    @androidx.annotation.Nullable
    private static Button findButton(View view, String title) {
        if (view instanceof Button && title.contentEquals(((Button) view).getText())) {
            return (Button) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findButton(group.getChildAt(i), title);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** A host with no launcher behind it: flat colours, and scheduling the test drives by hand. */
    private static final class RecordingHost implements NotificationCardSurface.Host {
        private final Context context;
        final List<String> cancelled = new ArrayList<>();
        final Set<String> activePackages = new HashSet<>();
        private final List<Runnable> pending = new ArrayList<>();

        RecordingHost(Context context) {
            this.context = context;
        }

        void runPending() {
            List<Runnable> due = new ArrayList<>(pending);
            pending.clear();
            for (Runnable runnable : due) runnable.run();
        }

        @androidx.annotation.NonNull @Override public Context context() { return context; }
        @Override public int dp(int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
        @Override public float density() {
            return context.getResources().getDisplayMetrics().density;
        }
        @Override public int textColor() { return 0xFFFFFFFF; }
        @Override public int subtleTextColor() { return 0xFFAAAAAA; }
        @Override public int panelColor() { return 0xFF202020; }
        @Override public int outlineColor() { return 0xFF404040; }
        @Override public int highlightAccentColor() { return 0xFF8080FF; }
        @Override public int sendButtonTextColor() { return 0xFF000000; }
        @Override public int sendButtonBackgroundColor() { return 0xFFC0C0FF; }
        @Override public void post(@androidx.annotation.NonNull Runnable action) {
            pending.add(action);
        }
        @Override public void postDelayed(@androidx.annotation.NonNull Runnable action,
                                          long delayMs) {
            pending.add(action);
        }
        @Override public void cancelNotification(@androidx.annotation.NonNull String key) {
            cancelled.add(key);
        }
        @Override public boolean hasActiveNotifications(
            @androidx.annotation.NonNull String packageName) {
            return activePackages.contains(packageName);
        }
    }

    private static final class RecordingListener implements NotificationCardSurface.Listener {
        final List<StatusBarNotification> dismissed = new ArrayList<>();
        final List<String> repliesSent = new ArrayList<>();
        int contentIntents;
        int actionsInvoked;
        int popupDismissRequests;
        int composerOpenCount;
        int imeRequests;
        @androidx.annotation.Nullable EditText composer;

        @Override public void onContentIntentSent(
            @androidx.annotation.NonNull StatusBarNotification sbn, boolean sent) {
            contentIntents++;
        }
        @Override public void onActionInvoked(
            @androidx.annotation.NonNull StatusBarNotification sbn,
            @androidx.annotation.NonNull Notification.Action action, boolean sent) {
            actionsInvoked++;
        }
        @Override public void onCardDismissed(
            @androidx.annotation.NonNull StatusBarNotification sbn) {
            dismissed.add(sbn);
        }
        @Override public void onReplyComposerOpened(
            @androidx.annotation.NonNull EditText editor) {
            composer = editor;
            composerOpenCount++;
        }
        @Override public void onReplyImeRequested(
            @androidx.annotation.NonNull EditText editor) {
            imeRequests++;
        }
        @Override public void onReplySent(
            @androidx.annotation.NonNull StatusBarNotification sbn,
            @androidx.annotation.NonNull CharSequence text) {
            repliesSent.add(text.toString());
        }
        @Override public void onPopupDismissRequested() {
            popupDismissRequests++;
        }
    }

    private static final class RecordingBadgeStyle implements NotificationBadgeFrame.Style {
        Set<String> active = new HashSet<>(Collections.singletonList("com.signal"));
        int fillReads;

        @Override public boolean badgesEnabled() { return true; }
        @androidx.annotation.NonNull @Override public Set<String> activeBadgePackages() {
            return active;
        }
        @Override public int badgeFillColor() { fillReads++; return 0xFFFF0000; }
        @Override public int badgeStrokeColor() { return 0xFF000000; }
        @Override public int iconSizePx() { return 48; }
        @Override public float density() { return 3f; }
    }
}
