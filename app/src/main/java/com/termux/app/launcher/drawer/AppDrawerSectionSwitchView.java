package com.termux.app.launcher.drawer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.termux.R;
import com.termux.app.surfaces.LegendSlider;

/**
 * The Default drawer's section switch: a glass capsule holding the labels "Apps" and "Categories",
 * with a smaller capsule over the selected one. One plain view; it draws its body, the selected
 * capsule and both labels itself.
 *
 * <p>The selected capsule has no animator. The view is a {@link AppDrawerSectionPager.Listener}:
 * each position change stores the number and invalidates this view, so the capsule and the label
 * inks follow the pages exactly, and at rest nothing invalidates. Text is measured when the
 * labels, the width cap or the font scale change, never while drawing.
 *
 * <p>The body is the drawer's small-glass dress, as the search bar wears it: a wash of the chrome's
 * ink and its hairline over the card's glass, the ink the A-Z rope reads (the settled glass ink,
 * else the launcher's text colour), so it follows the launcher's scheme. A tap on a label asks
 * {@link Callbacks#onSectionChosen}; a tap is a down and up within the touch slop, so a drag
 * starting here does nothing.
 *
 * <p>Accessibility: one node, described as the selected label followed by the other
 * ("Apps, selected, Categories"), a tab-strip class, and a click that chooses the other section.
 */
public final class AppDrawerSectionSwitchView extends View implements AppDrawerSectionPager.Listener {

    /** Told which label a tap landed on; the drawer decides what that does. */
    public interface Callbacks {
        void onSectionChosen(@NonNull AppDrawerSection section);
    }

    /** The ink the switch is dressed in; read at draw time, two int reads. */
    public interface InkSource {
        int ink();
    }

    /** The body's wash and hairline, as the search bar's: a veil of the ink, not a second surface. */
    private static final int BODY_FILL_ALPHA = 15;
    private static final int BODY_STROKE_ALPHA = 33;
    /** The selected capsule: a stronger wash of the same ink. */
    private static final int SELECTED_FILL_ALPHA = 56;
    /** The label that is not selected: the ink at 65%. */
    private static final int OTHER_LABEL_ALPHA = 0xA6;
    private static final int OPAQUE = 255;

    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint mText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private final AppDrawerSectionSwitchGeometry.Layout mLayout =
        new AppDrawerSectionSwitchGeometry.Layout();
    private final String[] mLabels = new String[2];
    private final String[] mShown = new String[] {"", ""};
    private final float[] mLabelMeasured = new float[2];
    private final float mDensity;
    private final int mTouchSlopPx;

    @Nullable private Callbacks mCallbacks;
    @Nullable private InkSource mInkSource;
    @Nullable private AppDrawerSectionPager mPager;
    private float mPosition;
    private float mScaledDensity;
    private int mMaxWidthPx;
    private boolean mLaidOut;
    private int mLaidOutCap = -1;
    private boolean mLaidOutRtl;
    private float mBaseline;
    @NonNull private AppDrawerSection mSelected = AppDrawerSection.APPS;
    private int mDownIndex = -1;
    private float mDownX;
    private float mDownY;

    public AppDrawerSectionSwitchView(@NonNull Context context) {
        super(context);
        mDensity = context.getResources().getDisplayMetrics().density;
        mTouchSlopPx = ViewConfiguration.get(context).getScaledTouchSlop();
        setFocusable(false);
        setClickable(true);
        setWillNotDraw(false);
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeWidth(Math.max(1f, mDensity));
        mText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mLabels[0] = context.getString(R.string.settings_gui_apps_step_apps);
        mLabels[1] = context.getString(R.string.settings_app_drawer_view_type_categories);
        measureLabels();
        updateDescription();
    }

    public void setCallbacks(@Nullable Callbacks callbacks) {
        mCallbacks = callbacks;
    }

