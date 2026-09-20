package com.termux.app.editorshell;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

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
     * The two things that make a capped body legible about what is below it.
     *
     * <p>Cutting the body at a whole row ({@link #wholeRowCapPx}) is the fix for a body cut through
     * the middle of a row's glyphs; these are what make the cut readable as a list that continues.
     * The fade covers the peek and the top of the row behind it, and the mark down the edge says
     * where in the list the rows on screen are.
     *
     * @return the context to build the scroller with, carrying the shell's scrollbar ink
     */
    @NonNull
    public static Context scrollerContext(@NonNull Context context) {
        return new android.view.ContextThemeWrapper(
            context, R.style.ThemeOverlay_Termux_EditorShellScroller);
    }

    /** How long the mark stays up after the finger stops, in milliseconds. */
    public static final int SCROLLBAR_FADE_DELAY_MS = 1000;

    /**
     * Turns the fade and the scrollbar on for a body scroller.
     *
     * <p>The scrollbar's thumb has to be handed over here. A view built with the one-argument
     * constructor never reads the scrollbar attributes off its theme — only the inflating
     * constructors do — so turning the scrollbar on without a thumb leaves the platform with
     * nothing to draw, and it throws the moment a body is long enough to show one. The themed
     * context still carries the size; only the thumb needs setting, and only from the release that
     * can.
     *
     * <p>It fades, and it has no track. A 3dp accent bar standing there permanently against a rule
     * of its own read as a piece of the card's furniture rather than as a position in a list; a
     * quiet mark that appears while the list is moving and goes a second after it stops says the
     * same thing and then stops saying it.
     */
    public static void applyBodyScroller(@NonNull View scroller) {
        float density = scroller.getResources().getDisplayMetrics().density;
        scroller.setVerticalFadingEdgeEnabled(true);
        scroller.setFadingEdgeLength(EditorShellMetrics.px(EditorShellMetrics.FADE_DP, density));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Drawable thumb = ContextCompat.getDrawable(
                scroller.getContext(), R.drawable.editor_shell_scrollbar_thumb);
            if (thumb != null) {
                scroller.setVerticalScrollbarThumbDrawable(thumb);
                scroller.setVerticalScrollbarTrackDrawable(null);
                scroller.setVerticalScrollBarEnabled(true);
                scroller.setScrollbarFadingEnabled(true);
                scroller.setScrollBarDefaultDelayBeforeFade(SCROLLBAR_FADE_DELAY_MS);
            }
        }
        int inset = EditorShellMetrics.px(2, density);
        scroller.setPadding(scroller.getPaddingLeft(), scroller.getPaddingTop(), inset,
            scroller.getPaddingBottom());
    }

    /**
     * The height a body scroller may actually take, ending on a whole row.
     *
     * <p>Called from the scroller's own {@code onMeasure}, after one pass at {@code availablePx},
     * and the answer re-measured at. That is the only moment the rows' real heights are known: the
     * cap used to be worked out while the card was being restated, from heights left over from the
     * <em>last</em> layout — and on the first open there was no last layout, so no cut was taken at
     * all and the body ended wherever the arithmetic landed, which was through the middle of a row.
     *
     * @param scroller the body scroller, whose one child is the column of rows
     */
    public static int wholeRowCapPx(@NonNull ViewGroup scroller, int availablePx, float density) {
        if (scroller.getChildCount() == 0)
            return availablePx;
        View child = scroller.getChildAt(0);
        if (!(child instanceof ViewGroup))
            return availablePx;
        ViewGroup column = (ViewGroup) child;
        int count = column.getChildCount();
        if (count == 0)
            return availablePx;
        int[] heights = new int[count];
        for (int index = 0; index < count; index++) {
            View row = column.getChildAt(index);
            if (row.getVisibility() == View.GONE)
                continue;
            int height = row.getMeasuredHeight();
            ViewGroup.LayoutParams params = row.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
                height += margins.topMargin + margins.bottomMargin;
            }
            heights[index] = height;
        }
        return EditorShellMetrics.bodyCap(availablePx, heights,
            EditorShellMetrics.px(EditorShellMetrics.PEEK_DP, density)).capPx;
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
