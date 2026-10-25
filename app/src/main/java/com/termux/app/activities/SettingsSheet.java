package com.termux.app.activities;

import android.annotation.SuppressLint;
import android.graphics.Outline;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.termux.R;

/**
 * Settings' window: a sheet over the live Home ({@code activity_settings_sheet}). It opens resting
 * at about 60% of the window, rises to full height (below the status bar) when dragged up or when
 * the keyboard comes up over it, and keeps that height until it is closed; the heights are
 * {@link SettingsSheetPolicy}'s. It closes when its header is dragged down past the closing
 * threshold, when the Home above it is tapped, or when {@link #close} is called (Back at the root).
 *
 * <p>The sheet is dragged by its header only, the handle strip and the toolbar under it: a page's
 * own lists, scroll views and sliders keep every touch that lands on them, so no page has to know
 * it stands in a sheet.
 *
 * <p>The window draws edge to edge and this class takes every inset: the sheet clears the status
 * bar at full height, its content clears the navigation bar, and it stands on the keyboard. The
 * pages below see no insets, as when the window fitted the system bars itself.
 */
final class SettingsSheet {

    private static final String STATE_RAISED = "settings_sheet_raised";

    /**
     * How long a close may take before the window goes anyway: a hide that never settles must not
     * leave an invisible Settings window standing over Home.
     */
    private static final long CLOSE_BACKSTOP_MS = 700;

    private final View mRoot;
    private final View mCoordinator;
    private final View mSheet;
    private final BottomSheetBehavior<View> mBehavior;
    private final boolean mTwoPane;
    private final boolean mInstant;
    private final Runnable mOnClosed;
    private final Runnable mCloseBackstop = this::finishClose;
    private final int[] mLocation = new int[2];

    /** The toolbar: drags that start on it move the sheet, as the handle's do. */
    @Nullable private View mHeader;

    /** Raised to full height, for good: the resting stop is gone until the sheet closes. */
    private boolean mRaised;
    /** Seen resting or raised at least once, so a hidden sheet means the user closed it. */
    private boolean mShown;
    private boolean mClosing;
    private boolean mClosed;
    private int mTopInset;

    SettingsSheet(@NonNull View root, boolean twoPane, boolean instant, @Nullable Bundle savedState,
                  @NonNull Runnable onClosed) {
        mRoot = root;
        mCoordinator = root.findViewById(R.id.settings_sheet_coordinator);
        mSheet = root.findViewById(R.id.settings_sheet);
        mTwoPane = twoPane;
        mInstant = instant;
        mOnClosed = onClosed;
        mRaised = savedState != null && savedState.getBoolean(STATE_RAISED, false);
        mBehavior = BottomSheetBehavior.from(mSheet);
        mBehavior.setHideable(true);
        mBehavior.setSkipCollapsed(false);
        mBehavior.setFitToContents(true);
        mBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View sheet, int state) {
                onSheetStateChanged(state);
            }

