package com.termux.app.launcher.notifications;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.popup.AnchoredMenuGeometry;

import java.util.ArrayList;
import java.util.List;

/**
 * The mirrored-notification stack: it turns live {@link StatusBarNotification}s into cards, runs
 * swipe-to-dismiss and the inline reply composer, and sends the actions the user picks.
 *
 * <p>The surface builds and drives content only — the window it lives in belongs to the host, which
 * owns focusability, its own dim and the IME handoff a reply field needs. Everything the surface
 * needs from outside comes through {@link Host} (materials, metrics, scheduling, and the two
 * notification-service operations) and everything it has to report goes out through
 * {@link Listener}, so a test can build and drive a full card stack with no listener service
 * connected.
 */
public final class NotificationCardSurface {

    private static final String LOG_TAG = "NotificationCardSurface";

    /** Materials, metrics and services the surface borrows from its host. */
    public interface Host {
        @NonNull
        Context context();

        /** Rounded dp, matching the host's own conversion. */
        int dp(int value);

        /** Display density, for geometry the popup module shares. */
        float density();

        int textColor();

        int subtleTextColor();

        int panelColor();

        int outlineColor();

        /** Accent used for the auto-opened card's rim. */
        int highlightAccentColor();

        int sendButtonTextColor();

        int sendButtonBackgroundColor();

        void post(@NonNull Runnable action);

        void postDelayed(@NonNull Runnable action, long delayMs);

        /** Cancels the system notification behind a card. */
        void cancelNotification(@NonNull String key);

        /** Whether the badge store still holds notifications for this package. */
        boolean hasActiveNotifications(@NonNull String packageName);
    }

    /** What the host has to react to: bookkeeping, IME and popup lifecycle all live up there. */
    public interface Listener {
        /** A card's message body was tapped and its content intent sent. */
        void onContentIntentSent(@NonNull StatusBarNotification sbn, boolean sent);

        /** A non-reply action button was tapped. */
        void onActionInvoked(@NonNull StatusBarNotification sbn,
                            @NonNull Notification.Action action, boolean sent);

        /** A card was cleared, by its Dismiss button or by a swipe. */
        void onCardDismissed(@NonNull StatusBarNotification sbn);

        /** A composer just replaced a card's action row: it is the live reply editor now. */
        void onReplyComposerOpened(@NonNull EditText editor);

        /** The composer was touched again and wants the IME back. */
        void onReplyImeRequested(@NonNull EditText editor);

        /** A reply went out through its RemoteInput. */
        void onReplySent(@NonNull StatusBarNotification sbn, @NonNull CharSequence text);

        /** The popup has served its purpose and should be taken away. */
        void onPopupDismissRequested();
    }

    /** One reply-capable card, kept so the host can auto-open the newest conversation. */
    public static final class ReplyTarget {
        /** The whole card, so an auto-opened composer can say which conversation it belongs to. */
        @NonNull public final View card;
        @NonNull public final FrameLayout actionHost;
        @NonNull public final StatusBarNotification sbn;
        @NonNull public final Notification.Action action;
        @NonNull public final RemoteInput[] remoteInputs;
        @NonNull public final RemoteInput freeform;
        @Nullable public final CharSequence recipient;

        ReplyTarget(@NonNull View card, @NonNull FrameLayout actionHost,
                    @NonNull StatusBarNotification sbn, @NonNull Notification.Action action,
                    @NonNull RemoteInput[] remoteInputs, @NonNull RemoteInput freeform,
                    @Nullable CharSequence recipient) {
            this.card = card;
            this.actionHost = actionHost;
            this.sbn = sbn;
            this.action = action;
            this.remoteInputs = remoteInputs;
            this.freeform = freeform;
            this.recipient = recipient;
        }
    }

    /** The built stack: the view to hand to the popup, plus the composers it can open. */
    public static final class Content {
        @NonNull public final LinearLayout shell;
        @NonNull public final List<ReplyTarget> replyTargets;

        Content(@NonNull LinearLayout shell, @NonNull List<ReplyTarget> replyTargets) {
            this.shell = shell;
            this.replyTargets = replyTargets;
        }
    }

    @NonNull private final Host host;
    @NonNull private final Listener listener;

    public NotificationCardSurface(@NonNull Host host, @NonNull Listener listener) {
        this.host = host;
        this.listener = listener;
    }

