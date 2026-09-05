package com.termux.app.x11;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.terminal.PaneContentFrame;
import com.termux.app.terminal.PaneGlass;
import com.termux.app.terminal.PaneGlassBackdropView;
import com.termux.app.terminal.PaneRim;
import com.termux.app.terminal.PaneSurfaceStyle;
import com.termux.x11.LorieView;

/**
 * The wall's Display page: the embedded X server's surface, wearing a terminal pane's rim, radius
 * and gap.
 *
 * <p>No glass slab — an X screen is opaque, so there is nothing to see through it. The rounded
 * corners are painted rather than clipped: a {@code SurfaceView}'s surface is composited outside
 * the view hierarchy, so no parent outline reaches it, and there is no public
 * {@code SurfaceView} corner radius at compileSdk 36 (checked, not assumed). The mask paints what
 * sits behind the page over the four arcs instead, and the surface fills the frame flush to its
 * rim so a maximised X window meets the same corners the panes have.
 *
 * <p>While no server is running the page shows its empty state, which is where a home screen
 * rests: the launcher never starts a display on its own.
 */
public final class X11PaneFrame extends PaneContentFrame {

    /** What the page needs from the launcher. */
    public interface Host {
        /** Run the configured start command — the page's "Start display" button. */
        void startDisplay();
        /** Switch the Linux display on — the page's button while the setting is off. */
        default void turnOnDisplay() { }
        /** The page was long-pressed; show its menu at these screen coordinates. */
        void showDisplayMenu(float rawX, float rawY);
        /**
         * True when one of the launcher's own chords claimed this key, in which case X must not
         * see it. Everything else is the display's.
         */
        default boolean consumeLauncherKey(@NonNull android.view.KeyEvent event) { return false; }
    }

    private final PaneRim mRim = new PaneRim();
    @Nullable private LorieView mDisplay;
    @Nullable private PaneGlassBackdropView mCornerMask;
    @Nullable private View mEmptyState;
    @Nullable private Host mHost;
    @Nullable private PaneSurfaceStyle mStyle;
    private boolean mRunning;
    /** The Linux display setting. Off, the page is still a place — it just says so. */
    private boolean mEnabled = true;
    /** The display's size while the frame around it animates; unset when it follows the frame. */
    private int mFrozenWidth = -1, mFrozenHeight = -1;

    public X11PaneFrame(Context context) {
        super(context);
    }