    public void setInkSource(@Nullable InkSource source) {
        mInkSource = source;
        invalidate();
    }

    /** Follows {@code pager}'s position and settled section from now on. */
    public void attachTo(@NonNull AppDrawerSectionPager pager) {
        if (mPager != null) mPager.removeListener(this);
        mPager = pager;
        pager.addListener(this);
        mPosition = pager.position();
        mSelected = pager.settledSection();
        updateDescription();
        invalidate();
    }

    /** The widest the switch may be, from the chrome layout; zero is no cap beyond the parent's. */
    public void setMaxWidthPx(int maxWidthPx) {
        int cap = Math.max(0, maxWidthPx);
        if (mMaxWidthPx == cap) return;
        mMaxWidthPx = cap;
        requestLayout();
    }

    /**
     * Interactive while the pages are shown; otherwise the switch stays visible, dimmed to the
     * launcher's disabled alpha, and taps do nothing.
     */
    public void setPagesActive(boolean active) {
        float alpha = active ? 1f : LegendSlider.DISABLED_ALPHA / (float) OPAQUE;
        if (isEnabled() != active) setEnabled(active);
        if (getAlpha() != alpha) setAlpha(alpha);
    }

    @Override
    public void onPositionChanged(float position) {
        if (mPosition == position) return;
        mPosition = position;
        invalidate();
    }

    @Override
    public void onSettled(@NonNull AppDrawerSection section, boolean changed) {
        if (mSelected == section) return;
        mSelected = section;
        updateDescription();
    }

    // ----------------------------------------------------------------------------- measuring

