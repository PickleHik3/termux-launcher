package com.termux.app.terminal.rename;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.TerminalNamePolicy;
import com.termux.app.terminal.inappkeyboard.TerminalKeyEventHandler;

/**
 * Runs every terminal rename as a glass chip anchored next to the thing being renamed, typed with
 * the in-app keyboard.
 *
 * <p>Renaming used to raise a Material dialog with an {@code EditText}, which took focus and
 * summoned the system IME — so naming a window cost an IME swap in and out for two words of typing,
 * with the in-app keyboard collapsing and the terminal resizing twice on the way. The chip is a
 * plain drawn view inside the activity: focus never moves, no {@code InputConnection} changes hands,
 * and the accessory stack is untouched, so nothing resizes.
 *
 * <p>The dialog remains as the fallback for the one case the chip cannot serve: no in-app keyboard
 * to type into, and hence nothing on screen to aim the three input channels at.
 */
public final class TerminalRenameCoordinator implements InlineRenameController.Host {

    /** Everything the chip needs from the Activity, kept behind an interface for testability. */
    public interface Host {
        /** Full-screen container the chip is added to, or null before the layout exists. */
        @Nullable ViewGroup chipHost();

        /** The view the chip should sit under, or null to centre it over the terminal. */
        @Nullable View anchorFor(@NonNull TerminalRenameTarget target);

        /** The target's current name, or null when it is unnamed. */
        @Nullable String currentName(@NonNull TerminalRenameTarget target);

        /** Stores a committed name; false when the target went away while the chip was up. */
        boolean applyName(@NonNull TerminalRenameTarget target, @Nullable String name);

        /** Glass background for the chip, built by the same helper as the other floating surfaces. */
        @NonNull Drawable chipBackground();

        /** Label, draft and caret colours, in that order. */
        @NonNull int[] chipColors();

        boolean isReducedMotionEnabled();

        /**
         * Make a keyboard available for typing into the chip, and report whether one is.
         *
         * <p>False means fall back to the dialog: with no in-app keyboard, a focusless chip has no
         * way for a user to type into it that does not involve the very IME dance the chip exists to
         * avoid.
         */
        boolean ensureTypingKeyboard();

        /** Legacy dialog, used only when {@link #ensureTypingKeyboard()} says no. */
        void promptRenameWithDialog(@NonNull TerminalRenameTarget target);

        /**
         * Whether a raw screen point lands on the in-app keyboard. Touches there must fall through
         * the chip's full-screen host untouched — they are the keys the rename is typed with.
         */
        boolean isPointOnTypingKeyboard(float rawX, float rawY);

        /** Installs (or with null restores) the in-app keyboard's interceptor slot. */
        void installRenameInterceptor(@Nullable TerminalKeyEventHandler.KeyValueInterceptor interceptor);

        /** Refresh whatever surfaces show the name after a rename ends. */
        void onRenameEnded(@NonNull TerminalRenameTarget target, boolean committed);
    }

    private static final long ENTER_DURATION_MS = 180L;
    private static final long EXIT_DURATION_MS = 110L;
    private static final float ANCHOR_GAP_DP = 6f;
    private static final float EDGE_MARGIN_DP = 10f;

    @NonNull private final Host host;
    @NonNull private final InlineRenameController controller = new InlineRenameController();
    @Nullable private TerminalRenameChipView chip;
    @Nullable private TerminalRenameTarget target;

    public TerminalRenameCoordinator(@NonNull Host host) {
        this.host = host;
    }

    public boolean isActive() {
        return controller.isActive();
    }

    @Nullable
    public TerminalRenameTarget activeTarget() {
        return controller.isActive() ? target : null;
    }