    public X11PaneFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        // The display view reads its preferences while it is measured, host or no host; the
        // page is measured whether or not a display can run on it.
        com.termux.x11.LorieHost.primePrefs(getContext());
        mDisplay = findViewById(R.id.x11_display_view);
        mCornerMask = findViewById(R.id.x11_pane_corner_mask);
        mEmptyState = findViewById(R.id.x11_pane_empty);
        // The display is not registered as the pane's content on purpose: that clearance keeps a
        // terminal's text out of the arcs, but an X screen wants to fill the frame to its rim,
        // with the corner mask painting the arcs over it - not sit as a square inside a rounded
        // one.
        PaneGlass.followLayout(mCornerMask);
        View start = findViewById(R.id.x11_pane_start);
        if (start != null) start.setOnClickListener(v -> {
            if (mHost == null) return;
            if (mEnabled) mHost.startDisplay();
            else mHost.turnOnDisplay();
        });
        setOnLongClickListener(v -> {
            if (mHost == null) return false;
            mHost.showDisplayMenu(getX() + getWidth() / 2f, getY() + getHeight() / 2f);
            return true;
        });
        applyRunning(false);
    }

    public void setHost(@Nullable Host host) {
        mHost = host;
    }

    @NonNull
    public LorieView display() {
        if (mDisplay == null) throw new IllegalStateException("Display page is not inflated");
        return mDisplay;
    }

    /**
     * Whether the Linux display is switched on. The page is a place on the wall either way — the
     * wall always has its three places — but off, it offers to turn the display on instead of to
     * start one.
     */
    public void applyEnabled(boolean enabled) {
        mEnabled = enabled;
        if (!mRunning) applyRunning(false);
    }

    /**
     * Show the surface or the empty state. A server that exits leaves the page empty; it never
     * leaves a dead screen on the home screen.
     */
    public void applyRunning(boolean running) {
        mRunning = running;
        if (mDisplay != null) mDisplay.setVisibility(running ? VISIBLE : INVISIBLE);
        if (mCornerMask != null) mCornerMask.setVisibility(running ? VISIBLE : GONE);
        if (mEmptyState != null) mEmptyState.setVisibility(running ? GONE : VISIBLE);
        if (running) return;
        // A server cannot start at all without the keyboard layouts, so say that here rather
        // than letting the user find an Xorg error in their shell.
        boolean ready = X11CliInstaller.hasKeyboardData();
        View message = findViewById(R.id.x11_pane_empty_message);
        if (message instanceof android.widget.TextView) {
            ((android.widget.TextView) message).setText(!mEnabled ? R.string.termux_x11_display_off
                : ready ? R.string.termux_x11_no_display : R.string.termux_x11_needs_keyboard_data);
        }
        View start = findViewById(R.id.x11_pane_start);
        if (start instanceof android.widget.TextView) {
            ((android.widget.TextView) start).setText(mEnabled
                ? R.string.termux_x11_start_display : R.string.termux_x11_turn_on);
        }
        if (start != null) start.setVisibility(mEnabled && !ready ? GONE : VISIBLE);
    }

    public boolean isRunning() {
        return mRunning;
    }

    /**
     * The frame is about to change height over several frames (the status bar expanding or
     * folding). The display keeps the size it has, pinned to the page's bottom edge, so the X
     * screen is resized once at the end instead of on every frame; until then the bar simply
     * covers it.
     */
    public void beginHostResize() {
        if (mDisplay == null || mDisplay.getWidth() <= 0 || mDisplay.getHeight() <= 0) return;
        if (mFrozenWidth < 0) {
            mFrozenWidth = mDisplay.getWidth();
            mFrozenHeight = mDisplay.getHeight();
        }
    }

    /** The frame has its final height: the display takes it, in one resize. */
    public void finishHostResize() {
        if (mFrozenWidth < 0) return;
        mFrozenWidth = -1;
        mFrozenHeight = -1;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (mFrozenWidth > 0 && mDisplay != null) {
            mDisplay.measure(MeasureSpec.makeMeasureSpec(mFrozenWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(mFrozenHeight, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mFrozenWidth > 0 && mDisplay != null) {
            // Where the frame put it, but at its frozen size and hanging from the bottom edge:
            // the picture stays exactly where it was on screen while the bar moves over it.
            int l = mDisplay.getLeft();
            int b = mDisplay.getBottom();
            mDisplay.layout(l, b - mFrozenHeight, l + mFrozenWidth, b);
        }
    }

    /**
     * Dress the page from the surface style — the same values the panes beside it read, so the
     * Display page follows the Canvas surface with the rest of the wall.
     */
    public void applyStyle(@Nullable PaneSurfaceStyle style) {
        mStyle = style;
        boolean glass = PaneGlass.isActive(style);
        float radiusPx = glass
            ? PaneGlass.radiusPx(style, getResources().getDisplayMetrics().density) : 0f;
        // The frame must not clip to its shape here: the mask's arcs lie exactly outside the
        // rounded outline, so a clipping frame cut away the very paint that rounds the surface,
        // which no clip reaches. The page has no slab of its own to keep square, and the rim
        // draws the rounded edge whichever way the frame is set.
        setPaneShape(radiusPx, false);
        if (mCornerMask != null) {
            mCornerMask.setCornerMaskRadius(radiusPx);
            // Behind the page is the wallpaper (or the surface base colour), never a pane's own
            // frost, so the mask is fed the frame and nothing else.
            if (style != null) {
                // The panes' frost when there is one; otherwise the wallpaper itself, unblurred,
                // which is what the wall shows between its panes; otherwise the flat colour. The
                // arcs are never left open, or the surface would show square through them.
                android.graphics.Bitmap frame = style.paneGlassBlurFrame();
                boolean frosted = frame != null;
                if (frame == null) frame = style.wallBehindFrame();
                mCornerMask.setGlass(frame, style.paneGlassBlurFrameRect(),
                    android.graphics.Color.TRANSPARENT, null, radiusPx,
                    frosted ? style.paneGlassFrostFilter() : null);
                mCornerMask.setCornerMaskFallbackColor(style.wallBehindColor());
            }
            mCornerMask.invalidateGlassPosition();
        }
        if (glass) mRim.apply(this, true, radiusPx, true);
        else mRim.clear(this);
    }

    /**
     * Every key aimed at the page passes through here on its way to the display's own view, so
     * this is where the launcher gets first refusal. It takes only its own chords; the rest of
     * the keyboard belongs to whatever is running on the display.
     */
    @Override
    public boolean dispatchKeyEvent(@NonNull android.view.KeyEvent event) {
        if (mHost != null && mHost.consumeLauncherKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mRim.cancel();
    }
}