    // ------------------------------------------------------------------ content

    /**
     * Builds the whole stack: app header, then one card per notification with dividers between.
     */
    @NonNull
    public Content buildContent(@NonNull CharSequence label, @Nullable Drawable headerIcon,
                                @NonNull List<StatusBarNotification> notifications) {
        Context context = host.context();
        LinearLayout shell = new LinearLayout(context);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView header = new TextView(context);
        header.setText(notifications.size() == 1
            ? label : label + " · " + notifications.size());
        header.setTextColor(host.textColor());
        header.setTextSize(13f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Drawable icon = headerIcon;
        if (icon != null && icon.getConstantState() != null) {
            icon = icon.getConstantState().newDrawable(context.getResources()).mutate();
        }
        if (icon != null) {
            icon.setBounds(0, 0, dp(24), dp(24));
            header.setCompoundDrawablesRelative(icon, null, null, null);
            header.setCompoundDrawablePadding(dp(9));
        }
        shell.addView(header, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        List<ReplyTarget> replyTargets = new ArrayList<>();
        for (int i = 0; i < notifications.size(); i++) {
            if (i > 0) addDivider(shell);
            ReplyTarget target = addCard(shell, notifications.get(i));
            if (target != null) replyTargets.add(target);
        }
        return new Content(shell, replyTargets);
    }

    /** Appends one card, returning its first free-form reply action if it has one. */
    @Nullable
    public ReplyTarget addCard(@NonNull LinearLayout shell, @NonNull StatusBarNotification sbn) {
        Context context = host.context();
        Notification notification = sbn.getNotification();
        if (notification == null) return null;
        Bundle extras = notification.extras;
        CharSequence title = firstText(extras,
            Notification.EXTRA_TITLE, Notification.EXTRA_TITLE_BIG);
        CharSequence text = firstText(extras,
            Notification.EXTRA_BIG_TEXT, Notification.EXTRA_TEXT, Notification.EXTRA_SUB_TEXT);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(2), dp(7), dp(2), dp(2));

        LinearLayout message = new LinearLayout(context);
        message.setOrientation(LinearLayout.VERTICAL);
        message.setPadding(0, 0, 0, dp(2));
        if (!TextUtils.isEmpty(title)) {
            TextView titleView = new TextView(context);
            titleView.setText(title);
            titleView.setTextColor(host.textColor());
            titleView.setTextSize(14f);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setMaxLines(2);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            message.addView(titleView);
        }
        if (!TextUtils.isEmpty(text)) {
            TextView textView = new TextView(context);
            textView.setText(text);
            textView.setTextColor(host.subtleTextColor());
            textView.setTextSize(12.5f);
            textView.setMaxLines(4);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textLp.topMargin = dp(3);
            message.addView(textView, textLp);
        }
        if (notification.contentIntent != null) {
            message.setClickable(true);
            message.setOnClickListener(v -> {
                boolean sent = sendIntent(notification.contentIntent, null, null);
                if ((notification.flags & Notification.FLAG_AUTO_CANCEL) != 0) {
                    host.cancelNotification(sbn.getKey());
                }
                listener.onContentIntentSent(sbn, sent);
                listener.onPopupDismissRequested();
            });
        }
        card.addView(message, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout actionHost = new FrameLayout(context);
        LinearLayout actionRow = new LinearLayout(context);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        HorizontalScrollView actionScroller = new HorizontalScrollView(context);
        actionScroller.setFillViewport(false);
        actionScroller.setHorizontalScrollBarEnabled(false);
        actionScroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        actionScroller.addView(actionRow, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        actionHost.addView(actionScroller, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int actionIndex = 0;
        ReplyTarget replyTarget = null;
        if (notification.actions != null) {
            for (Notification.Action action : notification.actions) {
                if (action == null || action.actionIntent == null) continue;
                String actionTitle = TextUtils.isEmpty(action.title) ? "Action" : action.title.toString();
                Button actionButton = actionButton(actionTitle);
                RemoteInput[] remoteInputs = action.getRemoteInputs();
                RemoteInput freeform = firstFreeformRemoteInput(remoteInputs);
                if (freeform != null) {
                    if (replyTarget == null) {
                        replyTarget = new ReplyTarget(
                            card, actionHost, sbn, action, remoteInputs, freeform, title);
                    }
                    actionButton.setOnClickListener(v ->
                        beginReply(actionHost, sbn, action, remoteInputs, freeform, title));
                } else {
                    actionButton.setOnClickListener(v -> {
                        boolean sent = sendIntent(action.actionIntent, null, null);
                        listener.onActionInvoked(sbn, action, sent);
                    });
                }
                actionRow.addView(actionButton, actionLayoutParams(actionIndex++ > 0));
            }
        }

        if (sbn.isClearable()) {
            Button dismiss = actionButton("Dismiss");
            dismiss.setOnClickListener(v -> dismissCard(card, sbn));
            actionRow.addView(dismiss, actionLayoutParams(actionIndex++ > 0));
        }
        if (actionIndex > 0) {
            LinearLayout.LayoutParams hostLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hostLp.topMargin = dp(3);
            card.addView(actionHost, hostLp);
        }
        // Replying from the system notification shade leaves WhatsApp's own notification (and this
        // card, which mirrors it) sitting there afterwards — clearable cards get a swipe-to-dismiss
        // wrapper so there is a way to clear that stale card without hunting for the small Dismiss
        // button on a touch target this narrow.
        SwipeDismissFrame wrapper = new SwipeDismissFrame(context);
        wrapper.setSwipeEnabled(sbn.isClearable());
        wrapper.setOnDismiss(() -> dismissCard(card, sbn));
        wrapper.addView(card, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        shell.addView(wrapper, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return replyTarget;
    }

    /** Cancels the underlying system notification and drops its card once the popup is idle. */
    public void dismissCard(@NonNull View card, @NonNull StatusBarNotification sbn) {
        host.cancelNotification(sbn.getKey());
        card.setVisibility(View.GONE);
        listener.onCardDismissed(sbn);
        host.postDelayed(() -> {
            if (!host.hasActiveNotifications(sbn.getPackageName())) {
                listener.onPopupDismissRequested();
            }
        }, 180L);
    }

    private void addDivider(@NonNull LinearLayout shell) {
        View divider = new View(host.context());
        divider.setBackgroundColor(withAlphaComponent(host.outlineColor(), 0x42));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1)));
        lp.setMargins(0, dp(5), 0, 0);
        shell.addView(divider, lp);
    }

    // ------------------------------------------------------------------ reply

    /** Opens the composer for a reply target the host picked (a swipe's auto-reply, say). */
    public void beginReply(@NonNull ReplyTarget target) {
        beginReply(target.actionHost, target.sbn, target.action, target.remoteInputs,
            target.freeform, target.recipient);
    }

    private void beginReply(
        @NonNull FrameLayout actionHost,
        @NonNull StatusBarNotification sbn,
        @NonNull Notification.Action action,
        @NonNull RemoteInput[] remoteInputs,
        @NonNull RemoteInput freeform,
        @Nullable CharSequence recipient
    ) {
        Context context = host.context();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(3), dp(4), dp(3));
        GradientDrawable composerBg = new GradientDrawable();
        composerBg.setCornerRadius(dp(12));
        composerBg.setColor(withAlphaComponent(host.panelColor(), 0xF4));
        composerBg.setStroke(Math.max(1, dp(1)),
            withAlphaComponent(host.outlineColor(), 0x70));
        row.setBackground(composerBg);

        EditText reply = new EditText(context);
        reply.setSingleLine(true);
        if (!TextUtils.isEmpty(recipient)) {
            reply.setHint("Reply to " + recipient);
        } else {
            reply.setHint(TextUtils.isEmpty(freeform.getLabel()) ? "Reply" : freeform.getLabel());
        }
        reply.setTextColor(host.textColor());
        reply.setHintTextColor(host.subtleTextColor());
        reply.setTextSize(13f);
        reply.setPadding(dp(10), 0, dp(8), 0);
        reply.setBackgroundColor(Color.TRANSPARENT);
        reply.setImeOptions(EditorInfo.IME_ACTION_SEND);
        reply.setOnTouchListener((view, event) -> {
            if (event != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                listener.onReplyImeRequested(reply);
            }
            return false;
        });
        row.addView(reply, new LinearLayout.LayoutParams(0, dp(40), 1f));

        Button send = sendButton();
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(
            dp(62), dp(36));
        row.addView(send, sendLp);
        Runnable sendReply = () -> sendReply(sbn, action, remoteInputs, reply.getText().toString());
        send.setOnClickListener(v -> sendReply.run());
        reply.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEND) return false;
            sendReply.run();
            return true;
        });
        send.setEnabled(false);
        send.setAlpha(0.45f);
        reply.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean enabled = !TextUtils.isEmpty(s == null ? null : s.toString().trim());
                send.setEnabled(enabled);
                send.setAlpha(enabled ? 1f : 0.45f);
            }
            @Override public void afterTextChanged(Editable editable) {}
        });
        actionHost.removeAllViews();
        actionHost.addView(row, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        listener.onReplyComposerOpened(reply);
    }

    /**
     * Fills the action's free-form RemoteInputs with {@code text} and fires its intent. Blank text
     * is a no-op — the Send button is disabled for it, and the IME action can still arrive.
     */
    public boolean sendReply(@NonNull StatusBarNotification sbn,
                             @NonNull Notification.Action action,
                             @NonNull RemoteInput[] remoteInputs,
                             @Nullable String text) {
        String value = text == null ? "" : text.trim();
        if (TextUtils.isEmpty(value)) return false;
        if (!sendIntent(action.actionIntent, remoteInputs, value)) return false;
        listener.onReplySent(sbn, value);
        listener.onPopupDismissRequested();
        return true;
    }

    /**
     * Mark the card whose composer was opened, and scroll it into view. With several conversations
     * on screen the composer alone does not say which one it belongs to; the enclosing ScrollView is
     * created by the popup module, so requestRectangleOnScreen is the only handle that does not need
     * to know about it.
     */
    public void highlightReplyCard(@NonNull ReplyTarget target) {
        View card = target.card;
        GradientDrawable highlight = new GradientDrawable();
        highlight.setCornerRadius(dp(12));
        highlight.setColor(withAlphaComponent(host.panelColor(), 0x38));
        highlight.setStroke(Math.max(1, dp(1)),
            withAlphaComponent(host.highlightAccentColor(), 0xB0));
        card.setBackground(highlight);
        // Padding, or the stroke clips the title and the action row.
        card.setPadding(card.getPaddingLeft() + dp(4), card.getPaddingTop(),
            card.getPaddingRight() + dp(4), card.getPaddingBottom() + dp(3));
        card.post(() -> card.requestRectangleOnScreen(
            new Rect(0, 0, card.getWidth(), card.getHeight()), false));
    }

    // ------------------------------------------------------------------ rules

    /**
     * Whether the swipe should open a reply composer straight away.
     *
     * <p>Notifications arrive newest-first, so target 0 is the latest conversation for this app —
     * exactly what swiping its icon asks for. The old rule required exactly one reply-capable
     * notification, which meant the gesture silently did nothing for any app the user actually talks
     * to on.
     */
    public static boolean shouldAutoOpenReply(int replyTargetCount) {
        return replyTargetCount >= 1;
    }

    /**
     * A notification arriving must not throw away a half-typed reply. Rebuilding the popup is the
     * right response to the list changing — unless the user is mid-compose, in which case the change
     * they care about is the one under their fingers.
     */
    public static boolean shouldDismissOnKeyChange(boolean keysChanged, boolean composing) {
        return keysChanged && !composing;
    }

    @Nullable
    public static RemoteInput firstFreeformRemoteInput(@Nullable RemoteInput[] inputs) {
        if (inputs == null) return null;
        for (RemoteInput input : inputs) {
            if (input != null && input.getAllowFreeFormInput()) return input;
        }
        return null;
    }

    @Nullable
    public static CharSequence firstText(@Nullable Bundle extras, @NonNull String... keys) {
        if (extras == null) return null;
        for (String key : keys) {
            CharSequence value = extras.getCharSequence(key);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return null;
    }

    // ------------------------------------------------------------------ width

    /** The popup width this stack wants: half-screen for one card, wider when actions demand it. */
    public int preferredWidth(@NonNull List<StatusBarNotification> notifications) {
        float density = host.density();
        int screenWidth = host.context().getResources().getDisplayMetrics().widthPixels;
        int preferredWidth = notifications.size() > 1
            ? screenWidth - dp(32) : screenWidth / 2;
        int requiredActionWidth = 0;
        boolean hasReplyAction = false;
        Paint actionTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        actionTextPaint.setTextSize(
            11.5f * host.context().getResources().getDisplayMetrics().scaledDensity);
        actionTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        for (StatusBarNotification sbn : notifications) {
            Notification notification = sbn.getNotification();
            if (notification == null) continue;
            int rowWidth = dp(28);
            int actionCount = 0;
            if (notification.actions != null) {
                for (Notification.Action action : notification.actions) {
                    if (action == null || action.actionIntent == null) continue;
                    String title = TextUtils.isEmpty(action.title) ? "Action" : action.title.toString();
                    rowWidth += actionMeasuredWidth(actionTextPaint, title);
                    if (firstFreeformRemoteInput(action.getRemoteInputs()) != null)
                        hasReplyAction = true;
                    actionCount++;
                }
            }
            if (sbn.isClearable()) {
                rowWidth += actionMeasuredWidth(actionTextPaint, "Dismiss");
                actionCount++;
            }
            if (actionCount > 1) rowWidth += dp(4) * (actionCount - 1);
            requiredActionWidth = Math.max(requiredActionWidth, rowWidth);
        }
        if (hasReplyAction && notifications.size() == 1)
            preferredWidth = Math.max(preferredWidth, (int) (screenWidth * 0.72f));
        return adaptiveWidth(
            preferredWidth,
            requiredActionWidth,
            AnchoredMenuGeometry.minWidth(screenWidth, false, density),
            AnchoredMenuGeometry.maxWidth(screenWidth, density)
        );
    }

    public static int adaptiveWidth(
        int preferredWidth,
        int requiredActionWidth,
        int minimumWidth,
        int maximumWidth
    ) {
        return clamp(Math.max(preferredWidth, requiredActionWidth), minimumWidth, maximumWidth);
    }

    private int actionMeasuredWidth(@NonNull Paint paint, @NonNull String title) {
        return Math.max(dp(52), (int) Math.ceil(paint.measureText(title)) + dp(20));
    }

    // ------------------------------------------------------------------ internals

    private boolean sendIntent(
        @NonNull PendingIntent pendingIntent,
        @Nullable RemoteInput[] remoteInputs,
        @Nullable CharSequence reply
    ) {
        try {
            Intent fillIn = null;
            if (remoteInputs != null && reply != null) {
                fillIn = new Intent();
                Bundle results = new Bundle();
                for (RemoteInput input : remoteInputs) {
                    if (input != null && input.getAllowFreeFormInput()) {
                        results.putCharSequence(input.getResultKey(), reply);
                    }
                }
                RemoteInput.addResultsToIntent(remoteInputs, fillIn, results);
            }
            pendingIntent.send(host.context(), 0, fillIn);
            return true;
        } catch (PendingIntent.CanceledException exception) {
            Log.d(LOG_TAG, "Notification action is no longer available: " + exception.getMessage());
            return false;
        }
    }

    @NonNull
    private Button actionButton(@NonNull String title) {
        Context context = host.context();
        Button button = new Button(context);
        button.setText(title);
        button.setTextColor(host.textColor());
        button.setTextSize(11.5f);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        TypedValue selectableBackground = new TypedValue();
        if (context.getTheme().resolveAttribute(
            android.R.attr.selectableItemBackgroundBorderless, selectableBackground, true)
            && selectableBackground.resourceId != 0) {
            button.setBackgroundResource(selectableBackground.resourceId);
        } else {
            button.setBackgroundColor(0x00000000);
        }
        return button;
    }

    @NonNull
    private Button sendButton() {
        Button send = new Button(host.context());
        send.setText("Send");
        send.setAllCaps(false);
        send.setSingleLine(true);
        send.setTextSize(11.5f);
        send.setTypeface(Typeface.DEFAULT_BOLD);
        send.setTextColor(host.sendButtonTextColor());
        send.setMinWidth(0);
        send.setMinimumWidth(0);
        send.setMinHeight(0);
        send.setMinimumHeight(0);
        send.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(9));
        background.setColor(host.sendButtonBackgroundColor());
        send.setBackground(background);
        return send;
    }

    @NonNull
    private LinearLayout.LayoutParams actionLayoutParams(boolean withStartGap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        if (withStartGap) lp.leftMargin = dp(4);
        return lp;
    }

    private int dp(int value) {
        return host.dp(value);
    }

    private static int withAlphaComponent(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