    /**
     * Opens the chip for {@code target}, or the dialog when no in-app keyboard can serve it.
     *
     * @return true when a rename surface is now up.
     */
    public boolean begin(@NonNull TerminalRenameTarget target) {
        // Any live chip is torn down before either surface opens, so a fallback dialog can never
        // stack on top of a still-active chip that would fight it for the interceptor and the name.
        if (controller.isActive()) controller.cancel();
        ViewGroup container = host.chipHost();
        if (container == null || !host.ensureTypingKeyboard()) {
            host.promptRenameWithDialog(target);
            return true;
        }
        this.target = target;
        TerminalRenameChipView view = obtainChip(container);
        int[] colors = host.chipColors();
        view.setColors(colors.length > 0 ? colors[0] : 0xFFFFFFFF,
            colors.length > 1 ? colors[1] : 0xFFFFFFFF,
            colors.length > 2 ? colors[2] : 0xFFFFFFFF);
        // The face the panes are drawing with, so a name previews in the font its tab will use.
        view.setTypeface(com.termux.app.terminal.TerminalLabelFaces.current().regular);
        view.setBackground(host.chipBackground());
        controller.begin(host.currentName(target), TerminalNamePolicy.maxCodePointsFor(target), this);
        host.installRenameInterceptor(controller);
        container.setVisibility(View.VISIBLE);
        view.setVisibility(View.VISIBLE);
        if (container.getWidth() == 0) {
            // First rename of this activity: the host was inflated GONE and has never been laid
            // out, so its size is still 0 and positioning now would pin the chip to the top-left
            // corner with the wrong pivot. Held invisible for the one layout pass the visibility
            // flip just scheduled, then placed and animated with real bounds.
            view.setAlpha(0f);
            container.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b,
                                           int ol, int ot, int or, int ob) {
                    container.removeOnLayoutChangeListener(this);
                    TerminalRenameChipView current = chip;
                    TerminalRenameTarget renaming = TerminalRenameCoordinator.this.target;
                    if (!controller.isActive() || current == null || renaming == null) return;
                    positionChip(container, current, renaming);
                    animateIn(current);
                }
            });
        } else {
            positionChip(container, view, target);
            animateIn(view);
        }
        return true;
    }

    public void cancel() {
        controller.cancel();
    }

    /** Commits whatever is typed. Used by an outside tap, which reads as "done", not "discard". */
    public void commit() {
        controller.commit();
    }

    /** Hardware and external-keyboard strokes, claimed while a rename is up. */
    public boolean handleKeyDown(int keyCode, @NonNull KeyEvent event) {
        return controller.handleKeyDown(keyCode, event);
    }

    /** System-IME committed text, claimed while a rename is up. */
    public boolean handleCodePoint(int codePoint, boolean ctrlDown) {
        return controller.handleCodePoint(codePoint, ctrlDown);
    }

    /** Cancels on pause: a chip that survived a trip to another app would edit stale state. */
    public void onActivityPaused() {
        controller.cancel();
    }

    /** Keeps the chip beside its anchor when the bar it hangs from moves or relayouts. */
    public void reposition() {
        ViewGroup container = host.chipHost();
        TerminalRenameChipView view = chip;
        TerminalRenameTarget current = target;
        if (container == null || view == null || current == null || !controller.isActive()) return;
        positionChip(container, view, current);
    }

    // ------------------------------------------------------------------ InlineRenameController.Host

    @Override
    public void onDraftChanged(@NonNull InlineRenameModel model) {
        TerminalRenameChipView view = chip;
        TerminalRenameTarget current = target;
        if (view == null || current == null) return;
        view.bind(current, model, emptyHintFor(current));
        reposition();
    }

    @Override
    public void onRenameEnded(boolean committed, @Nullable String committedName) {
        TerminalRenameTarget ended = target;
        target = null;
        host.installRenameInterceptor(null);
        hideChip();
        if (ended == null) return;
        if (committed) host.applyName(ended, committedName);
        host.onRenameEnded(ended, committed);
    }

    // ------------------------------------------------------------------ internals

    private void redraw() {
        TerminalRenameChipView view = chip;
        if (view != null) view.refresh();
        reposition();
    }

    @NonNull
    private TerminalRenameChipView obtainChip(@NonNull ViewGroup container) {
        TerminalRenameChipView view = chip;
        if (view == null) {
            view = new TerminalRenameChipView(container.getContext());
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
            container.addView(view, params);
            // An outside tap commits rather than discards: the chip has no buttons, and losing two
            // words of typing to a stray tap is the worse of the two failures. Taps on the in-app
            // keyboard are not "outside" though — the host overlays the whole activity, keyboard
            // included, and swallowing them would end the rename on the first key of the new name.
            // Those must fall through unconsumed so the key underneath types into the draft.
            container.setClickable(false);
            container.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        return !host.isPointOnTypingKeyboard(event.getRawX(), event.getRawY());
                    case android.view.MotionEvent.ACTION_UP:
                        // Committed on the finished tap, not on DOWN: a gesture-nav back swipe
                        // delivers its DOWN to the app before the system claims the gesture with
                        // ACTION_CANCEL, and committing on DOWN would turn every Back into a
                        // commit that then falls through to the next back consumer.
                        commit();
                        return true;
                    default:
                        return true;
                }
            });
            // Tapping the chip itself also commits, and being a real click it stays reachable
            // under TalkBack, where the host's raw outside-tap gesture is not.
            view.setOnClickListener(v -> commit());
            chip = view;
        }
        return view;
    }

    private void hideChip() {
        ViewGroup container = host.chipHost();
        TerminalRenameChipView view = chip;
        if (view == null) return;
        if (host.isReducedMotionEnabled()) {
            view.setVisibility(View.GONE);
            if (container != null) container.setVisibility(View.GONE);
            return;
        }
        view.animate().cancel();
        // The exit mirrors the entrance: the chip shrinks about the anchor pivot and slides back
        // toward the thing it was naming, instead of fading in place.
        view.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).translationY(anchorShift(view))
            .setDuration(EXIT_DURATION_MS).withEndAction(() -> {
                view.setVisibility(View.GONE);
                ViewGroup parent = host.chipHost();
                if (parent != null && !controller.isActive()) parent.setVisibility(View.GONE);
            }).start();
    }

    private void animateIn(@NonNull TerminalRenameChipView view) {
        view.animate().cancel();
        if (host.isReducedMotionEnabled()) {
            view.setAlpha(1f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            view.setTranslationY(0f);
            return;
        }
        // Grows out of the anchor: positionChip has already put the scale pivot on the anchor's
        // edge, so the chip unfolds from the renamed thing and slides the last few dp into place.
        view.setAlpha(0f);
        view.setScaleX(0.82f);
        view.setScaleY(0.82f);
        view.setTranslationY(anchorShift(view));
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(ENTER_DURATION_MS)
            .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0.8f, 0.2f, 1f))
            .start();
    }

    /**
     * Offset toward the anchor edge the pivot sits on: negative (up) for a chip hanging below its
     * anchor, positive for one flipped above it, none for the centred fallback.
     */
    private float anchorShift(@NonNull View view) {
        float shift = 4f * view.getResources().getDisplayMetrics().density;
        if (view.getPivotY() <= 0f) return -shift;
        if (view.getPivotY() >= view.getHeight()) return shift;
        return 0f;
    }

    /**
     * Places the chip just below its anchor, horizontally centred on it and clamped inside the
     * container. Measured first, because the chip's width follows the draft and a stale width would
     * clamp against the wrong edge.
     */
    private void positionChip(@NonNull ViewGroup container, @NonNull TerminalRenameChipView view,
                              @NonNull TerminalRenameTarget target) {
        view.measure(View.MeasureSpec.makeMeasureSpec(container.getWidth(),
                View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int width = Math.max(1, view.getMeasuredWidth());
        int height = Math.max(1, view.getMeasuredHeight());
        float margin = EDGE_MARGIN_DP * container.getResources().getDisplayMetrics().density;
        float gap = ANCHOR_GAP_DP * container.getResources().getDisplayMetrics().density;
        int left;
        int top;
        Rect anchorRect = anchorRectIn(container, host.anchorFor(target));
        if (anchorRect == null) {
            left = Math.round((container.getWidth() - width) / 2f);
            top = Math.round((container.getHeight() - height) / 2f);
        } else {
            left = Math.round(anchorRect.centerX() - width / 2f);
            // A pane is the surface itself rather than a label above one, so its chip sits just
            // inside its top edge; a bar's chip hangs below the bar, and flips above it when there
            // is no room underneath.
            if (target == TerminalRenameTarget.PANE) {
                top = Math.round(anchorRect.top + gap);
            } else {
                top = Math.round(anchorRect.bottom + gap);
                if (top + height + margin > container.getHeight())
                    top = Math.round(anchorRect.top - gap - height);
            }
        }
        left = (int) Math.max(margin, Math.min(left, container.getWidth() - width - margin));
        top = (int) Math.max(margin, Math.min(top, container.getHeight() - height - margin));
        // Scale pivot on the anchor, so enter/exit grow out of and collapse back into the thing
        // being renamed: the anchor's centre horizontally, and whichever card edge faces it.
        if (anchorRect == null) {
            view.setPivotX(width / 2f);
            view.setPivotY(height / 2f);
        } else {
            view.setPivotX(Math.max(0f, Math.min(anchorRect.centerX() - left, width)));
            view.setPivotY(top >= anchorRect.top ? 0f : height);
        }
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            if (margins.leftMargin != left || margins.topMargin != top) {
                margins.leftMargin = left;
                margins.topMargin = top;
                view.setLayoutParams(margins);
            }
        }
    }

    @Nullable
    private Rect anchorRectIn(@NonNull ViewGroup container, @Nullable View anchor) {
        if (anchor == null || !anchor.isShown() || anchor.getWidth() <= 0) return null;
        int[] containerLocation = new int[2];
        int[] anchorLocation = new int[2];
        container.getLocationInWindow(containerLocation);
        anchor.getLocationInWindow(anchorLocation);
        int left = anchorLocation[0] - containerLocation[0];
        int top = anchorLocation[1] - containerLocation[1];
        return new Rect(left, top, left + anchor.getWidth(), top + anchor.getHeight());
    }

    @NonNull
    private String emptyHintFor(@NonNull TerminalRenameTarget target) {
        // The chip used to carry two texts: a bold "window" tag and a greyed "auto label" saying
        // what an emptied draft falls back to. Two labels in a chip that has to fit beside a tab is
        // one too many, and the fallback wording was the less useful of the two — so the hint is now
        // the target itself and the tag is gone.
        return target.id;
    }
}
