package com.termux.app.assistant;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/**
 * The one notice above the composer (sheets 02.9, 02.10, 03.8 and 04): no model, downloading,
 * loading, a model failure, and the microphone and speech-model asks. One view with a few content
 * states; it is told what to say and says nothing of its own. Opaque in both glass modes.
 */
final class AssistantNoticeView extends LinearLayout {

    /** What the notice says. Equal contents are not applied twice. */
    static final class Content {
        @NonNull final String title;
        @NonNull final String body;
        /** 0 to 100 for a determinate bar; -1 for none. */
        final int progress;
        final boolean error;
        @Nullable final String secondaryLabel;
        @Nullable final String primaryLabel;
        @Nullable final Runnable secondary;
        @Nullable final Runnable primary;
        /** The primary button is the filled one (4.1's Download); otherwise tonal or outlined. */
        final boolean primaryFilled;

        Content(@NonNull String title, @NonNull String body, int progress, boolean error,
                @Nullable String secondaryLabel, @Nullable Runnable secondary,
                @Nullable String primaryLabel, @Nullable Runnable primary, boolean primaryFilled) {
            this.title = title;
            this.body = body;
            this.progress = progress;
            this.error = error;
            this.secondaryLabel = secondaryLabel;
            this.secondary = secondary;
            this.primaryLabel = primaryLabel;
            this.primary = primary;
            this.primaryFilled = primaryFilled;
        }

        /** The words and shape, without the actions. */
        boolean sameFace(@Nullable Content o) {
            return o != null && title.equals(o.title) && body.equals(o.body) && progress == o.progress
                && error == o.error && primaryFilled == o.primaryFilled
                && java.util.Objects.equals(secondaryLabel, o.secondaryLabel)
                && java.util.Objects.equals(primaryLabel, o.primaryLabel);
        }
    }

    private final TextView title;
    private final TextView body;
    private final LinearProgressIndicator progress;
    private final LinearLayout buttons;
    private final MaterialButton secondary;
    private final MaterialButton primary;
    @Nullable private Content shown;
    @Nullable private Runnable secondaryAction;
    @Nullable private Runnable primaryAction;
    private AssistantUi.Palette palette;

    AssistantNoticeView(@NonNull Context context, @NonNull AssistantUi.Palette palette) {
        super(context);
        this.palette = palette;
        setOrientation(VERTICAL);
        int pad = AssistantUi.dp(context, 16);
        setPadding(pad, pad, pad, pad);
        title = AssistantUi.text(context);
        body = AssistantUi.text(context);
        addView(title, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LayoutParams bodyParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = AssistantUi.dp(context, 4);
        addView(body, bodyParams);
        progress = new LinearProgressIndicator(context);
        progress.setIndeterminate(false);
        progress.setMax(100);
        progress.setTrackThickness(AssistantUi.dp(context, 4));
        progress.setTrackCornerRadius(AssistantUi.dp(context, 2));
        LayoutParams progressParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.topMargin = AssistantUi.dp(context, 8);
        addView(progress, progressParams);
        buttons = new LinearLayout(context);
        buttons.setOrientation(HORIZONTAL);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        secondary = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        primary = new MaterialButton(context);
        LayoutParams btn = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttons.addView(secondary, btn);
        LayoutParams btn2 = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btn2.setMarginStart(AssistantUi.dp(context, 8));
        buttons.addView(primary, btn2);
        LayoutParams buttonsParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsParams.topMargin = AssistantUi.dp(context, 8);
        addView(buttons, buttonsParams);
        secondary.setOnClickListener(v -> {
            if (secondaryAction != null) secondaryAction.run();
        });
        primary.setOnClickListener(v -> {
            if (primaryAction != null) primaryAction.run();
        });
        setVisibility(GONE);
        setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    void setPalette(@NonNull AssistantUi.Palette p) {
        palette = p;
        if (shown != null) {
            Content c = shown;
            shown = null;
            show(c);
        }
    }

    /** Shows {@code content}, or hides the notice for null. */
    void show(@Nullable Content content) {
        if (content == null) {
            shown = null;
            secondaryAction = null;
            primaryAction = null;
            AssistantUi.setVisible(this, false);
            return;
        }
        secondaryAction = content.secondary;
        primaryAction = content.primary;
        if (content.sameFace(shown) && getVisibility() == VISIBLE) {
            shown = content;
            return;
        }
        shown = content;
        int ink = content.error ? palette.onErrorContainer : palette.onSurface;
        int inkVariant = content.error ? palette.onErrorContainer : palette.onSurfaceVariant;
        setBackground(AssistantUi.fill(content.error ? palette.errorContainer : palette.surfaceHigh,
            AssistantUi.dp(this, 20)));
        AssistantUi.label(title, ink);
        AssistantUi.bodySmall(body, inkVariant);
        AssistantUi.setText(title, content.title);
        AssistantUi.setVisible(body, !content.body.isEmpty());
        AssistantUi.setText(body, content.body);
        AssistantUi.setVisible(progress, content.progress >= 0);
        if (content.progress >= 0) {
            progress.setIndicatorColor(palette.primary);
            progress.setTrackColor(ColorUtils.setAlphaComponent(palette.primary, 61));
            progress.setProgressCompat(content.progress, false);
        }
        bindButton(secondary, content.secondaryLabel, false, content, false);
        bindButton(primary, content.primaryLabel, true, content, content.primaryFilled);
        AssistantUi.setVisible(buttons, content.primaryLabel != null || content.secondaryLabel != null);
        AssistantUi.setVisible(this, true);
    }

    private void bindButton(@NonNull MaterialButton button, @Nullable String label, boolean isPrimary,
                            @NonNull Content content, boolean filled) {
        AssistantUi.setVisible(button, label != null);
        if (label == null) return;
        button.setText(label);
        button.setMinHeight(AssistantUi.dp(this, 40));
        if (content.error) {
            // Outlined in the error container's ink.
            button.setBackgroundTintList(ColorStateList.valueOf(0));
            button.setTextColor(palette.onErrorContainer);
            button.setStrokeColor(ColorStateList.valueOf(palette.onErrorContainer));
            button.setStrokeWidth(AssistantUi.dp(this, 1));
        } else if (isPrimary && filled) {
            button.setBackgroundTintList(ColorStateList.valueOf(palette.primary));
            button.setTextColor(palette.onPrimary);
            button.setStrokeWidth(0);
        } else if (isPrimary) {
            button.setBackgroundTintList(ColorStateList.valueOf(palette.secondaryContainer));
            button.setTextColor(palette.onSecondaryContainer);
            button.setStrokeWidth(0);
        } else {
            // A text button in primary.
            button.setBackgroundTintList(ColorStateList.valueOf(0));
            button.setTextColor(palette.primary);
            button.setStrokeWidth(0);
        }
    }
}
