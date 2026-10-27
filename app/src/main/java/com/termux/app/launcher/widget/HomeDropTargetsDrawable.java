package com.termux.app.launcher.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.termux.app.notice.TerminalDress;

import java.util.ArrayList;
import java.util.List;

/**
 * The Remove and Uninstall pills drawn while a Home icon is dragged. They hang in the window's
 * overlay, over the status chrome, so they can sit in the band at the top of the screen that the
 * Home page does not reach; the overlay takes no touches, and the controller hit-tests the finger
 * itself ({@link HomeDropTargets#hit}).
 *
 * <p>Dumb on purpose: everything is measured and placed once when the drag shows them, and a frame
 * only draws what was placed. The hovered pill takes the theme's error colour; the rest wear the
 * notice pill's dress ({@link TerminalDress}), so the targets read as the launcher's own chrome and
 * follow its scheme.</p>
 */
final class HomeDropTargetsDrawable extends Drawable {

    /** The label size: the notice pill's title. */
    private static final float LABEL_SP = 14f;
    /** How long the pills take to come in. */
    private static final long FADE_MS = 150L;

    private static final class Entry {
        @NonNull final String label;
        @Nullable final Drawable glyph;
        int labelWidth;
        final RectF pill = new RectF();
        @NonNull CharSequence shown = "";
        float textX, textY;

        Entry(@NonNull String label, @Nullable Drawable glyph) {
            this.label = label;
            this.glyph = glyph;
        }
    }

    private final float density;
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Entry> entries = new ArrayList<>(2);
    private int restFill, restStroke, restText, hotFill, hotText;
    private float radius;
    private int highlighted = -1;
    private int alpha = 255;
    @Nullable private ValueAnimator fade;

    HomeDropTargetsDrawable(@NonNull Context context) {
        density = context.getResources().getDisplayMetrics().density;
        text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LABEL_SP,
            context.getResources().getDisplayMetrics()));
        stroke.setStyle(Paint.Style.STROKE);
    }

    /** The colours: the notice pill's at rest, the theme's error pair under the finger. */
    void setLook(@NonNull TerminalDress dress, int hotFillColor, int hotTextColor) {
        restFill = dress.fillColor;
        restStroke = dress.strokeColor;
        restText = dress.textColor;
        hotFill = ColorUtils.setAlphaComponent(hotFillColor, Color.alpha(dress.fillColor));
        hotText = hotTextColor;
        stroke.setStrokeWidth(dress.strokeWidthPx);
        radius = dress.cornerRadiusPx(Math.round(HomeDropTargets.PILL_HEIGHT_DP * density));
    }

    /**
     * The targets' labels and glyphs, in order; answers each pill's content width, which the
     * layout needs before {@link #place} can be called.
     */
    @NonNull
    int[] setTargets(@NonNull Context context, @NonNull List<HomeDropTargets.Target> targets,
                     @NonNull List<String> labels) {
        entries.clear();
        highlighted = -1;
        int[] widths = new int[targets.size()];
        for (int i = 0; i < targets.size(); i++) {
            Drawable glyph = ContextCompat.getDrawable(context,
                targets.get(i) == HomeDropTargets.Target.UNINSTALL
                    ? com.termux.R.drawable.ic_app_menu_uninstall
                    : com.termux.R.drawable.ic_symbol_close);
            if (glyph != null) glyph = glyph.mutate();
            Entry entry = new Entry(labels.get(i), glyph);
            entry.labelWidth = Math.round(text.measureText(entry.label));
            entries.add(entry);
            widths[i] = HomeDropTargets.contentWidth(entry.labelWidth, density);
        }
        return widths;
    }

    /** Puts the pills at {@code pills} (on screen), drawn in a window whose corner is at the offset. */
    void place(@NonNull List<HomeDropTargets.Box> pills, int windowLeft, int windowTop) {
        int pad = Math.round(HomeDropTargets.PAD_DP * density);
        int glyphPx = Math.round(HomeDropTargets.GLYPH_DP * density);
        int glyphGap = Math.round(HomeDropTargets.GLYPH_GAP_DP * density);
        Paint.FontMetrics metrics = text.getFontMetrics();
        for (int i = 0; i < entries.size() && i < pills.size(); i++) {
            Entry entry = entries.get(i);
            HomeDropTargets.Box box = pills.get(i);
            entry.pill.set(box.left - windowLeft, box.top - windowTop,
                box.right - windowLeft, box.bottom - windowTop);
            radius = Math.min(radius, entry.pill.height() / 2f);
            float room = Math.max(0f, entry.pill.width() - 2 * pad - glyphPx - glyphGap);
            entry.shown = TextUtils.ellipsize(entry.label, text, room, TextUtils.TruncateAt.END);
            float shownWidth = text.measureText(entry.shown, 0, entry.shown.length());
            float content = glyphPx + glyphGap + shownWidth;
            float left = entry.pill.centerX() - content / 2f;
            if (entry.glyph != null) {
                int glyphTop = Math.round(entry.pill.centerY() - glyphPx / 2f);
                entry.glyph.setBounds(Math.round(left), glyphTop, Math.round(left) + glyphPx,
                    glyphTop + glyphPx);
            }
            entry.textX = left + glyphPx + glyphGap;
            entry.textY = entry.pill.centerY() - (metrics.ascent + metrics.descent) / 2f;
        }
        applyGlyphTints();
        invalidateSelf();
    }

    /** The pill under the finger, or -1; true when that changed. */
    boolean setHighlighted(int index) {
        if (index == highlighted) return false;
        highlighted = index;
        applyGlyphTints();
        invalidateSelf();
        return true;
    }

    /** Brings the pills in, faded over a beat unless {@code animate} is false. */
    void fadeIn(boolean animate) {
        cancelFade();
        if (!animate) {
            setAlpha(255);
            return;
        }
        setAlpha(0);
        ValueAnimator animator = ValueAnimator.ofInt(0, 255);
        animator.setDuration(FADE_MS);
        animator.addUpdateListener(a -> setAlpha((Integer) a.getAnimatedValue()));
        fade = animator;
        animator.start();
    }

    void cancelFade() {
        if (fade != null) fade.cancel();
        fade = null;
    }

    private void applyGlyphTints() {
        for (int i = 0; i < entries.size(); i++) {
            Drawable glyph = entries.get(i).glyph;
            if (glyph == null) continue;
            glyph.setTint(i == highlighted ? hotText : restText);
            glyph.setAlpha(alpha);
        }
    }

    @Override public void draw(@NonNull Canvas canvas) {
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            boolean hot = i == highlighted;
            fill.setColor(hot ? hotFill : restFill);
            fill.setAlpha(Color.alpha(hot ? hotFill : restFill) * alpha / 255);
            canvas.drawRoundRect(entry.pill, radius, radius, fill);
            if (!hot) {
                stroke.setColor(restStroke);
                stroke.setAlpha(Color.alpha(restStroke) * alpha / 255);
                canvas.drawRoundRect(entry.pill, radius, radius, stroke);
            }
            if (entry.glyph != null) entry.glyph.draw(canvas);
            text.setColor(hot ? hotText : restText);
            text.setAlpha(Color.alpha(hot ? hotText : restText) * alpha / 255);
            canvas.drawText(entry.shown, 0, entry.shown.length(), entry.textX, entry.textY, text);
        }
    }

    @Override public void setAlpha(int value) {
        int clamped = Math.max(0, Math.min(255, value));
        if (clamped == alpha) return;
        alpha = clamped;
        for (Entry entry : entries) if (entry.glyph != null) entry.glyph.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) { }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
