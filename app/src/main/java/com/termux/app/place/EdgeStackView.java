package com.termux.app.place;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.place.PlaceLayout.Edge;

import java.util.List;

/**
 * The one host every movable bar stands in: a stack on a screen edge, holding its bars in the order
 * {@link EdgeStackPolicy#stack} gives them. Four of these — one per edge — are all the chrome hosts
 * the launcher has; a bar is a single view that gets re-parented from one stack to another rather
 * than a host per place it might go.
 *
 * <p>The edge decides the axis: a stack on a side runs across the screen, so its bars stand beside
 * one another and it is a horizontal row of columns; a stack on the top or the bottom runs down, so
 * its bars lie on one another and it is a vertical column of rows.
 *
 * <p>{@link #setStack} is given the bars <em>outermost first</em>, the way the policy counts them —
 * 0 is the band against the glass. On the top and the left edges that is also the first child, but
 * on the bottom and the right the outermost band is the <em>last</em> one a {@link LinearLayout}
 * lays out, so the order is reversed on the way in. Callers never have to know which.
 *
 * <p>The stack carries the display cutout on its own side as padding ({@link #setCutoutPx}), so
 * every bar in it is a plain band of its own thickness and the edge's whole reach is the cutout
 * plus those bands — exactly what {@link EdgeStackPolicy#contentInsets} answers.
 */
public class EdgeStackView extends LinearLayout {

    @NonNull private Edge mEdge = Edge.TOP;

    public EdgeStackView(@NonNull Context context) {
        this(context, null);
    }

    public EdgeStackView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        Edge edge = Edge.TOP;
        if (attrs != null) {
            TypedArray values = context.obtainStyledAttributes(attrs, R.styleable.EdgeStackView);
            try {
                int ordinal = values.getInt(R.styleable.EdgeStackView_edgeStackEdge,
                    Edge.TOP.ordinal());
                Edge[] edges = Edge.values();
                if (ordinal >= 0 && ordinal < edges.length) edge = edges[ordinal];
            } finally {
                values.recycle();
            }
        }
        setEdge(edge);
    }

    /** The edge this stack holds. */
    @NonNull
    public Edge getEdge() {
        return mEdge;
    }

    /** Moves the whole stack to another edge, which turns it and reverses the order it draws in. */
    public void setEdge(@NonNull Edge edge) {
        mEdge = edge;
        setOrientation(edge.isOnSide() ? HORIZONTAL : VERTICAL);
    }

    /** How far in from its own side the stack starts: the display cutout under it, and nothing else. */
    public void setCutoutPx(int cutoutPx) {
        int cutout = Math.max(0, cutoutPx);
        int left = mEdge == Edge.LEFT ? cutout : 0;
        int right = mEdge == Edge.RIGHT ? cutout : 0;
        if (getPaddingLeft() == left && getPaddingRight() == right) return;
        setPadding(left, 0, right, 0);
    }

    /**
     * Stands these bars on this edge, outermost first, adopting any that are still children of
     * another stack. Nothing that is not in the list is taken out: every bar belongs to exactly one
     * edge, so one pass over all four stacks leaves each of them holding only its own.
     *
     * @return whether anything actually moved, which the crops cut against these views have to be
     *     told about
     */
    public boolean setStack(@NonNull List<View> outermostFirst) {
        boolean reversed = mEdge == Edge.BOTTOM || mEdge == Edge.RIGHT;
        int count = outermostFirst.size();
        boolean moved = false;
        for (int position = 0; position < count; position++) {
            View bar = outermostFirst.get(reversed ? count - 1 - position : position);
            if (bar == null) continue;
            ViewGroup parent = bar.getParent() instanceof ViewGroup
                ? (ViewGroup) bar.getParent() : null;
            if (parent == this && indexOfChild(bar) == position) continue;
            if (parent != null) parent.removeView(bar);
            addView(bar, Math.min(position, getChildCount()));
            moved = true;
        }
        return moved;
    }
}
