package com.termux.app.terminal;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.app.chrome.ChromeInk;
import com.termux.app.chrome.ChromeShade;

/**
 * The titlebar of a float: one glass strip along its top carrying the drag handle and the buttons
 * {@link FloatTitlebarPolicy#buttons} lists for its host.
 *
 * <p>It is cut from the pane's own material. Glass, it is a {@link PaneGlassBackdropView} fed by
 * {@link PaneGlass#apply} from the same style as the pane under it, so it samples the shared frame
 * at its live position like every slab (ADR 0004) and holds no pixels of its own; plain, it is the
 * pane's panel colour. Its corners are the pane's radius capped against the strip by
 * {@link PaneShape#radiusForBounds}, the rule every slab follows, and it wears the rim its pane
 * wears ({@link #applyRim}), so a focused float's strip carries the active rim with it.
 *
 * <p>The strip's own touch is the drag handle, which the float's container owns: a drag moves the
 * float and a long press opens the handle's menu. This view only answers {@link #actionAt} so the
 * container knows when a press belongs to a button instead.
 */
public final class FloatTitlebarView extends FrameLayout {

    /** What the float does when a titlebar button is tapped, or its handle's menu is asked for. */
    public interface Listener {
        void onTitlebarAction(int action);

        /**
         * The handle's menu was asked for without a touch: TalkBack's long press on the strip. A
         * finger's long press on the handle is the float's container's, which owns the strip's touch.
         */
        default void onHandleMenu() {}
    }

    private final PaneGlassBackdropView mGlass;
    private final int[] mActions;
    private final ImageButton[] mButtons;
    private final PaneRim mRim = new PaneRim();
    private final Paint mGripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mGripRect = new RectF();
    @Nullable private PaneSurfaceStyle mStyle;
    private float mRequestedRadiusPx;
    private float mOutlineRadiusPx;
    private boolean mMaximised;
    private boolean mFocused;
    private int mInk;
    /** The chrome's polarity {@link #mInk} was resolved on; a wallpaper change can move it. */
    @Nullable private ChromeInk.Polarity mInkPolarity;

