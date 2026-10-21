package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.widget.builtin.WidgetLayoutPlan.Part;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link WidgetPlanView} with views on top of it: for every TOUCH part of the plan the widget
 * is asked for a view, which is placed in the part's box. A widget whose art, buttons or rows
 * are real views (they take touches, ripples and accessibility) keeps them, and the plan still
 * says where each one goes. The views are rebuilt when the plan is, which is when the size
 * changes. Additive to the plan view; it draws nothing itself.
 */
final class WidgetPlanHost extends FrameLayout {
    /** Makes the view for a TOUCH part, or null to leave its box empty. */
    interface ViewSource {
        @Nullable View view(@NonNull Part part);
        /** The plan changed and its views were rebuilt: bind them to the content again. */
        void onPlanned(@NonNull WidgetLayoutPlan plan);
    }

    @NonNull private final WidgetPlanView planView;
    @NonNull private final ViewSource source;
    private final float density;
    private final List<View> placed = new ArrayList<>();
    @Nullable private WidgetLayoutPlan synced;
    @Nullable private WidgetLayoutPlan latest;

    WidgetPlanHost(@NonNull Context context, @NonNull BuiltinWidgetStyle style,
                   @NonNull WidgetPlanView.Planner planner, @NonNull ViewSource source) {
        super(context);
        this.source = source;
        density = context.getResources().getDisplayMetrics().density;
        planView = new WidgetPlanView(context, style, (w, h, measure) -> {
            WidgetLayoutPlan plan = planner.plan(w, h, measure);
            latest = plan;
            return plan;
        });
        addView(planView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    /** The plan on screen, null before the first layout. */
    @Nullable WidgetLayoutPlan plan() { return latest; }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        WidgetLayoutPlan plan = latest;
        if (plan == null || plan == synced) return;
        synced = plan;
        // Inside the layout pass: removeView would ask for another layout, as addView would.
        for (View view : placed) removeViewInLayout(view);
        placed.clear();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int width = right - left;
        for (Part part : plan.parts) {
            if (part.type != WidgetLayoutPlan.Type.TOUCH) continue;
            View view = source.view(part);
            if (view == null) continue;
            int w = Math.max(1, Math.round(part.box.width * density));
            int h = Math.max(1, Math.round(part.box.height * density));
            int x = Math.round(part.box.left * density);
            if (rtl) x = width - x - w;
            int y = Math.round(part.box.top * density);
            addViewInLayout(view, -1, new LayoutParams(w, h), true);
            view.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
            view.layout(x, y, x + w, y + h);
            placed.add(view);
        }
        source.onPlanned(plan);
    }
}
