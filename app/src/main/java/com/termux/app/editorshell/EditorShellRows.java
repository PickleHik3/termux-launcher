package com.termux.app.editorshell;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies {@link EditorShellMetrics} to a row the shell has just inflated.
 *
 * <p>The row layouts carry the same numbers as literals so the editor preview draws right, but the
 * metrics class is what the running app reads: one constant moved there reaches both editors
 * without either layout being edited, and the unit tests hold the number rather than a resource.
 */
public final class EditorShellRows {

    private EditorShellRows() {}

    /**
     * A section heading, added to the column the rows are being built into.
     *
     * <p>The first heading in a pane gets no top margin: a section reads as a break because of the
     * air above it, and air above the first row is just a gap at the top of the card.
     */
    @NonNull
    public static View addSection(@NonNull Context context, @NonNull ViewGroup into,
                                  @StringRes int titleRes, boolean first) {
        float density = context.getResources().getDisplayMetrics().density;
        View section = LayoutInflater.from(context)
            .inflate(R.layout.editor_shell_section, into, false);
        ((android.widget.TextView) section).setText(titleRes);
        section.setMinimumHeight(EditorShellMetrics.px(
            EditorShellMetrics.SECTION_MIN_HEIGHT_DP, density));
        ViewGroup.LayoutParams params = section.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            margins.topMargin = first ? 0
                : EditorShellMetrics.px(EditorShellMetrics.SECTION_TOP_MARGIN_DP, density);
            margins.bottomMargin = EditorShellMetrics.px(
                EditorShellMetrics.SECTION_BOTTOM_MARGIN_DP, density);
            section.setLayoutParams(params);
        }
        into.addView(section);
        return section;
    }

    /** Sizes one inflated row's columns. Every id it looks for is optional. */
    public static void apply(@NonNull View row) {
        float density = row.getResources().getDisplayMetrics().density;
        EditorShellMetrics.RowMetrics metrics = EditorShellMetrics.rowMetrics(density);

        View label = row.findViewById(R.id.editor_shell_row_label);
        setWidth(label, metrics.labelWidthPx);
        setEndMargin(label, metrics.labelGapPx);

        View control = row.findViewById(R.id.editor_shell_row_control);
        setEndMargin(control, metrics.controlGapPx);

        View value = row.findViewById(R.id.editor_shell_row_value);
        setWidth(value, metrics.valueWidthPx);
        setEndMargin(value, metrics.valueGapPx);

        View chip = row.findViewById(R.id.editor_shell_row_chip);
        if (chip != null) {
            setWidth(chip, metrics.chipSizePx);
            setHeight(chip, metrics.chipSizePx);
            // Drawn at 28 dp so it does not shout, tapped at the platform floor: the chip is how a
            // row gets back onto Base, and a 28 dp target is not a control.
            expandTouchTarget(chip, metrics.minHeightPx);
        }

        View inner = innerRow(row);
        if (inner != null) {
            inner.setMinimumHeight(metrics.minHeightPx);
            inner.setPadding(inner.getPaddingLeft(), metrics.verticalPaddingPx,
                inner.getPaddingRight(), metrics.verticalPaddingPx);
        }
    }

    /**
     * The line the columns stand on. A slider or switch row wraps it in a column that carries the
     * note underneath; a pills or action row is the line itself.
     */
    @Nullable
    private static View innerRow(@NonNull View row) {
        View label = row.findViewById(R.id.editor_shell_row_label);
        return label == null ? row : (View) label.getParent();
    }

    /**
     * The three things that make a capped body legible about what is below it.
     *
     * <p>Quantising the cap to whole rows (EditorShellMetrics.bodyCap) is the fix for a body cut
     * through the middle of a row's glyphs; these are what make the cut readable as a list that
     * continues. The fade covers the peek and the top of the row behind it, and the scrollbar is
     * the only thing on screen that says <em>how much</em> more there is, so it never fades out.
     *
     * @return the context to build the scroller with, carrying the shell's scrollbar ink
     */
    @NonNull
    public static Context scrollerContext(@NonNull Context context) {
        return new android.view.ContextThemeWrapper(
            context, R.style.ThemeOverlay_Termux_EditorShellScroller);
    }

    /** Turns the fade and the persistent scrollbar on for a body scroller. */
    public static void applyBodyScroller(@NonNull View scroller) {
        float density = scroller.getResources().getDisplayMetrics().density;
        scroller.setVerticalFadingEdgeEnabled(true);
        scroller.setFadingEdgeLength(EditorShellMetrics.px(EditorShellMetrics.FADE_DP, density));
        scroller.setVerticalScrollBarEnabled(true);
        scroller.setScrollbarFadingEnabled(false);
        int inset = EditorShellMetrics.px(2, density);
        scroller.setPadding(scroller.getPaddingLeft(), scroller.getPaddingTop(), inset,
            scroller.getPaddingBottom());
    }

    /** Grows a small view's touch target inside its parent, up to the row's own height. */
    public static void expandTouchTarget(@NonNull View target, int minSizePx) {
        View parent = (View) target.getParent();
        if (parent == null)
            return;
        parent.post(() -> {
            Rect bounds = new Rect();
            target.getHitRect(bounds);
            int growX = Math.max(0, (minSizePx - bounds.width()) / 2);
            int growY = Math.max(0, (minSizePx - bounds.height()) / 2);
            bounds.inset(-growX, -growY);
            SharedTouchDelegate shared = parent.getTouchDelegate() instanceof SharedTouchDelegate
                ? (SharedTouchDelegate) parent.getTouchDelegate()
                : new SharedTouchDelegate(parent);
            shared.add(bounds, target);
            parent.setTouchDelegate(shared);
        });
    }

    /**
     * The header stands five actions side by side and every one of them wants a finger-sized
     * target, but a view holds only one {@link TouchDelegate} — set them one at a time and only
     * the last one asked ever grows. This keeps them all: whichever grown rect the finger comes
     * down in takes the gesture and keeps it until the finger lifts.
     */
    private static final class SharedTouchDelegate extends TouchDelegate {

        private final List<Rect> mBounds = new ArrayList<>();
        private final List<TouchDelegate> mDelegates = new ArrayList<>();
        @Nullable private TouchDelegate mHolding;

        SharedTouchDelegate(@NonNull View parent) {
            super(new Rect(), parent);
        }

        void add(@NonNull Rect bounds, @NonNull View target) {
            mBounds.add(bounds);
            mDelegates.add(new TouchDelegate(bounds, target));
        }

        @Override
        public boolean onTouchEvent(@NonNull MotionEvent event) {
            // The rect is asked here rather than left to each delegate's own check, so a down that
            // lands in two overlapping rects goes to one of them and not to both.
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mHolding = null;
                int x = (int) event.getX();
                int y = (int) event.getY();
                for (int i = 0; i < mBounds.size(); i++) {
                    if (mBounds.get(i).contains(x, y)) {
                        mHolding = mDelegates.get(i);
                        break;
                    }
                }
            }
            if (mHolding == null)
                return false;
            boolean handled = mHolding.onTouchEvent(event);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_CANCEL)
                mHolding = null;
            return handled;
        }
    }

    private static void setWidth(@Nullable View view, int widthPx) {
        if (view == null)
            return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null || params.width == widthPx)
            return;
        params.width = widthPx;
        view.setLayoutParams(params);
    }

    private static void setHeight(@Nullable View view, int heightPx) {
        if (view == null)
            return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null || params.height == heightPx)
            return;
        params.height = heightPx;
        view.setLayoutParams(params);
    }

    private static void setEndMargin(@Nullable View view, int marginPx) {
        if (view == null)
            return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams))
            return;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        if (margins.getMarginEnd() == marginPx)
            return;
        margins.setMarginEnd(marginPx);
        view.setLayoutParams(params);
    }
}
