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
 * sits behind the page over the four arcs instead.
 *
 * <p>While no server is running the page shows its empty state, which is where a home screen
 * rests: the launcher never starts a display on its own.
 */
public final class X11PaneFrame extends PaneContentFrame {

    /** What the page needs from the launcher. */
    public interface Host {
        /** Run the configured start command — the page's "Start display" button. */
        void startDisplay();
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

    public X11PaneFrame(Context context) {
        super(context);
    }

    public X11PaneFrame(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mDisplay = findViewById(R.id.x11_display_view);
        mCornerMask = findViewById(R.id.x11_pane_corner_mask);
        mEmptyState = findViewById(R.id.x11_pane_empty);
        setPaneContent(mDisplay);
        PaneGlass.followLayout(mCornerMask);
        View start = findViewById(R.id.x11_pane_start);
        if (start != null) start.setOnClickListener(v -> {
            if (mHost != null) mHost.startDisplay();
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
            ((android.widget.TextView) message).setText(ready
                ? R.string.termux_x11_no_display : R.string.termux_x11_needs_keyboard_data);
        }
        View start = findViewById(R.id.x11_pane_start);
        if (start != null) start.setVisibility(ready ? VISIBLE : GONE);
    }

    public boolean isRunning() {
        return mRunning;
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
        // The frame's own outline still rounds the empty state and takes the content inset; the
        // surface underneath needs the mask on top of it either way.
        setPaneShape(radiusPx, glass);
        if (mCornerMask != null) {
            mCornerMask.setCornerMaskRadius(radiusPx);
            // Behind the page is the wallpaper (or the surface base colour), never a pane's own
            // frost, so the mask is fed the frame and nothing else.
            if (style != null) {
                mCornerMask.setGlass(style.paneGlassBlurFrame(), style.paneGlassBlurFrameRect(),
                    android.graphics.Color.TRANSPARENT, null, radiusPx,
                    style.paneGlassFrostFilter());
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
