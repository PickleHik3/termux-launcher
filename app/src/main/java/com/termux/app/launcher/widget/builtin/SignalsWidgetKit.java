package com.termux.app.launcher.widget.builtin;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.MetricAffectingSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;

/** Small pieces the Notifications and Command widgets share beyond {@link BuiltinWidgetUi}. */
final class SignalsWidgetKit {
    /** The smallest touch target a control gets. */
    static final int TOUCH_DP = 48;

    private SignalsWidgetKit() { }

    /** The direction's mono face at the design's 600 weight. */
    @NonNull static Typeface monoBold(@NonNull BuiltinWidgetStyle style) {
        return Typeface.create(style.mono, Typeface.BOLD);
    }

    /** CSS's {@code line-height: <multiple>}: the line pitch as a multiple of the font size. */
    static void lineHeight(@NonNull TextView view, float sp, float multiple) {
        float px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp * multiple,
            view.getResources().getDisplayMetrics());
        TextViewCompat.setLineHeight(view, Math.round(px));
    }

    /**
     * {@code disc} (a {@link BuiltinWidgetUi#roundButton} of {@code discDp}) centred in a 48dp
     * frame that takes the clicks, so the visible disc stays the design's size and the target
     * does not. The frame carries the description and the click listener.
     */
    @NonNull static FrameLayout touchFrame(@NonNull Context context, @NonNull View disc, int discDp,
                                           @NonNull BuiltinWidgetUi ui, @NonNull View.OnClickListener click) {
        FrameLayout frame = new FrameLayout(context);
        CharSequence description = disc.getContentDescription();
        disc.setClickable(false);
        disc.setFocusable(false);
        disc.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ui.dp(discDp), ui.dp(discDp));
        params.gravity = Gravity.CENTER;
        frame.addView(disc, params);
        frame.setContentDescription(description);
        frame.setClickable(true);
        frame.setFocusable(true);
        frame.setOnClickListener(click);
        int size = Math.max(ui.dp(TOUCH_DP), ui.dp(discDp));
        frame.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return frame;
    }

    /**
     * Makes {@code target} answer touches in at least a 48dp square around it, through a
     * {@link TouchDelegate} on {@code root} (an ancestor that spans the card), refreshed whenever
     * the target is laid out. One per root: a later call replaces the earlier delegate.
     */
    static void expandTouch(@NonNull ViewGroup root, @NonNull View target, @NonNull BuiltinWidgetUi ui) {
        int min = ui.dp(TOUCH_DP);
        target.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (view.getParent() == null) return;
            Rect rect = new Rect();
            view.getDrawingRect(rect);
            try {
                root.offsetDescendantRectToMyCoords(view, rect);
            } catch (IllegalArgumentException notADescendant) {
                return;
            }
            int growX = Math.max(0, (min - rect.width()) / 2);
            int growY = Math.max(0, (min - rect.height()) / 2);
            rect.inset(-growX, -growY);
            root.setTouchDelegate(new TouchDelegate(rect, view));
        });
    }

    /** Starts {@code intent} as a new task; false when nothing can open it. */
    static boolean start(@NonNull Context context, @Nullable Intent intent) {
        if (intent == null) return false;
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    /**
     * Appends {@code text} to {@code out} in its own face and colour at {@code scale} times the
     * view's text size, for two pieces of text that share a baseline in one line
     * ("Ahmed  WhatsApp · 13:32"): the view is sized for the larger piece, the smaller scales down.
     */
    static void append(@NonNull SpannableStringBuilder out, @NonNull CharSequence text,
                       @NonNull Typeface face, float scale, @ColorInt int color) {
        int start = out.length();
        out.append(text);
        int end = out.length();
        if (end == start) return;
        out.setSpan(new FaceSpan(face), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (scale != 1f) {
            out.setSpan(new RelativeSizeSpan(scale), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        out.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** A typeface as a span; {@code TypefaceSpan(Typeface)} needs API 28. */
    static final class FaceSpan extends MetricAffectingSpan {
        @NonNull private final Typeface face;
        FaceSpan(@NonNull Typeface face) { this.face = face; }
        @Override public void updateDrawState(@NonNull TextPaint paint) { paint.setTypeface(face); }
        @Override public void updateMeasureState(@NonNull TextPaint paint) { paint.setTypeface(face); }
    }
}
