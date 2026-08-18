package com.termux.app.terminal;

import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.TermuxActivity;

import java.util.Locale;

/** A small, non-focusable indicator shown while a multi-stroke binding is pending. */
final class TerminalKeyChordOverlay {

    private final TermuxActivity activity;
    private TextView label;

    TerminalKeyChordOverlay(@NonNull TermuxActivity activity) {
        this.activity = activity;
    }

    void show(@NonNull String normalizedSequence) {
        FrameLayout host = activity.findViewById(R.id.terminal_root_container);
        if (host == null) return;
        if (label == null) label = createLabel();
        if (label.getParent() != host) {
            if (label.getParent() instanceof ViewGroup) {
                ((ViewGroup) label.getParent()).removeView(label);
            }
            host.addView(label);
        }
        label.setText(activity.getString(R.string.terminal_key_chord_pending,
            displaySequence(normalizedSequence)));
        label.setVisibility(TextView.VISIBLE);
        label.announceForAccessibility(label.getText());
    }

    void showMode(@NonNull String mode) {
        FrameLayout host = activity.findViewById(R.id.terminal_root_container);
        if (host == null) return;
        if (label == null) label = createLabel();
        if (label.getParent() != host) {
            if (label.getParent() instanceof ViewGroup)
                ((ViewGroup) label.getParent()).removeView(label);
            host.addView(label);
        }
        label.setText(activity.getString(R.string.terminal_key_mode_active, mode));
        label.setVisibility(TextView.VISIBLE);
    }

    /**
     * Reports a bound action that refused to run, and hides itself again shortly after.
     *
     * <p>Without this a failing binding is indistinguishable from an unbound stroke: both swallow
     * the keys and show nothing, which is exactly how a rename stroke that answered
     * {@code 409 no_session} looked like a dead key.
     */
    void showFailure(@NonNull String stroke, @NonNull String message) {
        FrameLayout host = activity.findViewById(R.id.terminal_root_container);
        if (host == null) return;
        if (label == null) label = createLabel();
        if (label.getParent() != host) {
            if (label.getParent() instanceof ViewGroup)
                ((ViewGroup) label.getParent()).removeView(label);
            host.addView(label);
        }
        label.setText(activity.getString(R.string.terminal_key_binding_failed,
            displaySequence(stroke), message));
        label.setVisibility(TextView.VISIBLE);
        label.announceForAccessibility(label.getText());
        TextView shown = label;
        shown.removeCallbacks(hideRunnable);
        shown.postDelayed(hideRunnable, FAILURE_VISIBLE_MS);
    }

    /**
     * Confirms the binding that just ran, then gets out of the way. Several actions change nothing
     * visible on their own — a rename prompt on a pane already named, a layout cycle between two
     * similar layouts — and without this the stroke and a dead key look the same.
     */
    void showAction(@NonNull String stroke, @NonNull String name) {
        FrameLayout host = activity.findViewById(R.id.terminal_root_container);
        if (host == null) return;
        if (label == null) label = createLabel();
        if (label.getParent() != host) {
            if (label.getParent() instanceof ViewGroup)
                ((ViewGroup) label.getParent()).removeView(label);
            host.addView(label);
        }
        label.setText(activity.getString(R.string.terminal_key_binding_ran,
            displaySequence(stroke), name));
        label.setVisibility(TextView.VISIBLE);
        label.announceForAccessibility(label.getText());
        TextView shown = label;
        shown.removeCallbacks(hideRunnable);
        shown.postDelayed(hideRunnable, ACTION_VISIBLE_MS);
    }

    private static final long ACTION_VISIBLE_MS = 950L;
    private static final long FAILURE_VISIBLE_MS = 2400L;
    private final Runnable hideRunnable = this::hide;

    void hide() {
        if (label != null) label.removeCallbacks(hideRunnable);
        if (label == null) return;
        label.setVisibility(TextView.GONE);
        if (label.getParent() instanceof ViewGroup) {
            ((ViewGroup) label.getParent()).removeView(label);
        }
    }

    @NonNull
    private TextView createLabel() {
        TextView view = new TextView(activity);
        view.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        int horizontal = dp(14);
        int vertical = dp(9);
        view.setPadding(horizontal, vertical, horizontal, vertical);
        view.setElevation(dp(6));
        view.setFocusable(false);
        view.setClickable(false);
        view.setImportantForAccessibility(TextView.IMPORTANT_FOR_ACCESSIBILITY_YES);

        int surface = MaterialColors.getColor(activity,
            com.google.android.material.R.attr.colorSurfaceContainerHigh, 0xff303034);
        int foreground = MaterialColors.getColor(activity,
            com.google.android.material.R.attr.colorOnSurface, 0xffffffff);
        GradientDrawable background = new GradientDrawable();
        background.setColor(surface);
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), MaterialColors.getColor(activity,
            com.google.android.material.R.attr.colorOutlineVariant, 0xff777777));
        view.setBackground(background);
        view.setTextColor(foreground);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = dp(20);
        view.setLayoutParams(params);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    @NonNull
    static String displaySequence(@NonNull String normalizedSequence) {
        StringBuilder result = new StringBuilder();
        for (String stroke : normalizedSequence.split(">")) {
            if (result.length() > 0) result.append("  ›  ");
            String[] pieces = stroke.split("\\+");
            for (int i = 0; i < pieces.length; i++) {
                if (i > 0) result.append('+');
                String piece = pieces[i];
                if (piece.length() == 1) {
                    result.append(piece.toUpperCase(Locale.US));
                } else {
                    result.append(Character.toUpperCase(piece.charAt(0))).append(piece.substring(1));
                }
            }
        }
        return result.toString();
    }
}
