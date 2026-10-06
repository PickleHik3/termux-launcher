package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

/** Placeholder until the real widget lands: the kind's name, centred. */
public class TasksWidgetView extends BuiltinWidgetView {
    public TasksWidgetView(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                 @NonNull BuiltinWidgetStyle style) {
        super(context, BuiltinWidgetKind.TASKS, services, style);
    }

    @Override protected void onBuild(@NonNull FrameLayout frame, @NonNull BuiltinWidgetSpan span,
                                     @NonNull BuiltinWidgetUi ui) {
        android.widget.TextView label = ui.caption(getContext().getString(kind.label));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        frame.addView(label, params);
    }
}
