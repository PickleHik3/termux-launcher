package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A row or a column that keeps what matters when the room is short. Each child carries a rank;
 * when the children do not fit along the main axis, the lowest rank is left out whole, then the
 * next, until what is left fits ({@link #ESSENTIAL} is never left out). A child left out is not
 * laid out at all and is detached from the stack, so nothing of it is drawn, touched, or counted
 * against the card's bounds; it comes back by itself when the room does. The decision is made in
 * {@code onMeasure} from the room the parent offers and applied in {@code onLayout} with
 * {@link #addViewInLayout}, which asks for no further layout.
 *
 * <p>Children with the same rank are left out together, so a set of secondary lines goes as one
 * and the card never shows a caption under one row but not the next. Along the main axis a child
 * is one of: {@link #add content-sized}, {@link #addFlex flexible} (takes what the others leave,
 * never less than its minimum), {@link #addShrink shrinkable} (wants a size, settles for less
 * before anything is dropped) or {@link #addElastic content-sized behind a gap that stretches}
 * when there is room to spare, which is how a column pushes its last row to the bottom. A child's
 * own width or height in its layout params is honoured; {@code MATCH_PARENT} across the stack
 * fills it; margins across the stack are kept.</p>
 */
final class FitStack extends ViewGroup {
    /** The rank of a child that is never left out. */
    static final int ESSENTIAL = Integer.MAX_VALUE;
    /** The rank a list's first row starts at; each later row ranks one below the one before. */
    private static final int ROW_RANK = 1 << 20;

    private enum Kind { FIXED, FLEX, SHRINK }

    private static final class Slot {
        final View view;
        final int rank;
        final int gap;
        final Kind kind;
        final boolean elastic;
        final int preferred;
        final int minimum;
        boolean shown;
        int main;

        Slot(View view, int rank, int gap, Kind kind, boolean elastic, int preferred, int minimum) {
            this.view = view; this.rank = rank; this.gap = gap; this.kind = kind;
            this.elastic = elastic; this.preferred = preferred; this.minimum = minimum;
        }
    }

    private final boolean vertical;
    private final List<Slot> slots = new ArrayList<>();
    private boolean crossCentered;
    private boolean mainCentered;
    private int rows;
    // What the last measure decided, for the layout that follows it.
    private int leadPx;
    private int stretchPx;

    private FitStack(@NonNull Context context, boolean vertical) {
        super(context);
        this.vertical = vertical;
    }

    /** A column: children top to bottom. */
    @NonNull static FitStack column(@NonNull Context context) { return new FitStack(context, true); }

    /** A row: children start to end. */
    @NonNull static FitStack row(@NonNull Context context) { return new FitStack(context, false); }

    /** Centres the children across the stack (a column's children horizontally). */
    @NonNull FitStack centerAcross() { crossCentered = true; return this; }

    /** Centres the children along the stack when there is room left over and nothing takes it. */
    @NonNull FitStack centerAlong() { mainCentered = true; return this; }

    /** A content-sized child, {@code gapPx} after the one before it. */
    @NonNull FitStack add(@NonNull View child, int rank, int gapPx) {
        slots.add(new Slot(child, rank, gapPx, Kind.FIXED, false, 0, 0));
        return this;
    }

    /**
     * A content-sized child whose gap grows with any room the stack has to spare, shared with the
     * other elastic children: {@code gapPx} at the least.
     */
    @NonNull FitStack addElastic(@NonNull View child, int rank, int gapPx) {
        slots.add(new Slot(child, rank, gapPx, Kind.FIXED, true, 0, 0));
        return this;
    }

    /** A child that takes the room the others leave, and is left out if it cannot have {@code minPx}. */
    @NonNull FitStack addFlex(@NonNull View child, int rank, int gapPx, int minPx) {
        slots.add(new Slot(child, rank, gapPx, Kind.FLEX, false, 0, minPx));
        return this;
    }

    /** A child that wants {@code preferredPx} along the stack and takes as little as {@code minPx}. */
    @NonNull FitStack addShrink(@NonNull View child, int rank, int gapPx, int preferredPx,
                                int minPx) {
        slots.add(new Slot(child, rank, gapPx, Kind.SHRINK, false, preferredPx, minPx));
        return this;
    }

    /**
     * The next row of a list: the first row outranks the second, the second the third, so the
     * list shows as many whole rows from the top as there is room for. Rows take the full width
     * of a column.
     */
    @NonNull FitStack addRow(@NonNull View child, int gapPx) {
        ViewGroup.LayoutParams params = child.getLayoutParams();
        if (params == null) {
            child.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        } else if (vertical) {
            params.width = MATCH_PARENT;
        }
        return add(child, ROW_RANK - rows++, gapPx);
    }

    // ----- measure --------------------------------------------------------------------------

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int mainSpec = vertical ? heightMeasureSpec : widthMeasureSpec;
        int crossSpec = vertical ? widthMeasureSpec : heightMeasureSpec;
        int padMain = vertical ? getPaddingTop() + getPaddingBottom()
            : getPaddingLeft() + getPaddingRight();
        int padCross = vertical ? getPaddingLeft() + getPaddingRight()
            : getPaddingTop() + getPaddingBottom();
        boolean mainBounded = MeasureSpec.getMode(mainSpec) != MeasureSpec.UNSPECIFIED;
        boolean crossBounded = MeasureSpec.getMode(crossSpec) != MeasureSpec.UNSPECIFIED;
        int limit = mainBounded ? Math.max(0, MeasureSpec.getSize(mainSpec) - padMain)
            : Integer.MAX_VALUE;
        int crossLimit = crossBounded ? Math.max(0, MeasureSpec.getSize(crossSpec) - padCross) : 0;

        for (Slot slot : slots) {
            slot.shown = slot.view.getVisibility() != GONE;
            if (!slot.shown) continue;
            switch (slot.kind) {
                case SHRINK: slot.main = slot.preferred; break;
                case FLEX: slot.main = slot.minimum; break;
                default:
                    measure(slot, naturalSpec(slot), crossLimit, crossBounded);
                    slot.main = mainSize(slot.view);
                    break;
            }
        }

        // Leave out the lowest rank until what remains fits, shrinking what can shrink first.
        int need;
        while (true) {
            need = 0;
            int slack = 0;
            boolean first = true;
            for (Slot slot : slots) {
                if (!slot.shown) continue;
                need += (first ? 0 : slot.gap) + slot.main;
                if (slot.kind == Kind.SHRINK) slack += slot.preferred - slot.minimum;
                first = false;
            }
            if (!mainBounded || need - slack <= limit) break;
            int lowest = ESSENTIAL;
            for (Slot slot : slots) if (slot.shown && slot.rank < lowest) lowest = slot.rank;
            if (lowest == ESSENTIAL) break;
            for (Slot slot : slots) if (slot.shown && slot.rank == lowest) slot.shown = false;
        }

        int spare = mainBounded ? limit - need : 0;
        if (spare < 0) {
            for (int i = slots.size() - 1; i >= 0 && spare < 0; i--) {
                Slot slot = slots.get(i);
                if (!slot.shown || slot.kind != Kind.SHRINK) continue;
                int cut = Math.min(-spare, slot.preferred - slot.minimum);
                slot.main -= cut;
                spare += cut;
            }
        }
        int flexCount = 0;
        int elasticCount = 0;
        boolean opening = true;
        for (Slot slot : slots) {
            if (!slot.shown) continue;
            if (slot.kind == Kind.FLEX) flexCount++;
            // The first child has no gap before it to stretch.
            if (slot.elastic && !opening) elasticCount++;
            opening = false;
        }
        leadPx = 0;
        stretchPx = 0;
        int flexShare = 0;
        if (spare > 0) {
            if (flexCount > 0) flexShare = spare / flexCount;
            else if (elasticCount > 0) stretchPx = spare / elasticCount;
            else if (mainCentered) leadPx = spare / 2;
        }

        int used = 0;
        int cross = 0;
        boolean first = true;
        for (Slot slot : slots) {
            if (!slot.shown) continue;
            if (slot.kind == Kind.FLEX) slot.main += flexShare;
            if (slot.kind != Kind.FIXED) {
                measure(slot, MeasureSpec.makeMeasureSpec(Math.max(0, slot.main),
                    MeasureSpec.EXACTLY), crossLimit, crossBounded);
            }
            used += (first ? 0 : slot.gap + (slot.elastic ? stretchPx : 0)) + slot.main;
            cross = Math.max(cross, crossSize(slot.view) + crossMargins(slot.view));
            first = false;
        }
        int mainSize = resolveSize(leadPx + used + padMain, mainSpec);
        int crossSize = resolveSize(cross + padCross, crossSpec);
        setMeasuredDimension(vertical ? crossSize : mainSize, vertical ? mainSize : crossSize);
    }

    /** The main-axis spec of a content-sized child: its own size when it names one, else open. */
    private int naturalSpec(@NonNull Slot slot) {
        ViewGroup.LayoutParams params = slot.view.getLayoutParams();
        int dimension = params == null ? WRAP_CONTENT : vertical ? params.height : params.width;
        return dimension > 0 ? MeasureSpec.makeMeasureSpec(dimension, MeasureSpec.EXACTLY)
            : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    }

    /** Measures {@code slot}'s view with {@code mainSpec} along the stack and its own across it. */
    private void measure(@NonNull Slot slot, int mainSpec, int crossLimit, boolean crossBounded) {
        View view = slot.view;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        int dimension = params == null ? WRAP_CONTENT : vertical ? params.width : params.height;
        int room = Math.max(0, crossLimit - crossMargins(view));
        int crossSpec;
        if (!crossBounded) {
            crossSpec = dimension > 0 ? MeasureSpec.makeMeasureSpec(dimension, MeasureSpec.EXACTLY)
                : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        } else if (dimension == MATCH_PARENT) {
            crossSpec = MeasureSpec.makeMeasureSpec(room, MeasureSpec.EXACTLY);
        } else if (dimension > 0) {
            crossSpec = MeasureSpec.makeMeasureSpec(Math.min(dimension, room), MeasureSpec.EXACTLY);
        } else {
            crossSpec = MeasureSpec.makeMeasureSpec(room, MeasureSpec.AT_MOST);
        }
        view.measure(vertical ? crossSpec : mainSpec, vertical ? mainSpec : crossSpec);
    }

    private int mainSize(@NonNull View view) {
        return vertical ? view.getMeasuredHeight() : view.getMeasuredWidth();
    }

    private int crossSize(@NonNull View view) {
        return vertical ? view.getMeasuredWidth() : view.getMeasuredHeight();
    }

    private int crossStartMargin(@NonNull View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof MarginLayoutParams)) return 0;
        MarginLayoutParams margins = (MarginLayoutParams) params;
        return vertical ? margins.leftMargin : margins.topMargin;
    }

    private int crossMargins(@NonNull View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof MarginLayoutParams)) return 0;
        MarginLayoutParams margins = (MarginLayoutParams) params;
        return vertical ? margins.leftMargin + margins.rightMargin
            : margins.topMargin + margins.bottomMargin;
    }

    // ----- layout ---------------------------------------------------------------------------

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Attach what is shown and detach what is left out, without asking for another layout.
        int index = 0;
        for (Slot slot : slots) {
            boolean attached = slot.view.getParent() == this;
            if (slot.shown) {
                if (!attached) {
                    ViewGroup.LayoutParams params = slot.view.getLayoutParams();
                    addViewInLayout(slot.view, index, params != null ? params
                        : generateDefaultLayoutParams(), true);
                }
                index++;
            } else if (attached) {
                removeViewInLayout(slot.view);
            }
        }

        int width = r - l;
        int height = b - t;
        boolean rtl = !vertical && getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int crossRoom = vertical ? width - getPaddingLeft() - getPaddingRight()
            : height - getPaddingTop() - getPaddingBottom();
        int offset = leadPx;
        boolean first = true;
        for (Slot slot : slots) {
            if (!slot.shown) continue;
            if (!first) offset += slot.gap + (slot.elastic ? stretchPx : 0);
            first = false;
            View view = slot.view;
            int w = view.getMeasuredWidth();
            int h = view.getMeasuredHeight();
            int crossStart = (vertical ? getPaddingLeft() : getPaddingTop()) + crossStartMargin(view);
            if (crossCentered) {
                int room = crossRoom - crossMargins(view);
                crossStart += Math.max(0, (room - crossSize(view)) / 2);
            }
            if (vertical) {
                int top = getPaddingTop() + offset;
                view.layout(crossStart, top, crossStart + w, top + h);
                offset += h;
            } else {
                int left = rtl ? width - getPaddingRight() - offset - w : getPaddingLeft() + offset;
                view.layout(left, crossStart, left + w, crossStart + h);
                offset += w;
            }
        }
    }
}
