package com.termux.app.terminal;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
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

    /** What the plane needs from the activity: its views, the keyboard it is typed with, its glass. */
    public interface Host {
        @NonNull Context context();

        @Nullable <T extends View> T findView(int viewId);

        /**
         * Two full-screen glass planes must never stack, and a sheet is the modal one: the FULL
         * status pane and the drawer yield, the same handoff the palette makes.
         */
        void yieldCompetingPlanes();

        /** Makes a keyboard available for typing into the sheet, if the in-app keyboard is on. */
        void ensureInAppTypingKeyboard();

        /** Claims (or releases) the in-app keyboard's single interceptor slot for the plane. */
        void setSheetInterceptorActive(boolean active);

        /** Whether a raw screen point lands on the in-app keyboard — the keys the sheet is typed with. */
        boolean isPointOnInAppKeyboard(float rawX, float rawY);

        /** The in-app keyboard's rectangle on screen; false when no keyboard is up. */
        boolean inAppKeyboardBoundsOnScreen(@NonNull Rect out);

        /** Wallpaper frost for the plane's glass; true when the live blur should rest. */
        boolean applyWallpaperFrost(@NonNull ImageView frost);

        /** Glass for a sheet card, in the same kit as the dock and the rename chip. */
        @NonNull Drawable sheetSurface();

        /**
         * The terminal's own frame on screen — the rectangle its border is drawn around, insets
         * included — for a card that belongs inside the terminal window rather than over it. False
         * when there is no terminal laid out.
         */
        boolean terminalFrameOnScreen(@NonNull Rect out);

        /** The terminal frame's corner radius, so a card sitting in its corners can match them. */
        float terminalCornerRadiusPx();

        boolean isReducedMotionEnabled();
    }

    private static final long ENTER_DURATION_MS = 170L;
    private static final long EXIT_DURATION_MS = 110L;
    /** Side inset of a card, and the vertical inset of a full-height one. */
    private static final float SIDE_INSET_DP = 14f;
    private static final float VERTICAL_INSET_DP = 28f;
    /** An anchored card is a menu, not a page: it takes the width its rows need and no more. */
    private static final float ANCHORED_MAX_WIDTH_DP = 260f;
    /** A strip is one row of actions; its buttons carry their own touch padding. */
    private static final float STRIP_MAX_WIDTH_DP = 420f;
    private static final float STRIP_PADDING_DP = 2f;
    /** Half the strip's ~44dp height: a full pill, the Material shape for a floating action row. */
    private static final float STRIP_CORNER_RADIUS_DP = 22f;
    private static final float STRIP_ELEVATION_DP = 3f;
    /** How far a page key moves a list selection, in rows. */
    private static final int PAGE_ARROW_ROWS = 5;
    private static final float CORNER_RADIUS_DP = 22f;
    private static final float CARD_PADDING_DP = 18f;
    /** The least plane a card may be left with when the keyboard is inset out of it. */
    private static final float MIN_CARD_SPACE_DP = 140f;
    /**
     * A foot panel is a strip across the terminal, not a card floating in the middle of it: it
     * spends less on padding than a dialog would, in both directions.
     */
    private static final float FOOT_PADDING_DP = 10f;
    private static final float FOOT_SIDE_PADDING_DP = 14f;

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
        private static final Placement CENTERED = new Placement(null, false, false, false);

        @Nullable final PointF anchor;
        /** No plane backdrop: the surface behind this card stays exactly as it was. */
        final boolean bare;
        /** A thin one-line card whose bottom edge sits at the anchor instead of its top. */
        final boolean strip;
        /** A panel rising from the terminal's own bottom edge, inside its frame. */
        final boolean foot;

        private Placement(@Nullable PointF anchor, boolean bare, boolean strip, boolean foot) {
            this.anchor = anchor;
            this.bare = bare;
            this.strip = strip;
            this.foot = foot;
        }

        /** The default: a centred card with side and vertical insets. */
        @NonNull
        public static Placement centered() {
            return CENTERED;
        }

        /** A compact menu at a touch point, clamped inside the plane. */
        @NonNull
        public static Placement at(@Nullable PointF screenPoint) {
            return screenPoint == null ? CENTERED : new Placement(screenPoint, false, false, false);
        }

        /**
         * A thin strip whose bottom edge lands at the touch point, with no backdrop.
         *
         * <p>For a card that is <em>about</em> the text under the finger — the tapped hyperlink's
         * strip must not cover the link it names, and frosting the transcript would take away the
         * very line being asked about.
         */
        @NonNull
        public static Placement stripAbove(@Nullable PointF screenPoint) {
            return screenPoint == null ? CENTERED : new Placement(screenPoint, true, true, false);
        }

        /**
         * A panel that rises from the terminal's bottom edge and sinks back into it.
         *
         * <p>The shape the keybind hints already use, the other way up: inside the terminal's frame,
         * edge to edge, wearing the terminal's own corner radius where it sits in its bottom corners
         * — part of the terminal window rather than a card floating over one. It is anchored to the
         * terminal and not to the dock, so it lands on the same edge whether the dock, the A–Z row
         * and the extra keys are all there or none of them is.
         *
         * <p>Bare, because everything these panels are about is behind them: the transcript a search
         * is searching, the session the prompt is about to save.
         */
        @NonNull
        public static Placement terminalFoot() {
            return new Placement(null, true, false, true);
        }
    }

    /**
     * A one-line label typed from the key channel — the replacement for every {@code EditText} the
     * migrated prompts used to carry. Renders the draft with a caret, or the hint while empty.
     */
    public static final class TextField implements TextSink {

        /** Where the next keystroke lands. Shown on an empty field too, ahead of the hint. */
        private static final String CARET = "▏";
        /** The system's own cursor cadence, so this reads as a text cursor and not as a warning. */
        private static final long BLINK_MS = 500L;

        public interface OnChanged {
            void onChanged(@NonNull String value);
        }

        @NonNull private final TextView mView;
        @NonNull private final String mHint;
        @Nullable private final OnChanged mOnChanged;
        @Nullable private final Runnable mOnCommit;
        @NonNull private String mValue = "";
        /** The blink's phase. The glyph is always in the text; only its colour comes and goes. */
        private boolean mCaretVisible = true;
        /**
         * Its own handler rather than {@code View.postDelayed}: a view that is not attached to a
         * window queues those in its run queue instead of on a looper, and the field is built
         * before its card reaches the plane.
         */
        private final android.os.Handler mBlinkHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
        private final Runnable mBlink = new Runnable() {
            @Override public void run() {
                // The card is taken off the plane when the prompt closes, and a self-reposting
                // blink that outlived it would tick for the rest of the session.
                if (mView.getParent() == null) return;
                mCaretVisible = !mCaretVisible;
                render(false);
                mBlinkHandler.postDelayed(this, BLINK_MS);
            }
        };

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
            restartBlink();
        }

        /**
         * Puts the caret back on and starts the phase again.
         *
         * <p>Called on every keystroke as well as on attach, which is what a text cursor does: it
         * stays solid while the user is typing and only blinks once they stop, so the blink never
         * hides the character just entered.
         */
        private void restartBlink() {
            mBlinkHandler.removeCallbacks(mBlink);
            mCaretVisible = true;
            render(false);
            // A cursor that blinks is an animation like any other; with animations turned off it
            // stays solid rather than ticking away in the corner of the eye.
            if (android.provider.Settings.Global.getFloat(mView.getContext().getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) return;
            mBlinkHandler.postDelayed(mBlink, BLINK_MS);
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
            restartBlink();
        }

        @Override
        public void onBackspace() {
            if (mValue.isEmpty()) return;
            // By code point, not by char: a name can carry an emoji, and halving a surrogate pair
            // would leave the field holding an unpaired one.
            mValue = mValue.substring(0, mValue.offsetByCodePoints(mValue.length(), -1));
            render(true);
            restartBlink();
        }

        @Override
        public boolean onCommit() {
            if (mOnCommit == null) return false;
            mOnCommit.run();
            return true;
        }

        /**
         * Draws the draft, its caret and — on an empty field — the hint behind the caret.
         *
         * <p>The caret glyph is in the text whatever the blink is doing, and only its colour is
         * turned on and off: dropping the character instead would shuffle everything left and right
         * twice a second, and on an empty field the hint would jump with it.
         */
        private void render(boolean notify) {
            mView.setAlpha(1f);
            int color = mView.getCurrentTextColor();
            android.text.SpannableStringBuilder text =
                new android.text.SpannableStringBuilder(mValue).append(CARET);
            int caretStart = mValue.length();
            if (!mCaretVisible) {
                text.setSpan(new android.text.style.ForegroundColorSpan(
                        androidx.core.graphics.ColorUtils.setAlphaComponent(color, 0)),
                    caretStart, caretStart + CARET.length(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (mValue.isEmpty()) {
                // The hint follows the caret rather than replacing it: without one the prompt read
                // as a label, and nothing on screen said the keys would land here.
                int hintStart = text.length();
                text.append(mHint);
                text.setSpan(new android.text.style.ForegroundColorSpan(
                        androidx.core.graphics.ColorUtils.setAlphaComponent(color, 128)),
                    hintStart, text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            mView.setText(text);
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
        /** True for a card that asked for no backdrop; see {@link Placement#aboveDockBare()}. */
        final boolean bare;
        /** True for a card that rose from the terminal's foot, and has to sink back into it. */
        final boolean foot;

        Sheet(@NonNull View card, @Nullable TextSink sink, @Nullable Runnable onDismiss,
              boolean coversPrevious, boolean bare, boolean foot) {
            this.card = card;
            this.sink = sink;
            this.onDismiss = onDismiss;
            this.coversPrevious = coversPrevious;
            this.bare = bare;
            this.foot = foot;
        }
    }

    @NonNull private final Host mHost;
    @NonNull private final List<Sheet> mStack = new ArrayList<>();
    private final float mDensity;

    @Nullable private FrameLayout mPlane;
    @Nullable private FrameLayout mStackHost;
    /** Set by {@link #show} for the card it is about to build; read once by {@link #buildCard}. */
    @NonNull private Placement mPendingPlacement = Placement.centered();
    /** How much of the plane's bottom is inset away, so a layout pass that changes nothing no-ops. */
    private int mPlaneBottomInset;
    /** Foot cards on the plane, including one still sinking back into the terminal. */
    private int mFootCards;
    private final Rect mKeyboardBounds = new Rect();
    private final Rect mTerminalBounds = new Rect();
    private final int[] mPlaneOnScreen = new int[2];

    public TerminalSheetController(@NonNull Host host) {
        mHost = host;
        mDensity = host.context().getResources().getDisplayMetrics().density;
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
        if (mStack.isEmpty()) mHost.yieldCompetingPlanes();
        boolean bare = placement.bare;
        View card = buildCard(title, content, fillHeight);
        if (coverPrevious && !mStack.isEmpty())
            mStack.get(mStack.size() - 1).card.setVisibility(View.GONE);
        mStackHost.addView(card);
        if (placement.foot) mFootCards++;
        mStack.add(new Sheet(card, sink, onDismiss, coverPrevious, bare, placement.foot));
        mPlane.setVisibility(View.VISIBLE);
        applyBackdropMaterial();
        // Only a sheet with somewhere for typing to land is worth summoning a keyboard for; a
        // confirmation is all buttons and would just push the terminal around.
        if (sink != null) mHost.ensureInAppTypingKeyboard();
        mHost.setSheetInterceptorActive(true);
        applyPlaneInsets();
        animateIn(card, placement.foot);
        return true;
    }

    /** Pops the top sheet. The stacking rule in one line: back and an outside tap both land here. */
    public void dismiss() {
        if (mStack.isEmpty()) return;
        Sheet top = mStack.remove(mStack.size() - 1);
        animateOut(top.card, top.foot);
        if (top.coversPrevious && !mStack.isEmpty())
            mStack.get(mStack.size() - 1).card.setVisibility(View.VISIBLE);
        if (top.onDismiss != null) top.onDismiss.run();
        if (mStack.isEmpty()) onEmptied();
        // A bare card leaving can uncover one that does want the backdrop, and the other way round.
        else applyBackdropMaterial();
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
            animateOut(sheet.card, sheet.foot);
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
        mFootCards = 0;
        applyPlaneInsets();
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
        mHost.setSheetInterceptorActive(false);
        if (mPlane != null) mPlane.setVisibility(View.INVISIBLE);
        ImageView frost = mHost.findView(R.id.terminal_sheet_wallpaper_backdrop);
        if (frost != null) {
            // Drop the full-screen frost bitmap while shut; the next show() rebuilds it.
            frost.setImageDrawable(null);
            frost.setVisibility(View.GONE);
        }
    }

    private boolean bindViews() {
        if (mStackHost != null) return true;
        FrameLayout host = mHost.findView(R.id.terminal_sheet_host);
        FrameLayout stack = mHost.findView(R.id.terminal_sheet_stack);
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
                    return !mHost.isPointOnInAppKeyboard(event.getRawX(), event.getRawY());
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
        mPlane = host;
        mStackHost = stack;
        // The keyboard lays out after the sheet asks for it, and it can also be raised or dropped
        // while a sheet is up, so the inset is recomputed on every pass rather than only on show().
        host.getViewTreeObserver().addOnGlobalLayoutListener(this::applyPlaneInsets);
        applyPlaneInsets();
        return true;
    }

    /**
     * Where the plane's bottom edge stops, and whether it clips there.
     *
     * <p>Two reasons it cannot simply be the screen's bottom. The keyboard: the plane fills the
     * activity, keys included, and its glass is a frost over a live blur — with the keys behind it
     * the Save-workspace prompt asked for a name over a keyboard the user could not see. Touches
     * already fell through to those keys, so the keyboard was working the whole time; it was only
     * ever covered. And the terminal's foot: a panel that rises out of the terminal's bottom edge
     * has to be cut off by that edge, both while it rises and while it sinks back, or it slides
     * across the dock and the keyboard on its way in and out.
     *
     * <p>The terminal's edge is always the higher of the two, so a foot panel simply takes over the
     * inset while it is on the plane — and the card it clips is the same one the inset was cut for.
     */
    private void applyPlaneInsets() {
        if (mPlane == null || mStackHost == null) return;
        boolean foot = mFootCards > 0;
        // A foot panel is clipped by the plane's own edge, which is the terminal's; nothing else on
        // the plane is clipped, because a card's shadow is drawn outside it.
        mStackHost.setClipChildren(foot);
        int inset = foot ? terminalFootInsetPx() : keyboardInsetPx();
        // The frost is one pre-blurred frame of the wallpaper stretched over the whole plane, so
        // shortening its container would rescale it and slide the wallpaper out of register with
        // the real one behind — the seam that appeared across the keyboard's top edge. It keeps the
        // plane's full height whatever the glass around it is cut to, and is cropped by it.
        View frost = mHost.findView(R.id.terminal_sheet_wallpaper_backdrop);
        if (frost != null && frost.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) frost.getLayoutParams();
            int height = inset > 0 ? mPlane.getHeight() : ViewGroup.LayoutParams.MATCH_PARENT;
            if (params.height != height || params.gravity != Gravity.TOP) {
                params.height = height;
                params.gravity = Gravity.TOP;
                frost.setLayoutParams(params);
            }
        }
        if (inset == mPlaneBottomInset) return;
        mPlaneBottomInset = inset;
        setBottomInset(mHost.findView(R.id.terminal_sheet_glass), inset);
        setBottomInset(mStackHost, inset);
    }

    /** How much of the plane's bottom the in-app keyboard is taking. */
    private int keyboardInsetPx() {
        if (mPlane == null || mPlane.getHeight() <= 0
            || !mHost.inAppKeyboardBoundsOnScreen(mKeyboardBounds)) return 0;
        mPlane.getLocationOnScreen(mPlaneOnScreen);
        return clampInset(mPlaneOnScreen[1] + mPlane.getHeight() - mKeyboardBounds.top);
    }

    /** The same, measured to the terminal's own bottom edge. Falls back to the keys. */
    private int terminalFootInsetPx() {
        if (mPlane == null || mPlane.getHeight() <= 0
            || !mHost.terminalFrameOnScreen(mTerminalBounds)) return keyboardInsetPx();
        mPlane.getLocationOnScreen(mPlaneOnScreen);
        return clampInset(mPlaneOnScreen[1] + mPlane.getHeight() - mTerminalBounds.bottom);
    }

    /** A card still needs somewhere to be, whatever is claiming the bottom of the screen. */
    private int clampInset(int inset) {
        if (mPlane == null) return 0;
        return Math.max(0, Math.min(inset,
            Math.max(0, mPlane.getHeight() - dp(MIN_CARD_SPACE_DP))));
    }

    private static void setBottomInset(@Nullable View view, int inset) {
        if (view == null || !(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (params.bottomMargin == inset) return;
        params.bottomMargin = inset;
        view.setLayoutParams(params);
    }

    /**
     * Picks the glass backdrop for this open: over the system wallpaper a pre-blurred wallpaper
     * frost (the live blur cannot see through the window there and renders grey mud), otherwise the
     * RealtimeBlurView blurring real window content.
     */
    private void applyBackdropMaterial() {
        ImageView frost = mHost.findView(R.id.terminal_sheet_wallpaper_backdrop);
        View blur = mHost.findView(R.id.terminal_sheet_blur);
        if (allBare()) {
            // Every open card asked for no backdrop, so the plane draws nothing of its own and what
            // is behind it — the transcript a search is searching — stays exactly as it was.
            if (frost != null) {
                frost.setImageDrawable(null);
                frost.setVisibility(View.GONE);
            }
            if (blur != null) blur.setVisibility(View.GONE);
            return;
        }
        boolean frosted = frost != null && mHost.applyWallpaperFrost(frost);
        if (blur != null) blur.setVisibility(frosted ? View.GONE : View.VISIBLE);
    }

    /** True when nothing on the plane wants a backdrop; one card that does brings it back. */
    private boolean allBare() {
        if (mStack.isEmpty()) return false;
        for (Sheet sheet : mStack) {
            if (!sheet.bare) return false;
        }
        return true;
    }

    @NonNull
    private View buildCard(@NonNull CharSequence title, @NonNull View content,
                           boolean fillHeight) {
        Context context = mStackHost.getContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        if (mPendingPlacement.foot) {
            // The keybind hints' dress, the other way up: this panel sits in the terminal's bottom
            // corners, so it takes the terminal's radius there and its own where it leaves the edge.
            applyFootDress(card);
            card.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
                if (b - t != ob - ot) applyFootDress(card);
            });
            int padH = dp(FOOT_SIDE_PADDING_DP);
            int padV = dp(FOOT_PADDING_DP);
            card.setPadding(padH, padV, padH, padV);
        } else if (mPendingPlacement.strip) {
            // A strip is one row of actions on a flat opaque surface: the glass sheet material and
            // the page padding would both spend more screen than the strip's content does.
            card.setBackground(buildStripSurface(context));
            // Separation comes from the shadow, not a border: the pill background provides the
            // rounded outline the shadow is cast from.
            card.setElevation(dp(STRIP_ELEVATION_DP));
            int pad = dp(STRIP_PADDING_DP);
            card.setPadding(pad, pad, pad, pad);
        } else {
            card.setBackground(mHost.sheetSurface());
            int padding = dp(CARD_PADDING_DP);
            card.setPadding(padding, padding, padding, padding);
        }
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

    /** The strip's flat material: an opaque borderless pill, no glass and no blur. */
    @NonNull
    private android.graphics.drawable.Drawable buildStripSurface(@NonNull Context context) {
        android.graphics.drawable.GradientDrawable surface =
            new android.graphics.drawable.GradientDrawable();
        surface.setColor(com.google.android.material.color.MaterialColors.getColor(context,
            com.google.android.material.R.attr.colorSurfaceContainerHigh,
            com.google.android.material.color.MaterialColors.getColor(context,
                com.termux.shared.R.attr.termuxColorSurfacePanelHigh,
                androidx.core.content.ContextCompat.getColor(context,
                    R.color.termux_surface_panel_high))));
        surface.setCornerRadius(dp(STRIP_CORNER_RADIUS_DP));
        return surface;
    }

    private void addHeading(@NonNull LinearLayout card, @NonNull Context context,
                            @NonNull CharSequence title) {
        TextView heading = new TextView(context);
        heading.setText(title);
        heading.setTextSize(TerminalSheetViews.HEADING_TEXT_SIZE_SP);
        heading.setSingleLine(true);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headingParams.bottomMargin = dp(6f);
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
        if (placement.foot) {
            card.setLayoutParams(terminalFootParams(fillHeight));
            return card;
        }
        if (placement.anchor != null) {
            card.setLayoutParams(anchoredParams(card, placement));
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

    private void applyFootDress(@NonNull View card) {
        Context context = card.getContext();
        float radius = mHost.terminalCornerRadiusPx();
        card.setBackground(TerminalHintSurface.footBackground(context, radius,
            TerminalHintSurface.freeCornerRadiusPx(context, radius, card.getHeight())));
    }

    /**
     * Places a panel across the terminal's foot, inside its frame.
     *
     * <p>Bottom gravity with no bottom margin puts it on the plane's own bottom edge, which
     * {@link #applyPlaneInsets()} has already moved to the terminal's; the top margin is the rest of
     * the terminal, and it is what stops a long list rather than where the panel sits — a
     * wrap-height child of a {@code FrameLayout} measures against the parent less its margins, so
     * that margin is how the terminal's ceiling reaches the list inside.
     *
     * @param fillHeight the browser's weighted list, which has no height of its own to wrap: the
     *     panel takes the whole terminal so the list has a measured frame to divide.
     */
    @NonNull
    private FrameLayout.LayoutParams terminalFootParams(boolean fillHeight) {
        int planeWidth = mStackHost.getWidth();
        Rect terminal = new Rect();
        int[] planeOnScreen = new int[2];
        mStackHost.getLocationOnScreen(planeOnScreen);
        boolean haveTerminal = mHost.terminalFrameOnScreen(terminal) && terminal.width() > 0;

        int inset = dp(SIDE_INSET_DP);
        int width = haveTerminal ? terminal.width() : Math.max(0, planeWidth - 2 * inset);
        if (planeWidth > 0) width = Math.min(width, planeWidth);
        int left = haveTerminal ? terminal.left - planeOnScreen[0] : inset;
        if (planeWidth > 0) left = Math.max(0, Math.min(left, Math.max(0, planeWidth - width)));
        int top = haveTerminal ? terminal.top - planeOnScreen[1] : inset;

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width,
            fillHeight ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.START);
        params.leftMargin = left;
        params.topMargin = Math.max(0, top);
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
    private FrameLayout.LayoutParams anchoredParams(@NonNull View card,
                                                    @NonNull Placement placement) {
        PointF anchor = placement.anchor;
        int planeWidth = mStackHost.getWidth();
        int planeHeight = mStackHost.getHeight();
        int maxWidth = Math.min(dp(placement.strip ? STRIP_MAX_WIDTH_DP : ANCHORED_MAX_WIDTH_DP),
            Math.max(dp(160f), planeWidth - 2 * dp(SIDE_INSET_DP)));
        card.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(Math.max(0, planeHeight - 2 * dp(SIDE_INSET_DP)),
                View.MeasureSpec.AT_MOST));
        // A strip is exactly its actions wide; the menu minimum would pad it with dead surface.
        int minWidth = placement.strip ? dp(48f) : dp(160f);
        int width = Math.min(maxWidth, Math.max(card.getMeasuredWidth(), minWidth));
        int height = card.getMeasuredHeight();

        int[] planeOnScreen = new int[2];
        mStackHost.getLocationOnScreen(planeOnScreen);
        int inset = dp(SIDE_INSET_DP);
        int left = Math.round(anchor.x) - planeOnScreen[0] - width / 2;
        // A strip hangs from its anchor rather than growing down from it, so the tapped line —
        // the thing the strip is about — stays visible below it.
        int top = Math.round(anchor.y) - planeOnScreen[1] - (placement.strip ? height : 0);
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

    private void animateIn(@NonNull View card, boolean foot) {
        card.animate().cancel();
        if (mHost.isReducedMotionEnabled()) {
            card.setAlpha(1f);
            card.setScaleX(1f);
            card.setScaleY(1f);
            card.setTranslationY(0f);
            return;
        }
        if (foot) {
            // A foot panel peeks out of the terminal's bottom edge, so it rises rather than
            // appears: the travel is its own height, which is only known once it has been laid out.
            card.setAlpha(0f);
            card.getViewTreeObserver().addOnPreDrawListener(
                new android.view.ViewTreeObserver.OnPreDrawListener() {
                    @Override public boolean onPreDraw() {
                        card.getViewTreeObserver().removeOnPreDrawListener(this);
                        card.setAlpha(1f);
                        card.setTranslationY(card.getHeight());
                        card.animate().translationY(0f).setDuration(ENTER_DURATION_MS)
                            .setInterpolator(new PathInterpolator(0.2f, 0.8f, 0.2f, 1f))
                            .start();
                        return true;
                    }
                });
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

    private void animateOut(@NonNull View card, boolean foot) {
        FrameLayout stack = mStackHost;
        if (stack == null) return;
        card.animate().cancel();
        if (mHost.isReducedMotionEnabled()) {
            stack.removeView(card);
            onCardRemoved(foot);
            return;
        }
        if (foot) {
            // The plane keeps its foot inset until the panel has finished sinking: that inset is
            // the terminal's bottom edge, and it is what cuts the panel off as it goes.
            card.animate().translationY(card.getHeight()).setDuration(EXIT_DURATION_MS)
                .withEndAction(() -> {
                    stack.removeView(card);
                    onCardRemoved(true);
                })
                .start();
            return;
        }
        card.animate().alpha(0f).scaleX(0.94f).scaleY(0.94f)
            .setDuration(EXIT_DURATION_MS)
            .withEndAction(() -> stack.removeView(card))
            .start();
    }

    /** A card has actually left the plane, so the insets it was holding can be given back. */
    private void onCardRemoved(boolean foot) {
        if (foot && mFootCards > 0) mFootCards--;
        applyPlaneInsets();
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
