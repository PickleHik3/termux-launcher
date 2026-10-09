package com.termux.app.launcher.widget.builtin;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives a widget's small controls the touch area they need without changing how they look.
 *
 * <p>Installed on a span's root layout, it sees only the touches no child took. A tap within
 * 24dp of a control's centre (a 48dp square at least) clicks that control — the task "+" disc,
 * the Add pill, a file chip. A tap inside a list region clicks the nearest row, so the gap
 * between two checklist rows still toggles one of them. Anything else falls through to the card,
 * which opens what the widget shows. Each control and row stays clickable on its own, with its
 * own content description, for touches that land on it and for accessibility services.</p>
 */
final class FilesWidgetTapRouter implements View.OnTouchListener {
    private final ViewGroup root;
    private final int minTargetPx;
    private final int slop;
    private final List<View> controls = new ArrayList<>();
    private final List<View> rows = new ArrayList<>();
    @Nullable private View region;
    @Nullable private View target;
    private float downX, downY;
    private final Rect rect = new Rect();

    private FilesWidgetTapRouter(@NonNull ViewGroup root, @NonNull BuiltinWidgetUi ui) {
        this.root = root;
        minTargetPx = ui.dp(48);
        slop = ViewConfiguration.get(root.getContext()).getScaledTouchSlop();
    }

    @SuppressLint("ClickableViewAccessibility") // Taps end in the target's own performClick().
    @NonNull static FilesWidgetTapRouter install(@NonNull ViewGroup root, @NonNull BuiltinWidgetUi ui) {
        FilesWidgetTapRouter router = new FilesWidgetTapRouter(root, ui);
        root.setOnTouchListener(router);
        return router;
    }

    /** A control whose touch area grows to 48dp around its centre. */
    void control(@NonNull View view) { controls.add(view); }

    /** Taps inside {@code area} click the nearest of {@code items}. */
    void rows(@NonNull View area, @NonNull List<? extends View> items) {
        region = area;
        rows.clear();
        rows.addAll(items);
    }

    @SuppressLint("ClickableViewAccessibility") // A tap ends in the target's performClick().
    @Override public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                target = resolve(event.getX(), event.getY());
                downX = event.getX();
                downY = event.getY();
                return target != null;
            case MotionEvent.ACTION_MOVE:
                if (target != null && moved(event)) target = null;
                return true;
            case MotionEvent.ACTION_UP:
                View tapped = target;
                target = null;
                if (tapped != null && !moved(event)) tapped.performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                target = null;
                return true;
            default:
                return true;
        }
    }

    private boolean moved(@NonNull MotionEvent event) {
        return Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop;
    }

    @Nullable private View resolve(float x, float y) {
        for (View control : controls) {
            if (!usable(control)) continue;
            bounds(control);
            int growX = Math.max(0, (minTargetPx - rect.width()) / 2);
            int growY = Math.max(0, (minTargetPx - rect.height()) / 2);
            rect.inset(-growX, -growY);
            if (rect.contains((int) x, (int) y)) return control;
        }
        if (region == null || !usable(region)) return null;
        bounds(region);
        if (!rect.contains((int) x, (int) y)) return null;
        View nearest = null;
        float best = Float.MAX_VALUE;
        for (View row : rows) {
            if (!usable(row)) continue;
            bounds(row);
            float dx = Math.max(0f, Math.max(rect.left - x, x - rect.right));
            float dy = Math.max(0f, Math.max(rect.top - y, y - rect.bottom));
            float distance = dx * dx + dy * dy;
            if (distance < best) { best = distance; nearest = row; }
        }
        return nearest;
    }

    private static boolean usable(@NonNull View view) {
        return view.isShown() && view.isEnabled() && view.getWidth() > 0 && view.getHeight() > 0;
    }

    /** {@code view}'s bounds in the root's coordinates, into {@link #rect}. */
    private void bounds(@NonNull View view) {
        view.getDrawingRect(rect);
        root.offsetDescendantRectToMyCoords(view, rect);
    }
}
