package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.chrome.GroundUnderGlass;
import com.termux.app.chrome.OnGlass;
import com.termux.app.terminal.WindowChipInk;
import com.termux.app.wall.pageswitch.MarkControls;
import com.termux.app.wall.pageswitch.MarksZoomPlan;
import com.termux.app.window.WindowLayer;
import com.termux.app.window.WindowLayers;

/**
 * The Glyphs look's window controls: a held window chip opens the same controls a held mark does
 * ({@link MarkControls}), over the window bar's end in the chrome layer: the window's name in a
 * glass capsule, then round glass controls with their glyphs, Close last. Made at the hold, gone
 * at the lift; never touched (the bar keeps the stream and asks {@link #controlAt}).
 */
public final class ChipControlsView extends FrameLayout {

    private static final int CONTROLS = 4;
    private static final float GLYPH_SHARE = 0.6f;
    private static final double PALE_INK_BELOW = 0.5;

    @NonNull private final View mName;
    @NonNull private final View[] mControls = new View[CONTROLS];
    @NonNull private final Drawable[] mGlyphs = new Drawable[MarkControls.Control.values().length];
    private final TextPaint mText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics mMetrics = new Paint.FontMetrics();
    private final float mDensity;
    private final int[] mLoc = new int[2];
    private final int[] mHostLoc = new int[2];

    @NonNull private MarkControls.Control[] mSet = new MarkControls.Control[0];
    @Nullable private MarkControls.Layout mLayout;
    @Nullable private String mTitle;
    private float mNameLeft;
    private boolean mRtl;
    private int mInk;
    private int mAccent;
    private int mFocus = -1;
    /** The bar's left edge on screen, to map a raw x to the layout's line. */
    private float mBarScreenLeft;
    private int mBarWidth;