    public FloatTitlebarView(@NonNull Context context, boolean hasMinimiseTarget,
                             @NonNull Listener listener) {
        super(context);
        setWillNotDraw(false);
        mGlass = new PaneGlassBackdropView(context);
        mGlass.setVisibility(GONE);
        addView(mGlass, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        PaneGlass.followLayout(mGlass);
        mActions = FloatTitlebarPolicy.buttons(hasMinimiseTarget);
        mButtons = new ImageButton[mActions.length];
        TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(
            android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        for (int i = 0; i < mActions.length; i++) {
            final int action = mActions[i];
            ImageButton button = new ImageButton(context);
            button.setScaleType(ImageButton.ScaleType.CENTER);
            if (ripple.resourceId != 0) button.setBackgroundResource(ripple.resourceId);
            else button.setBackground(null);
            button.setOnClickListener(v -> listener.onTitlebarAction(action));
            mButtons[i] = button;
            addView(button, new LayoutParams(0, 0));
        }
        // TalkBack reaches the handle as the strip itself: "Move window", long press for its menu.
        setContentDescription(context.getString(R.string.titlebar_handle_description));
        setOnLongClickListener(v -> {
            listener.onHandleMenu();
            return true;
        });
        ViewCompat.replaceAccessibilityAction(this,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
            context.getString(R.string.titlebar_handle_menu_action), null);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mOutlineRadiusPx);
            }
        });
        bindButtons();
    }

    /** The actions this strip offers, leading to trailing. */
    @NonNull
    public int[] actions() {
        return mActions.clone();
    }

    /** The button for {@code action}, or null when this host does not offer it. */
    @Nullable
    public ImageButton buttonFor(int action) {
        for (int i = 0; i < mActions.length; i++) if (mActions[i] == action) return mButtons[i];
        return null;
    }

    /** The button action under {@code (x, y)} in this view's coordinates, or ACTION_NONE. */
    public int actionAt(float x, float y) {
        if (y < 0f || y >= getHeight()) return FloatTitlebarPolicy.ACTION_NONE;
        for (int i = 0; i < mButtons.length; i++) {
            ImageButton b = mButtons[i];
            if (b.getVisibility() == VISIBLE && x >= b.getLeft() && x < b.getRight())
                return mActions[i];
        }
        return FloatTitlebarPolicy.ACTION_NONE;
    }

    /** Maximise reads as Restore while the float fills its host. */
    public void setMaximised(boolean maximised) {
        if (mMaximised == maximised) return;
        mMaximised = maximised;
        bindButtons();
    }

    /**
     * Dress the strip in the pane's material: glass from {@code style} while the pane is glass,
     * else the pane's panel colour. Cheap to repeat; runs wherever the pane's slab is re-fed.
     */
    public void dress(@Nullable PaneSurfaceStyle style, float requestedRadiusPx) {
        mStyle = style;
        mRequestedRadiusPx = requestedRadiusPx;
        boolean glass = PaneGlass.apply(style, this, mGlass, requestedRadiusPx, false);
        float outline = PaneShape.radiusForBounds(requestedRadiusPx, getWidth(), getHeight());
        if (outline != mOutlineRadiusPx) {
            mOutlineRadiusPx = outline;
            invalidateOutline();
        }
        if (glass) {
            if (getBackground() != null) setBackground(null);
        } else {
            GradientDrawable fill = getBackground() instanceof GradientDrawable
                ? (GradientDrawable) getBackground() : new GradientDrawable();
            fill.setColor(panelColour());
            fill.setCornerRadius(outline);
            if (getBackground() != fill) setBackground(fill);
        }
        updateInk(glass);
    }

    /** Re-aim the glass at the wallpaper: the float moved, or the page it sits on slid. */
    public void invalidateGlassPosition() {
        if (mGlass.getVisibility() == VISIBLE) mGlass.invalidateGlassPosition();
    }

    /** Wear the border the pane under this strip wears, decided once for both. */
    public void applyRim(boolean glass, float radiusPx, @NonNull PaneBorderStyle.Decision decision,
                         @Nullable PaneSurfaceStyle style, float travelAlpha, boolean focused) {
        if (mRim.apply(this, glass, radiusPx, decision, style)) mRim.setTravelAlpha(travelAlpha);
        if (mFocused != focused) {
            mFocused = focused;
            updateInk(mGlass.getVisibility() == VISIBLE);
        }
    }

    /** The wall's slide fades the strip's rim with its pane's. */
    public void setRimTravelAlpha(float alpha) {
        mRim.setTravelAlpha(alpha);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // The slab's corner is capped against its own size, which is only known now.
        dress(mStyle, mRequestedRadiusPx);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        int slot = FloatTitlebarPolicy.buttonSlotPx(width, mButtons.length,
            dp(FloatTitlebarPolicy.BUTTON_DP));
        int exactHeight = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY);
        mGlass.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), exactHeight);
        for (ImageButton button : mButtons)
            button.measure(MeasureSpec.makeMeasureSpec(slot, MeasureSpec.EXACTLY), exactHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        mGlass.layout(0, 0, width, height);
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        // Trailing-aligned, Close outermost: walk the buttons from the last one inward.
        int edge = rtl ? 0 : width;
        for (int i = mButtons.length - 1; i >= 0; i--) {
            ImageButton button = mButtons[i];
            int slot = button.getMeasuredWidth();
            if (rtl) {
                button.layout(edge, 0, edge + slot, height);
                edge += slot;
            } else {
                button.layout(edge - slot, 0, edge, height);
                edge -= slot;
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // The chrome re-votes its polarity on a wallpaper, palette or mode change without telling
        // the floats; the strip follows it on the next frame, as the dock's rim does.
        if (ChromeShade.polarity() != mInkPolarity) updateInk(mGlass.getVisibility() == VISIBLE);
        super.dispatchDraw(canvas);
        // The handle: a grip bar centred in the span the buttons leave, when it fits there.
        int buttons = 0;
        for (ImageButton button : mButtons) buttons += button.getWidth();
        float free = getWidth() - buttons;
        float grip = dp(FloatTitlebarPolicy.GRIP_WIDTH_DP);
        if (free < grip + 2 * dp(8)) return;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float centerX = rtl ? buttons + free / 2f : free / 2f;
        float centerY = getHeight() / 2f;
        float half = dp(1.75f);
        mGripRect.set(centerX - grip / 2f, centerY - half, centerX + grip / 2f, centerY + half);
        mGripPaint.setColor(mInk);
        mGripPaint.setAlpha(Math.round(android.graphics.Color.alpha(mInk) * 0.6f));
        canvas.drawRoundRect(mGripRect, half, half, mGripPaint);
    }

    private void bindButtons() {
        for (int i = 0; i < mActions.length; i++) {
            ImageButton button = mButtons[i];
            switch (mActions[i]) {
                case FloatTitlebarPolicy.ACTION_MINIMISE:
                    button.setImageResource(R.drawable.ic_titlebar_minimise);
                    button.setContentDescription(getContext().getString(
                        R.string.titlebar_minimise_description));
                    break;
                case FloatTitlebarPolicy.ACTION_MAXIMISE:
                    button.setImageResource(mMaximised
                        ? R.drawable.ic_titlebar_restore : R.drawable.ic_titlebar_maximise);
                    button.setContentDescription(getContext().getString(mMaximised
                        ? R.string.titlebar_restore_description
                        : R.string.titlebar_maximise_description));
                    break;
                default:
                    button.setImageResource(R.drawable.ic_titlebar_close);
                    button.setContentDescription(getContext().getString(
                        R.string.titlebar_close_description));
                    break;
            }
        }
    }

    /**
     * The glyphs' colour, {@link FloatTitlebarPolicy#glyphInk}: on glass the window bar's label ink
     * on the chrome's polarity, on the panel the on-surface ink made legible on it.
     */
    private void updateInk(boolean glass) {
        mInkPolarity = ChromeShade.polarity();
        int surface = glass && mStyle != null ? mStyle.paneGlassTintColor() : panelColour();
        int onSurface = MaterialColors.getColor(getContext(),
            com.termux.shared.R.attr.termuxColorOnSurface,
            ContextCompat.getColor(getContext(), R.color.termux_on_surface));
        int surfaceBase = MaterialColors.getColor(getContext(),
            com.termux.shared.R.attr.termuxColorSurfaceBase,
            ContextCompat.getColor(getContext(), R.color.termux_surface_base));
        int ink = FloatTitlebarPolicy.glyphInk(glass, mInkPolarity, onSurface, surfaceBase,
            surface, mFocused);
        if (ink == mInk) return;
        mInk = ink;
        ColorStateList tint = ColorStateList.valueOf(ink);
        for (ImageButton button : mButtons) button.setImageTintList(tint);
        invalidate();
    }

    private int panelColour() {
        return MaterialColors.getColor(getContext(),
            com.termux.shared.R.attr.termuxColorSurfacePanel,
            ContextCompat.getColor(getContext(), R.color.termux_surface_panel));
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private float dp(float value) {
        return getResources().getDisplayMetrics().density * value;
    }

    /** The strip never scrolls, so a button shows its press at once. */
    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }
}
