package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.HostedFloatSlot;

/**
 * The Terminal widget: a live shell on a Home page, typed into in place. The widget is the frame
 * of a hosted Home float ({@link HostedFloatSlot}): its pane is the terminal pane controller's,
 * put in here while the widget is on screen and taken out when it leaves, so the shell keeps
 * running off screen and nothing is drawn for it there. Its size is the terminal's size: resizing
 * the widget in edit mode changes the rows and columns.
 *
 * <p>Every size is one layout, the pane filling the card, so it is never zoomed. While its shell
 * has ended the card says so, and a tap starts a new one.</p>
 */
public final class TerminalWidgetView extends BuiltinWidgetView implements HostedFloatSlot {

    /** The lines the picker's card shows: a prompt, its answer, and the next prompt. */
    private static final String PREVIEW_TEXT = "~ $ ls\nnotes  projects\n~ $ █";

    @Nullable private FrameLayout paneFrame;
    @Nullable private TextView endedLabel;
    private boolean ended;

    public TerminalWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                              @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.TERMINAL, services, style);
    }

    @Override protected boolean zoomsToFill(@NonNull BuiltinWidgetSpan span) { return false; }

    /** One layout for every size: a resize is the terminal's new size, not a new card. */
    @Override protected boolean rebuildsForSpan() { return false; }

    @Override
    protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                           @NonNull BuiltinWidgetUi ui) {
        BuiltinWidgetStyle style = style();
        if (isPreview()) {
            TextView sample = ui.mono(PREVIEW_TEXT, 11f);
            sample.setSingleLine(false);
            sample.setMaxLines(3);
            sample.setTextColor(style.onSurface);
            sample.setGravity(Gravity.START | Gravity.BOTTOM);
            sample.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
            frame.addView(sample, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            paneFrame = null;
            endedLabel = null;
            return;
        }
        // The card's rim is drawn under the content; the terminal's ground stops inside it.
        int rim = style.hasRim() ? (int) Math.ceil(style.rimWidthPx) : 0;
        FrameLayout pane = new FrameLayout(getContext());
        FrameLayout.LayoutParams paneParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        paneParams.setMargins(rim, rim, rim, rim);
        frame.addView(pane, paneParams);
        paneFrame = pane;
        TextView label = ui.sans(getResources().getText(R.string.terminal_widget_ended), 13f, false);
        label.setSingleLine(false);
        label.setMaxLines(3);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(style.onSurfaceVariant);
        label.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));
        label.setVisibility(ended ? VISIBLE : GONE);
        frame.addView(label, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        endedLabel = label;
    }

    @Override protected void onStart() {
        if (widgetId() != NO_WIDGET_ID) services.host().attachTerminalWidget(widgetId(), this);
    }

    @Override protected void onStop() {
        if (widgetId() != NO_WIDGET_ID) services.host().detachTerminalWidget(widgetId(), this);
    }

    /** A tap reaches the card only where the terminal is not: on the ended card it starts a shell. */
    @Override protected void onTap() {
        if (ended && widgetId() != NO_WIDGET_ID) services.host().restartTerminalWidget(widgetId());
    }

    /** The shell ended and none runs here ({@code true}), or one does again. */
    public void showEnded(boolean value) {
        if (ended == value) return;
        ended = value;
        if (endedLabel != null) endedLabel.setVisibility(value ? VISIBLE : GONE);
    }

    /** Whether the card shows that its shell ended. */
    public boolean isShowingEnded() { return ended; }

    // ----- hosted float slot ----------------------------------------------------------------

    @Override @NonNull public FrameLayout paneFrame() {
        if (paneFrame == null) {
            // Asked before the first build: the frame the next build keeps is made now.
            paneFrame = new FrameLayout(getContext());
            content().addView(paneFrame, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        return paneFrame;
    }

    /** The cell the grid lays this widget out in: that is what moves, so the whole card rises. */
    @Override @NonNull public View liftTarget() {
        return getParent() instanceof View ? (View) getParent() : this;
    }

    @Override public float cornerRadiusPx() { return style().cornerRadiusPx; }

    /** The card's ground: whole on the solid card, the Look's tint on its glass. */
    @Override public int groundAlpha() { return cardAlpha(); }

    /**
     * The card's ground changed (Glass worn or put down, the Look's opacity moved): the pane in
     * it is dressed again, in place, as it is when it comes on screen.
     */
    @Override protected void onCardChanged() {
        if (isStarted() && widgetId() != NO_WIDGET_ID) {
            services.host().attachTerminalWidget(widgetId(), this);
        }
    }
}
