package com.termux.app.terminal;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;

import java.util.ArrayList;
import java.util.List;

import juloo.keyboard2.KeyValue;

/**
 * The one in-app surface every terminal prompt is shown on, in place of {@code AlertDialog} and
 * {@code PopupMenu}.
 *
 * <p>A dialog or a popup opens a window of its own, and a window that takes focus pulls the
 * {@code InputConnection} off {@code TerminalView} and summons the system IME. That is the swap the
 * in-app keyboard exists to avoid — the session browser used to collapse the keyboard and resize the
 * terminal twice just to type three letters into its search box. This plane is a view inside the
 * activity, exactly like {@link TerminalCommandPaletteController} and the rename chip: focus never
 * moves, nothing here is an {@code EditText}, and typing arrives through the same three channels a
 * focusless overlay has to serve — the in-app keyboard's interceptor, hardware key events, and text
 * a system IME commits.
 *
 * <p>Sheets stack rather than replace: the workspace picker opens a delete confirmation on top of
 * itself, and popping that has to leave the picker where it was.
 */
public final class TerminalSheetController
    implements TerminalKeyEventHandler.KeyValueInterceptor {

    private static final long ENTER_DURATION_MS = 170L;
    private static final long EXIT_DURATION_MS = 110L;
    /** Side inset of a card, and the vertical inset of a full-height one. */
    private static final float SIDE_INSET_DP = 14f;
    private static final float VERTICAL_INSET_DP = 28f;
    /** An anchored card is a menu, not a page: it takes the width its rows need and no more. */
    private static final float ANCHORED_MAX_WIDTH_DP = 260f;
    /** How far a page key moves a list selection, in rows. */
    private static final int PAGE_ARROW_ROWS = 5;
    private static final float CORNER_RADIUS_DP = 22f;
    private static final float CARD_PADDING_DP = 18f;

    /** Where a sheet's typing goes. A sheet without one swallows keystrokes rather than leaking. */
    public interface TextSink {

        void onText(@NonNull String text);

        void onBackspace();

        /** ⏎ while this sheet is on top. @return true when the stroke was spent here. */
        boolean onCommit();

        /**
         * An arrow or page key aimed at this sheet: -1 is up, +1 is down, and a page key multiplies.
         *
         * <p>Default no-op, because most sheets are a field and some buttons. A sheet with a list
         * overrides it and moves its selection, which is the only way to walk that list from the
         * in-app keyboard, an extra-keys row or a hardware arrow — none of which can tap a row.
         *
         * @return true when the stroke was spent here.
         */
        default boolean onArrow(int delta) {
            return false;
        }
    }

    /** Where a card sits on the plane. */
    public static final class Placement {
        private static final Placement CENTERED = new Placement(null, false);

        @Nullable final PointF anchor;
        final boolean docked;

        private Placement(@Nullable PointF anchor, boolean docked) {
            this.anchor = anchor;
            this.docked = docked;
        }

        /** The default: a centred card with side and vertical insets. */
        @NonNull
        public static Placement centered() {
            return CENTERED;
        }

        /** A compact menu at a touch point, clamped inside the plane. */
        @NonNull
        public static Placement at(@Nullable PointF screenPoint) {
            return screenPoint == null ? CENTERED : new Placement(screenPoint, false);
        }

        /**
         * A bar the width of the dock, sitting directly above it and growing upward.
         *
         * <p>For a surface that belongs to the terminal rather than over it: the search bar lands
         * where the dock already is, so the eye does not have to move and the terminal stays visible
         * above it.
         */
        @NonNull
        public static Placement aboveDock() {
            return new Placement(null, true);
        }
    }

    /**
     * A one-line label typed from the key channel — the replacement for every {@code EditText} the
     * migrated prompts used to carry. Renders the draft with a caret, or the hint while empty.
     */
    public static final class TextField implements TextSink {

        public interface OnChanged {
            void onChanged(@NonNull String value);
        }

        @NonNull private final TextView mView;
        @NonNull private final String mHint;
        @Nullable private final OnChanged mOnChanged;
        @Nullable private final Runnable mOnCommit;
        @NonNull private String mValue = "";

        public TextField(@NonNull TextView view, @NonNull String hint,
                         @Nullable OnChanged onChanged) {
            this(view, hint, onChanged, null);
        }

        public TextField(@NonNull TextView view, @NonNull String hint,
                         @Nullable OnChanged onChanged, @Nullable Runnable onCommit) {
            mView = view;
            mHint = hint;
            mOnChanged = onChanged;
            mOnCommit = onCommit;
            render(false);
        }

        @NonNull
        public String value() {
            return mValue;
        }

        @Override
        public void onText(@NonNull String text) {
            if (text.isEmpty()) return;
            mValue = mValue + text;
            render(true);
        }

        @Override
        public void onBackspace() {
            if (mValue.isEmpty()) return;
            // By code point, not by char: a name can carry an emoji, and halving a surrogate pair
            // would leave the field holding an unpaired one.
            mValue = mValue.substring(0, mValue.offsetByCodePoints(mValue.length(), -1));
            render(true);
        }

        @Override
        public boolean onCommit() {
            if (mOnCommit == null) return false;
            mOnCommit.run();
            return true;
        }

        private void render(boolean notify) {
            if (mValue.isEmpty()) {
                mView.setText(mHint);
                mView.setAlpha(0.5f);
            } else {
                mView.setText(mValue + "▏");
                mView.setAlpha(1f);
            }
            if (notify && mOnChanged != null) mOnChanged.onChanged(mValue);
        }
    }

    /** One card on the plane, with whatever the caller wants to be told when it goes away. */
    private static final class Sheet {
        @NonNull final View card;
        @Nullable final TextSink sink;
        @Nullable final Runnable onDismiss;
        /** True while this card hides the one under it; see {@link #show}'s {@code coverPrevious}. */
        final boolean coversPrevious;

        Sheet(@NonNull View card, @Nullable TextSink sink, @Nullable Runnable onDismiss,
              boolean coversPrevious) {
            this.card = card;
            this.sink = sink;
            this.onDismiss = onDismiss;
            this.coversPrevious = coversPrevious;
        }
    }

    @NonNull private final TermuxActivity mActivity;
    @NonNull private final List<Sheet> mStack = new ArrayList<>();
    private final float mDensity;

    @Nullable private FrameLayout mHost;
    @Nullable private FrameLayout mStackHost;
    /** Set by {@link #show} for the card it is about to build; read once by {@link #buildCard}. */
    @NonNull private Placement mPendingPlacement = Placement.centered();

    public TerminalSheetController(@NonNull TermuxActivity activity) {
        mActivity = activity;
        mDensity = activity.getResources().getDisplayMetrics().density;
    }

    public boolean isOpen() {
        return !mStack.isEmpty();
    }

    /** How many cards are stacked; what the stacking tests read. */
    public int depth() {
        return mStack.size();
    }

    /** A wrap-height sheet: prompts, confirmations and menus. */
    public boolean show(@NonNull CharSequence title, @NonNull View content) {
        return show(title, content, false, null, null);
    }

    /**
     * @param fillHeight true for content that lays itself out against a known height — the session
     *                   browser's weighted list is unmeasurable inside a wrapping card.
     * @return false when the plane is not in the layout yet, so a caller that subscribed something
     *         to the sheet's lifetime can unwind rather than leak it.
     */
    public boolean show(@NonNull CharSequence title, @NonNull View content, boolean fillHeight,
                        @Nullable TextSink sink, @Nullable Runnable onDismiss) {
        return show(title, content, fillHeight, sink, onDismiss, false);
    }

    /**
     * @param coverPrevious hides the card underneath while this one is up. A submenu replaces the
     *                      menu it came from — two translucent cards on top of each other read as
     *                      one card with both sets of rows bleeding through, and expose both to
     *                      accessibility. A confirmation over a picker passes false: seeing what
     *                      the confirmation is about is the point.
     */
    public boolean show(@NonNull CharSequence title, @NonNull View content, boolean fillHeight,
                        @Nullable TextSink sink, @Nullable Runnable onDismiss,
                        boolean coverPrevious) {
        return show(title, content, fillHeight, sink, onDismiss, coverPrevious,
            Placement.centered());
    }

    /** @param anchor screen point to open at, or null to centre. */
    public boolean show(@NonNull CharSequence title, @NonNull View content, boolean fillHeight,
                        @Nullable TextSink sink, @Nullable Runnable onDismiss,
                        boolean coverPrevious, @Nullable PointF anchor) {
        return show(title, content, fillHeight, sink, onDismiss, coverPrevious,
            Placement.at(anchor));
    }

    /**
     * @param anchor screen coordinates the card should appear at, or null for a centred card. An
     *               anchored card is compact: it wraps its rows, is capped at
     *               {@link #ANCHORED_MAX_WIDTH_DP}, and is clamped inside the plane, so a menu opens
     *               under the finger that asked for it instead of taking over the screen.
     */
    public boolean show(@NonNull CharSequence title, @NonNull View content, boolean fillHeight,
                        @Nullable TextSink sink, @Nullable Runnable onDismiss,
                        boolean coverPrevious, @NonNull Placement placement) {
        if (!bindViews()) return false;
        mPendingPlacement = placement;
        if (mStack.isEmpty()) {
            // Two full-screen glass planes must never stack, and a sheet is the modal one: the
            // drawer and the FULL status pane yield, the same handoff the palette makes. Guarded on
            // the open check rather than reached through the lazy accessor, so a session that never
            // pulls the drawer down does not build one just because it opened a prompt.
            mActivity.closeFullStatusBarImmediate();
            if (mActivity.isAppDrawerOpen()) mActivity.getAppDrawerController().closeImmediate();
        }
        View card = buildCard(title, content, fillHeight);
        if (coverPrevious && !mStack.isEmpty())
            mStack.get(mStack.size() - 1).card.setVisibility(View.GONE);
        mStackHost.addView(card);
        mStack.add(new Sheet(card, sink, onDismiss, coverPrevious));
        mHost.setVisibility(View.VISIBLE);
        applyBackdropMaterial();
        // Only a sheet with somewhere for typing to land is worth summoning a keyboard for; a
        // confirmation is all buttons and would just push the terminal around.
        if (sink != null) mActivity.ensureInAppTypingKeyboard();
        mActivity.setTerminalSheetInterceptorActive(true);
        animateIn(card);
        return true;
    }

    /** Pops the top sheet. The stacking rule in one line: back and an outside tap both land here. */
    public void dismiss() {
        if (mStack.isEmpty()) return;
        Sheet top = mStack.remove(mStack.size() - 1);
        animateOut(top.card);
        if (top.coversPrevious && !mStack.isEmpty())
            mStack.get(mStack.size() - 1).card.setVisibility(View.VISIBLE);
        if (top.onDismiss != null) top.onDismiss.run();
        if (mStack.isEmpty()) onEmptied();
    }

    /**
     * Drops the {@code count} oldest cards and leaves everything above them alone.
     *
     * <p>For a menu whose row opens a card of its own: the menu has to go, but the card the row just
     * opened must not go with it. {@link #dismissAll()} took both down, which is how Select URL and
     * the kill-process confirmation ended up showing nothing at all.
     */
    public void dismissUnder(int count) {
        if (count <= 0) return;
        int limit = Math.min(count, mStack.size());
        for (int i = limit - 1; i >= 0; i--) {
            Sheet sheet = mStack.remove(i);
            animateOut(sheet.card);
            if (sheet.onDismiss != null) sheet.onDismiss.run();
        }
        // Whatever is left is on top now, so nothing may still be hidden behind a card that went.
        for (Sheet sheet : mStack) sheet.card.setVisibility(View.VISIBLE);
        if (mStack.isEmpty()) onEmptied();
    }

    /** Pops everything, for an action that has left the surfaces behind the stack meaningless. */
    public void dismissAll() {
        while (!mStack.isEmpty()) dismiss();
    }

    /** Drops the plane without animating, for pause, configuration change and destroy. */
    public void dismissImmediately() {
        for (Sheet sheet : mStack) {
            if (mStackHost != null) mStackHost.removeView(sheet.card);
            if (sheet.onDismiss != null) sheet.onDismiss.run();
        }
        mStack.clear();
        onEmptied();
    }

    /**
     * Back aimed at the plane. Closes one card, never the whole stack: a confirmation opened over
     * the workspace picker has to give the picker back rather than dropping the user on the
     * terminal.
     */
    public boolean onBackPressed() {
        if (mStack.isEmpty()) return false;
        dismiss();
        return true;
    }

    private void onEmptied() {
        mActivity.setTerminalSheetInterceptorActive(false);
        if (mHost != null) mHost.setVisibility(View.INVISIBLE);
        ImageView frost = mActivity.findViewById(R.id.terminal_sheet_wallpaper_backdrop);
        if (frost != null) {
            // Drop the full-screen frost bitmap while shut; the next show() rebuilds it.
            frost.setImageDrawable(null);
            frost.setVisibility(View.GONE);
        }
    }

    private boolean bindViews() {
        if (mStackHost != null) return true;
        FrameLayout host = mActivity.findViewById(R.id.terminal_sheet_host);
        FrameLayout stack = mActivity.findViewById(R.id.terminal_sheet_stack);
        if (host == null || stack == null) return false;
        // The plane must never take focus itself; the whole point of it is that TerminalView keeps
        // the InputConnection while a sheet is up.
        host.setFocusable(false);
        host.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    // Taps on the in-app keyboard are not "outside": the host covers the whole
                    // activity, keyboard included, and those keys are how the sheet is typed into.
                    // They must fall through unconsumed or the first keystroke closes the sheet.
                    return !mActivity.isPointOnInAppKeyboard(event.getRawX(), event.getRawY());
                case MotionEvent.ACTION_UP:
                    // Dismissed on the finished tap, not on DOWN: a gesture-nav back swipe delivers
                    // its DOWN to the app before the system claims the gesture with ACTION_CANCEL,
                    // and dismissing on DOWN would turn every Back into two dismissals.
                    dismiss();
                    return true;
                default:
                    return true;
            }
        });
        mHost = host;
        mStackHost = stack;
        return true;
    }

    /**
     * Picks the glass backdrop for this open: over the system wallpaper a pre-blurred wallpaper
     * frost (the live blur cannot see through the window there and renders grey mud), otherwise the
     * RealtimeBlurView blurring real window content.
     */
    private void applyBackdropMaterial() {
        ImageView frost = mActivity.findViewById(R.id.terminal_sheet_wallpaper_backdrop);
        View blur = mActivity.findViewById(R.id.terminal_sheet_blur);
        boolean frosted = frost != null && mActivity.applyCommandPaletteWallpaperFrost(frost);
        if (blur != null) blur.setVisibility(frosted ? View.GONE : View.VISIBLE);
    }

    @NonNull
    private View buildCard(@NonNull CharSequence title, @NonNull View content,
                           boolean fillHeight) {
        Context context = mStackHost.getContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(mActivity.buildTerminalSheetSurface());
        int padding = dp(CARD_PADDING_DP);
        card.setPadding(padding, padding, padding, padding);
        // A card swallows the taps that land on it, so the host's listener only ever sees the ones
        // that fell outside every sheet.
        card.setClickable(true);
        // Nothing inside a sheet may become the focused view. Rows and buttons still work — a click
        // needs no focus — but no descendant can ever raise an InputConnection of its own.
        card.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        card.setFocusable(false);

        // An empty title means the card carries no heading at all: the long-press menu is read as a
        // list of actions, and a word above it ("Terminal") only spends a row saying where you
        // already are. Prompts and confirmations still title themselves.
        if (title.length() > 0) addHeading(card, context, title);
        return finishCard(card, content, fillHeight);
    }

    private void addHeading(@NonNull LinearLayout card, @NonNull Context context,
                            @NonNull CharSequence title) {
        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextSize(20f);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headingParams.bottomMargin = dp(10f);
        card.addView(heading, headingParams);
    }

    @NonNull
    private View finishCard(@NonNull LinearLayout card, @NonNull View content, boolean fillHeight) {

        card.addView(content, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            fillHeight ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT,
            fillHeight ? 1f : 0f));

        Placement placement = mPendingPlacement;
        mPendingPlacement = Placement.centered();
        if (placement.anchor != null) {
            card.setLayoutParams(anchoredParams(card, placement.anchor));
            return card;
        }
        if (placement.docked) {
            card.setLayoutParams(dockedParams());
            return card;
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            fillHeight ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER);
        params.leftMargin = dp(SIDE_INSET_DP);
        params.rightMargin = dp(SIDE_INSET_DP);
        params.topMargin = dp(VERTICAL_INSET_DP);
        params.bottomMargin = dp(VERTICAL_INSET_DP);
        card.setLayoutParams(params);
        return card;
    }

    /**
     * Places a bar the width of the dock, directly above it.
     *
     * <p>Wrap height and bottom gravity together are what makes it grow upward: the bar's bottom edge
     * stays put on the dock while its content pushes the top edge up, so the terminal above it is
     * covered only as far as the content actually needs.
     */
    @NonNull
    private FrameLayout.LayoutParams dockedParams() {
        int planeWidth = mStackHost.getWidth();
        int planeHeight = mStackHost.getHeight();
        int inset = dp(SIDE_INSET_DP);
        Rect dock = new Rect();
        int[] planeOnScreen = new int[2];
        mStackHost.getLocationOnScreen(planeOnScreen);
        boolean haveDock = mActivity.dockBoundsOnScreen(dock) && dock.width() > 0;

        int width = haveDock ? dock.width() : Math.max(0, planeWidth - 2 * inset);
        if (planeWidth > 0) width = Math.min(width, planeWidth);
        int left = haveDock ? dock.left - planeOnScreen[0] : inset;
        if (planeWidth > 0) left = Math.max(0, Math.min(left, Math.max(0, planeWidth - width)));
        // Above the dock's top edge, or above the plane's own bottom inset when there is no dock at
        // all — a terminal-only install still has to put the bar somewhere sensible.
        int bottomMargin = haveDock && planeHeight > 0
            ? Math.max(inset, planeHeight - (dock.top - planeOnScreen[1]) + dp(8f))
            : inset;
        if (planeHeight > 0) bottomMargin = Math.min(bottomMargin, Math.max(0, planeHeight - dp(80f)));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        params.leftMargin = left;
        params.bottomMargin = bottomMargin;
        return params;
    }

    /**
     * Places a compact card at the touch point.
     *
     * <p>The card measures itself once against the plane so it can be clamped by its real size: a
     * menu opened near the bottom edge has to move up by its own height, and one opened near the
     * right edge by its own width, or the rows the user came for end up off screen. Both margins are
     * absolute, so nothing here depends on the plane's own gravity.
     */
    @NonNull
    private FrameLayout.LayoutParams anchoredParams(@NonNull View card, @NonNull PointF anchor) {
        int planeWidth = mStackHost.getWidth();
        int planeHeight = mStackHost.getHeight();
        int maxWidth = Math.min(dp(ANCHORED_MAX_WIDTH_DP),
            Math.max(dp(160f), planeWidth - 2 * dp(SIDE_INSET_DP)));
        card.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(Math.max(0, planeHeight - 2 * dp(SIDE_INSET_DP)),
                View.MeasureSpec.AT_MOST));
        int width = Math.min(maxWidth, Math.max(card.getMeasuredWidth(), dp(160f)));
        int height = card.getMeasuredHeight();

        int[] planeOnScreen = new int[2];
        mStackHost.getLocationOnScreen(planeOnScreen);
        int inset = dp(SIDE_INSET_DP);
        int left = Math.round(anchor.x) - planeOnScreen[0] - width / 2;
        int top = Math.round(anchor.y) - planeOnScreen[1];
        if (planeWidth > 0)
            left = Math.max(inset, Math.min(left, planeWidth - width - inset));
        if (planeHeight > 0 && height > 0)
            top = Math.max(inset, Math.min(top, planeHeight - height - inset));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        params.leftMargin = Math.max(0, left);
        params.topMargin = Math.max(0, top);
        return params;
    }

    private void animateIn(@NonNull View card) {
        card.animate().cancel();
        if (mActivity.isReducedMotionEnabled()) {
            card.setAlpha(1f);
            card.setScaleX(1f);
            card.setScaleY(1f);
            return;
        }
        card.setAlpha(0f);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(ENTER_DURATION_MS)
            .setInterpolator(new PathInterpolator(0.2f, 0.8f, 0.2f, 1f))
            .start();
    }

    private void animateOut(@NonNull View card) {
        FrameLayout stack = mStackHost;
        if (stack == null) return;
        card.animate().cancel();
        if (mActivity.isReducedMotionEnabled()) {
            stack.removeView(card);
            return;
        }
        card.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f)
            .setDuration(EXIT_DURATION_MS)
            .withEndAction(() -> stack.removeView(card))
            .start();
    }

    // ------------------------------------------------------------------ input

    /**
     * The in-app keyboard. Every branch returns true while a sheet is up: the plane is modal, so
     * nothing typed at it may reach the shell behind it.
     */
    @Override
    public boolean interceptKeyValue(@NonNull KeyValue value, boolean ctrl, boolean alt,
                                     boolean shift) {
        if (mStack.isEmpty()) return false;
        switch (value.getKind()) {
            case Char:
                if (!ctrl && !alt) text(String.valueOf(value.getChar()));
                return true;
            case String:
                if (!ctrl && !alt) text(value.getString());
                return true;
            case Editing:
                switch (value.getEditing()) {
                    case SPACE_BAR: text(" "); break;
                    case BACKSPACE: backspace(); break;
                    default: break;
                }
                return true;
            case Keyevent:
                handleKeyCode(value.getKeyevent());
                return true;
            case Event:
                if (value.getEvent() == KeyValue.Event.ACTION) commit();
                return true;
            default:
                return true;
        }
    }

    /** Hardware and external-keyboard strokes, claimed before the terminal writes them. */
    public boolean handleHardwareKey(int keyCode, @NonNull KeyEvent event) {
        if (mStack.isEmpty()) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        if (handleKeyCode(keyCode)) return true;
        if (event.isCtrlPressed() || event.isAltPressed()) return true;
        int unicode = event.getUnicodeChar();
        if (unicode >= ' ') text(String.valueOf((char) unicode));
        return true;
    }

    /**
     * Text committed by a system IME. Third-party keyboards send no key events at all for ordinary
     * characters, so without this a sheet looks dead on most people's keyboards.
     */
    public boolean handleSoftKeyboardCodePoint(int codePoint, boolean ctrlDown) {
        if (mStack.isEmpty()) return false;
        if (ctrlDown) return true;
        if (codePoint == '\r' || codePoint == '\n') {
            commit();
            return true;
        }
        if (codePoint == '\b') {
            backspace();
            return true;
        }
        if (codePoint == 0x1b) {
            dismiss();
            return true;
        }
        if (codePoint >= ' ') text(new String(Character.toChars(codePoint)));
        return true;
    }

    private boolean handleKeyCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: return arrow(-1);
            case KeyEvent.KEYCODE_DPAD_DOWN: return arrow(1);
            case KeyEvent.KEYCODE_PAGE_UP: return arrow(-PAGE_ARROW_ROWS);
            case KeyEvent.KEYCODE_PAGE_DOWN: return arrow(PAGE_ARROW_ROWS);
            case KeyEvent.KEYCODE_DEL: backspace(); return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: commit(); return true;
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BACK: dismiss(); return true;
            default: return false;
        }
    }

    private void text(@NonNull String value) {
        TextSink sink = topSink();
        if (sink != null) sink.onText(value);
    }

    private void backspace() {
        TextSink sink = topSink();
        if (sink != null) sink.onBackspace();
    }

    /**
     * An arrow aimed at the top sheet. Unclaimed arrows return false so they keep falling through
     * {@link #handleKeyCode}'s other cases rather than being silently eaten by a sheet that has no
     * list to walk — but the caller still swallows them, because the plane is modal.
     */
    private boolean arrow(int delta) {
        TextSink sink = topSink();
        return sink != null && sink.onArrow(delta);
    }

    /** ⏎ a sheet does not claim is spent on nothing; it must never fall through to the shell. */
    private void commit() {
        TextSink sink = topSink();
        if (sink != null) sink.onCommit();
    }

    @Nullable
    private TextSink topSink() {
        return mStack.isEmpty() ? null : mStack.get(mStack.size() - 1).sink;
    }

    /** The card a keystroke or an outside tap is currently aimed at. */
    @Nullable
    public View topCard() {
        return mStack.isEmpty() ? null : mStack.get(mStack.size() - 1).card;
    }

    private int dp(float value) {
        return Math.round(value * mDensity);
    }

    /** Corner radius of a sheet card, so the activity's glass builder and the plane agree. */
    public static float cornerRadiusDp() {
        return CORNER_RADIUS_DP;
    }
}