    /** Re-measures the labels; runs on construction and when the font scale changes. */
    private void measureLabels() {
        mScaledDensity = getResources().getDisplayMetrics().scaledDensity;
        mText.setTextSize(AppDrawerSectionSwitchGeometry.TEXT_SP * mScaledDensity);
        for (int i = 0; i < 2; i++) mLabelMeasured[i] = mText.measureText(mLabels[i]);
        Paint.FontMetrics metrics = mText.getFontMetrics();
        mBaseline = AppDrawerSectionSwitchGeometry.HEIGHT_DP * mDensity * 0.5f
            - (metrics.ascent + metrics.descent) * 0.5f;
        mLaidOut = false;
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        float scaled = getResources().getDisplayMetrics().scaledDensity;
        if (scaled == mScaledDensity) return;
        measureLabels();
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int cap = mMaxWidthPx;
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            int parent = MeasureSpec.getSize(widthMeasureSpec);
            cap = cap > 0 ? Math.min(cap, parent) : parent;
        }
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        if (!mLaidOut || mLaidOutCap != cap || mLaidOutRtl != rtl) {
            solve(cap, rtl);
        }
        setMeasuredDimension(Math.round(mLayout.width), Math.round(mLayout.height));
    }

    /** The geometry and the labels as drawn (ellipsized last, only when the cap shortened them). */
    private void solve(int cap, boolean rtl) {
        AppDrawerSectionSwitchGeometry.compute(mLayout, mLabelMeasured[0], mLabelMeasured[1],
            mDensity, cap, rtl);
        mText.setTextSize(AppDrawerSectionSwitchGeometry.TEXT_SP * mScaledDensity);
        for (int i = 0; i < 2; i++) {
            mShown[i] = mLayout.labelWidth[i] >= mLabelMeasured[i] ? mLabels[i]
                : TextUtils.ellipsize(mLabels[i], mText, mLayout.labelWidth[i],
                    TextUtils.TruncateAt.END).toString();
        }
        mLaidOut = true;
        mLaidOutCap = cap;
        mLaidOutRtl = rtl;
    }

    // -------------------------------------------------------------------------------- drawing

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (!mLaidOut) return;
        AppDrawerSectionSwitchGeometry.Layout layout = mLayout;
        int ink = mInkSource != null ? mInkSource.ink() : Color.WHITE;
        float inset = mStroke.getStrokeWidth() * 0.5f;
        float radius = layout.height * 0.5f;
        mRect.set(inset, inset, layout.width - inset, layout.height - inset);
        mFill.setColor(withAlpha(ink, BODY_FILL_ALPHA));
        canvas.drawRoundRect(mRect, radius, radius, mFill);
        mStroke.setColor(withAlpha(ink, BODY_STROKE_ALPHA));
        canvas.drawRoundRect(mRect, radius, radius, mStroke);

        mRect.set(AppDrawerSectionSwitchGeometry.capsuleLeft(layout, mPosition),
            layout.capsuleTop, AppDrawerSectionSwitchGeometry.capsuleRight(layout, mPosition),
            layout.capsuleBottom);
        float capsuleRadius = (layout.capsuleBottom - layout.capsuleTop) * 0.5f;
        mFill.setColor(withAlpha(ink, SELECTED_FILL_ALPHA));
        canvas.drawRoundRect(mRect, capsuleRadius, capsuleRadius, mFill);

        mText.setTextSize(AppDrawerSectionSwitchGeometry.TEXT_SP * mScaledDensity);
        for (int i = 0; i < 2; i++) {
            float weight = AppDrawerSectionSwitchGeometry.labelWeight(i, mPosition);
            int alpha = Math.round(OTHER_LABEL_ALPHA + (OPAQUE - OTHER_LABEL_ALPHA) * weight);
            mText.setColor(withAlpha(ink, alpha));
            float x = layout.segmentLeft[i] + layout.padding;
            canvas.drawText(mShown[i], x, mBaseline, mText);
        }
    }

    /** {@code ink} at {@code alpha} of its own alpha. */
    private static int withAlpha(int ink, int alpha) {
        return ColorUtils.setAlphaComponent(ink, Math.round(Color.alpha(ink) * alpha / (float) OPAQUE));
    }

    // ----------------------------------------------------------------------------------- touch

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mDownX = event.getX();
                mDownY = event.getY();
                mDownIndex = isEnabled() && mLaidOut
                    ? AppDrawerSectionSwitchGeometry.hit(mLayout, mDownX, mDownY) : -1;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mDownIndex >= 0 && (Math.abs(event.getX() - mDownX) > mTouchSlopPx
                    || Math.abs(event.getY() - mDownY) > mTouchSlopPx)) mDownIndex = -1;
                return true;
            case MotionEvent.ACTION_UP: {
                int index = mDownIndex;
                mDownIndex = -1;
                if (index >= 0 && isEnabled() && mLaidOut
                    && AppDrawerSectionSwitchGeometry.hit(mLayout, event.getX(), event.getY())
                        == index) {
                    choose(AppDrawerSection.values()[index]);
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                mDownIndex = -1;
                return true;
            default:
                return true;
        }
    }

    private void choose(@NonNull AppDrawerSection section) {
        if (section == mSelected && mPager != null && mPager.isAtRest()) return;
        Callbacks callbacks = mCallbacks;
        if (callbacks != null) callbacks.onSectionChosen(section);
    }

    // ------------------------------------------------------------------------- accessibility

    private void updateDescription() {
        AppDrawerSection other = mSelected == AppDrawerSection.APPS
            ? AppDrawerSection.CATEGORIES : AppDrawerSection.APPS;
        String selected = getContext().getString(R.string.app_drawer_section_switch_selected,
            mLabels[mSelected.index()]);
        setContentDescription(selected + ", " + mLabels[other.index()]);
    }

    @Override
    public boolean performClick() {
        // An accessibility click has no point: it chooses the section that is not selected.
        if (!isEnabled()) return false;
        super.performClick();
        choose(mSelected == AppDrawerSection.APPS
            ? AppDrawerSection.CATEGORIES : AppDrawerSection.APPS);
        return true;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(android.widget.TabWidget.class.getName());
        info.setClickable(isEnabled());
    }
}