    public ChipControlsView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mName = new View(context);
        addView(mName, new LayoutParams(0, 0, Gravity.TOP | Gravity.LEFT));
        for (int i = 0; i < CONTROLS; i++) {
            View control = new View(context);
            control.setVisibility(INVISIBLE);
            addView(control, new LayoutParams(0, 0, Gravity.TOP | Gravity.LEFT));
            mControls[i] = control;
        }
        mGlyphs[MarkControls.Control.MINIMISE.ordinal()] = glyph(R.drawable.ic_titlebar_minimise);
        mGlyphs[MarkControls.Control.MAXIMISE.ordinal()] = glyph(R.drawable.ic_titlebar_maximise);
        mGlyphs[MarkControls.Control.MOVE.ordinal()] = glyph(R.drawable.ic_rl_move_window);
        mGlyphs[MarkControls.Control.CLOSE.ordinal()] = glyph(R.drawable.ic_titlebar_close);
    }

    @Nullable
    private Drawable glyph(int res) {
        Drawable d = ContextCompat.getDrawable(getContext(), res);
        return d == null ? null : d.mutate();
    }

    /**
     * Opens the controls over {@code bar}'s end for a window named {@code name}, with the finger
     * at {@code rawX}: in the chrome layer, wearing {@code glass}. False when there is no chrome
     * layer to stand in (nothing shows, and the bar's lift goes on as a reorder).
     */
    public boolean show(@NonNull View bar, @Nullable String name,
                        @NonNull MarkControls.Control[] set, float rawX, int accent,
                        @Nullable MarksLineView.TitleGlass glass) {
        hide();
        ViewGroup host = WindowLayers.host(id -> bar.getRootView().findViewById(id),
            WindowLayer.CHROME);
        if (host == null || bar.getWidth() <= 0 || bar.getHeight() <= 0) return false;
        mSet = set;
        mRtl = bar.getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        int height = bar.getHeight();
        mBarWidth = bar.getWidth();
        bar.getLocationOnScreen(mLoc);
        mBarScreenLeft = mLoc[0];
        float size = height;
        mText.setTextSize(size * MarksZoomPlan.TITLE_TEXT_SHARE * 0.6f);
        mText.getFontMetrics(mMetrics);
        float inset = MarksZoomPlan.CAPSULE_INSET_DP * mDensity;
        String fitted = name == null || name.isEmpty() ? null
            : TextUtils.ellipsize(name, mText, mBarWidth / 3f, TextUtils.TruncateAt.END).toString();
        mTitle = fitted == null || fitted.isEmpty() ? null : fitted;
        float nameWidth = mTitle == null ? 0f : mText.measureText(mTitle) + 2f * inset;
        float finger = along(rawX);
        mLayout = new MarkControls.Layout(set.length, size, 0f, mBarWidth, nameWidth, finger,
            mDensity);
        mInk = inkFor(glass, false);
        mAccent = inkFor(glass, true, accent);
        mFocus = -1;
        tint();
        for (int i = 0; i < CONTROLS; i++) {
            View control = mControls[i];
            boolean shown = i < mLayout.count();
            control.setVisibility(shown ? VISIBLE : INVISIBLE);
            if (!shown) continue;
            LayoutParams p = (LayoutParams) control.getLayoutParams();
            p.width = Math.round(size);
            p.height = Math.round(size);
            control.setLayoutParams(p);
            control.setTranslationX(mLayout.leftPx(i, mBarWidth, mRtl));
            control.setBackground(ground(glass, size / 2f, control));
        }
        if (mTitle != null) {
            float start = mLayout.nameEndPx() - nameWidth;
            mNameLeft = mRtl ? mBarWidth - start - nameWidth : start;
            LayoutParams p = (LayoutParams) mName.getLayoutParams();
            p.width = Math.round(nameWidth);
            p.height = height;
            mName.setLayoutParams(p);
            mName.setTranslationX(mNameLeft);
            mName.setBackground(ground(glass, height / 2f, mName));
            mName.setVisibility(VISIBLE);
        } else {
            mName.setVisibility(INVISIBLE);
        }
        bar.getLocationInWindow(mLoc);
        host.getLocationInWindow(mHostLoc);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(mBarWidth, height);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        setTranslationX(mLoc[0] - mHostLoc[0]);
        setTranslationY(mLoc[1] - mHostLoc[1]);
        if (!WindowLayers.add(id -> bar.getRootView().findViewById(id), WindowLayer.CHROME, this,
            params)) {
            mLayout = null;
            return false;
        }
        invalidate();
        return true;
    }

    @Nullable
    private static Drawable ground(@Nullable MarksLineView.TitleGlass glass, float radius,
                                   @NonNull View anchor) {
        Drawable d = glass == null ? null : glass.ground(radius, anchor);
        if (d != null) GroundUnderGlass.bind(d, anchor);
        return d;
    }

    private int inkFor(@Nullable MarksLineView.TitleGlass glass, boolean accent) {
        return inkFor(glass, accent, 0);
    }

    /** The ink legible on the glass: Global's tint over the surface, as the marks' title solves it. */
    private int inkFor(@Nullable MarksLineView.TitleGlass glass, boolean accent, int accentColor) {
        Context c = getContext();
        int neutral = MaterialColors.getColor(this, com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(c, R.color.termux_on_surface));
        int error = MaterialColors.getColor(this, com.termux.shared.R.attr.termuxColorError,
            ContextCompat.getColor(c, R.color.termux_error));
        int surfaceBase = MaterialColors.getColor(this,
            com.termux.shared.R.attr.termuxColorSurfaceBase,
            ContextCompat.getColor(c, R.color.termux_surface_base));
        int under = OnGlass.opaque(surfaceBase);
        int ground = glass == null ? under
            : OnGlass.opaque(ColorUtils.compositeColors(glass.tint(), under));
        boolean pale = ColorUtils.calculateLuminance(ground) < PALE_INK_BELOW;
        MarksLineInk ink = MarksLineInk.onBand(ground, pale,
            WindowChipInk.neutralSeed(neutral, surfaceBase, pale),
            accentColor != 0 ? accentColor : neutral, error);
        return accent ? ink.current : ink.other;
    }

    /** The finger's raw x along the bar's line, from its start (mirrored under right-to-left). */
    private float along(float rawX) {
        float x = rawX - mBarScreenLeft;
        return mRtl ? mBarWidth - x : x;
    }

    /** Whether the controls stand now. */
    public boolean isOpen() {
        return mLayout != null;
    }

    /** The finger moved to {@code rawX}: the control under it takes the accent. */
    public void move(float rawX) {
        MarkControls.Layout layout = mLayout;
        if (layout == null) return;
        int focus = layout.controlAt(along(rawX));
        if (focus == mFocus) return;
        mFocus = focus;
        tint();
        invalidate();
    }

    /** The control under {@code rawX}, or null. */
    @Nullable
    public MarkControls.Control controlAt(float rawX) {
        MarkControls.Layout layout = mLayout;
        if (layout == null) return null;
        int i = layout.controlAt(along(rawX));
        return i >= 0 && i < mSet.length ? mSet[i] : null;
    }

    private void tint() {
        for (int i = 0; i < mSet.length; i++) {
            Drawable glyph = mGlyphs[mSet[i].ordinal()];
            if (glyph != null) glyph.setTint(i == mFocus ? mAccent : mInk);
        }
    }

    /** Takes the controls away. Safe in any state. */
    public void hide() {
        mLayout = null;
        mFocus = -1;
        mName.setBackground(null);
        for (View control : mControls) {
            control.setBackground(null);
            control.setVisibility(INVISIBLE);
        }
        ViewParent parent = getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(this);
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        MarkControls.Layout layout = mLayout;
        if (layout == null) return;
        float cy = getHeight() / 2f;
        if (mTitle != null) {
            float inset = MarksZoomPlan.CAPSULE_INSET_DP * mDensity;
            mText.setColor(mInk);
            canvas.drawText(mTitle, mNameLeft + inset,
                cy - (mMetrics.ascent + mMetrics.descent) / 2f, mText);
        }
        int half = Math.round(layout.sizePx() * GLYPH_SHARE / 2f);
        for (int i = 0; i < layout.count() && i < mSet.length; i++) {
            Drawable glyph = mGlyphs[mSet[i].ordinal()];
            if (glyph == null) continue;
            int cx = Math.round(layout.leftPx(i, mBarWidth, mRtl) + layout.sizePx() / 2f);
            glyph.setBounds(cx - half, Math.round(cy) - half, cx + half, Math.round(cy) + half);
            glyph.draw(canvas);
        }
    }
}