            @Override
            public void onSlide(@NonNull View sheet, float slideOffset) {}
        });

        // Only the top corners are round; the outline runs a radius past the bottom so a plain
        // round rect (which every API level clips to) leaves the bottom square.
        int radius = root.getResources().getDimensionPixelSize(R.dimen.settings_sheet_corner_radius);
        mSheet.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + radius, radius);
            }
        });
        mSheet.setClipToOutline(true);
        // Touches the page does not take stay in the sheet, never falling through to the Home
        // area behind it, whose tap closes Settings.
        consumeTouches(mSheet);

        View scrim = root.findViewById(R.id.settings_sheet_scrim);
        scrim.setOnClickListener(v -> close());
        View handle = root.findViewById(R.id.settings_sheet_handle);
        handle.setOnClickListener(v -> raise());

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout());
            int ime = insets.isVisible(WindowInsetsCompat.Type.ime())
                ? insets.getInsets(WindowInsetsCompat.Type.ime()).bottom : 0;
            applyInsets(bars, ime);
            return WindowInsetsCompat.CONSUMED;
        });
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) v.post(this::updateGeometry);
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private static void consumeTouches(@NonNull View sheet) {
        sheet.setOnTouchListener((v, event) -> true);
    }

    /** Sets the toolbar as part of the header the sheet is dragged by. */
    void setHeader(@Nullable View header) {
        mHeader = header;
    }

    /**
     * Shows the sheet. A fresh open rises from the bottom edge to rest; a window recreated with
     * its state (a rotation) keeps where the sheet stood, and one recreated with the sheet put
     * away (mid-open or mid-close) brings it back rather than leaving an empty window over Home.
     */
    void open(boolean fresh) {
        if (!fresh) {
            mShown = true;
            mRoot.post(() -> {
                if (!mClosing && !mClosed && mBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN)
                    settle();
            });
            return;
        }
        if (mInstant) {
            // No rise: the sheet waits unseen for the first layout, which gives the window's size
            // and with it the resting height, then appears there.
            mSheet.setVisibility(View.INVISIBLE);
            mBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            mRoot.post(() -> {
                updateGeometry();
                mSheet.setVisibility(View.VISIBLE);
            });
            return;
        }
        mBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        // Runs after the first layout, from where the hidden sheet can rise.
        mRoot.post(() -> {
            if (!mClosing && !mClosed) settle();
        });
    }

    /** Called when Settings is asked to open again while it stands (a second entry point). */
    void reopen() {
        if (mClosed) return;
        boolean wasClosing = mClosing;
        if (wasClosing) {
            mClosing = false;
            mRoot.removeCallbacks(mCloseBackstop);
        }
        // Turned back mid-slide, or brought up from hidden.
        if (wasClosing || mBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) settle();
    }

    /** Puts the sheet away, then the window; Back at the root and a tap on Home above it. */
    void close() {
        if (mClosing || mClosed) return;
        mClosing = true;
        if (mInstant || !mSheet.isLaidOut() || mBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
            finishClose();
            return;
        }
        mBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        mRoot.postDelayed(mCloseBackstop, CLOSE_BACKSTOP_MS);
    }

    boolean isClosing() {
        return mClosing || mClosed;
    }

    void saveState(@NonNull Bundle outState) {
        outState.putBoolean(STATE_RAISED, mRaised);
    }

    /**
     * A finger came down at ({@code x}, {@code y}) in the window: the sheet follows the drag that
     * starts there only when it is on the header.
     */
    void onTouchDown(float x, float y) {
        mBehavior.setDraggable(isOnHeader(x, y));
    }

    @VisibleForTesting
    boolean isRaised() {
        return mRaised;
    }

    @VisibleForTesting
    @NonNull
    BottomSheetBehavior<View> behavior() {
        return mBehavior;
    }

    private boolean isOnHeader(float x, float y) {
        mSheet.getLocationInWindow(mLocation);
        float left = mLocation[0];
        float top = mLocation[1];
        if (x < left || x >= left + mSheet.getWidth() || y < top) return false;
        View header = mHeader != null && mHeader.isShown() ? mHeader : null;
        if (header == null) {
            View handle = mSheet.findViewById(R.id.settings_sheet_handle);
            return y < top + handle.getHeight();
        }
        header.getLocationInWindow(mLocation);
        return y < mLocation[1] + header.getHeight();
    }

    /** Settles where the sheet belongs when open: resting, or full height once raised. */
    private void settle() {
        updateGeometry();
        mBehavior.setState(mRaised ? BottomSheetBehavior.STATE_EXPANDED : BottomSheetBehavior.STATE_COLLAPSED);
    }

    /** To full height, kept there until the sheet closes (the expanded state latches it). */
    private void raise() {
        if (mClosing || mClosed) return;
        int state = mBehavior.getState();
        if (state != BottomSheetBehavior.STATE_EXPANDED && state != BottomSheetBehavior.STATE_HIDDEN)
            mBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void onSheetStateChanged(int state) {
        switch (state) {
            case BottomSheetBehavior.STATE_EXPANDED:
                mShown = true;
                if (!mRaised) {
                    mRaised = true;
                    updateGeometry();
                }
                break;
            case BottomSheetBehavior.STATE_COLLAPSED:
                mShown = true;
                break;
            case BottomSheetBehavior.STATE_HIDDEN:
                // Dragged away, or put away by close().
                if (mShown || mClosing) finishClose();
                break;
            default:
                break;
        }
    }

    private void finishClose() {
        if (mClosed) return;
        mClosed = true;
        mClosing = false;
        mRoot.removeCallbacks(mCloseBackstop);
        mOnClosed.run();
    }

    private void applyInsets(@NonNull Insets bars, int ime) {
        if (mRoot.getPaddingLeft() != bars.left || mRoot.getPaddingRight() != bars.right)
            mRoot.setPadding(bars.left, 0, bars.right, 0);
        // The sheet stands on the keyboard; its colour runs under the navigation bar, its content
        // stops above it.
        setMargins(mCoordinator, 0, ime);
        setMargins(mSheet, bars.top, 0);
        int bottomPadding = Math.max(0, bars.bottom - ime);
        if (mSheet.getPaddingBottom() != bottomPadding)
            mSheet.setPadding(mSheet.getPaddingLeft(), mSheet.getPaddingTop(), mSheet.getPaddingRight(), bottomPadding);
        mTopInset = bars.top;
        updateGeometry();
        // Typing wants the room: the keyboard raises the sheet for good.
        if (ime > 0) raise();
    }

    private static void setMargins(@NonNull View view, int top, int bottom) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
        if (margins.topMargin == top && margins.bottomMargin == bottom) return;
        margins.topMargin = top;
        margins.bottomMargin = bottom;
        view.setLayoutParams(margins);
    }

    /** The peek and width for the window as it stands; each setter does nothing when unchanged. */
    private void updateGeometry() {
        int height = mRoot.getHeight();
        int width = mRoot.getWidth() - mRoot.getPaddingLeft() - mRoot.getPaddingRight();
        if (height <= 0 || width <= 0) return;
        float density = mRoot.getResources().getDisplayMetrics().density;
        mBehavior.setPeekHeight(SettingsSheetPolicy.settledHeight(height, mTopInset, density, mRaised));
        int maxWidth = SettingsSheetPolicy.maxWidth(width, density, mTwoPane);
        if (mBehavior.getMaxWidth() != maxWidth) {
            mBehavior.setMaxWidth(maxWidth);
            mSheet.requestLayout();
        }
    }
}
