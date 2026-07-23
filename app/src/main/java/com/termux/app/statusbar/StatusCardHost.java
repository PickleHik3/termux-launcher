package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Hosts the single status-bar detail card. Exactly one card is shown at a time; opening a new one
 * dismisses the previous. The card is a {@link PopupWindow} anchored beneath the tapped widget and
 * dismisses on an outside tap, on Back, or whenever {@link #dismiss()} is called (e.g. a window
 * change). Width is constrained to the portrait-screen bounds; the popup itself keeps the card on
 * screen vertically.
 */
public final class StatusCardHost {

    /** Supplies the current status-bar styling so the card matches Default glass or the capsule. */
    public interface StyleProvider {
        @NonNull Drawable cardBackground();
        float cornerRadiusPx();
    }

    @Nullable private PopupWindow mPopup;
    @Nullable private View mAnchor;
    @Nullable private Runnable mOnDismiss;

    public boolean isShowing() {
        return mPopup != null && mPopup.isShowing();
    }

    public boolean isShowingFor(@Nullable View anchor) {
        return isShowing() && mAnchor == anchor;
    }

    /**
     * Show {@code content} in a card anchored beneath {@code anchor}. Any currently open card is
     * dismissed first. {@code onDismiss} runs when this card goes away for any reason.
     */
    public void show(@NonNull View anchor, @NonNull View content, @NonNull StyleProvider style,
                     @Nullable Runnable onDismiss) {
        show(anchor, content, style, 300, onDismiss);
    }

    /** Variant with a caller-selected portrait width; used by the horizontal weather forecast. */
    public void show(@NonNull View anchor, @NonNull View content, @NonNull StyleProvider style,
                     int desiredWidthDp, @Nullable Runnable onDismiss) {
        dismiss();
        Context context = anchor.getContext();
        int maxWidth = portraitMaxWidthPx(context, desiredWidthDp);

        FrameLayout container = new FrameLayout(context);
        final float radius = style.cornerRadiusPx();
        container.setBackground(style.cardBackground());
        container.setClipToOutline(true);
        container.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        container.setElevation(dp(context, 10));
        int pad = dp(context, 10);
        container.setPadding(pad, pad, pad, pad);
        container.addView(content,
            new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        PopupWindow popup = new PopupWindow(container, maxWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(0));   // required for outside-touch dismissal
        popup.setOutsideTouchable(true);
        popup.setClippingEnabled(true);                      // keep the card inside screen bounds
        popup.setOnDismissListener(() -> {
            mPopup = null;
            mAnchor = null;
            Runnable cb = mOnDismiss;
            mOnDismiss = null;
            if (cb != null) cb.run();
        });

        mPopup = popup;
        mAnchor = anchor;
        mOnDismiss = onDismiss;

        // Anchor the card's right edge under the widget so trailing widgets open cards that stay on
        // screen, then drop it just below the status row.
        int xOffset = anchorRightAlignedXOffset(anchor, maxWidth);
        popup.showAsDropDown(anchor, xOffset, dp(context, 4), Gravity.START);
    }

    public void dismiss() {
        if (mPopup != null) {
            PopupWindow popup = mPopup;
            mPopup = null;   // guard against re-entrancy through the dismiss listener
            popup.dismiss();
        }
    }

    private static int anchorRightAlignedXOffset(@NonNull View anchor, int cardWidth) {
        // showAsDropDown aligns the popup's start to the anchor's start; shift left so the card's
        // right edge lines up with the anchor's right edge (keeps trailing-widget cards on screen).
        return anchor.getWidth() - cardWidth;
    }

    private static int portraitMaxWidthPx(@NonNull Context context, int desiredWidthDp) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int screenWidth = Math.min(dm.widthPixels, dm.heightPixels); // portrait width regardless of orientation
        int margin = dp(context, 12);
        int desired = dp(context, Math.max(200, desiredWidthDp));
        return Math.min(desired, Math.max(dp(context, 200), screenWidth - 2 * margin));
    }

    private static int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    // Kept for callers that want to clamp custom content to the same visible portrait rect.
    public static void clampToPortrait(@NonNull Rect out, @NonNull Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        out.set(0, 0, Math.min(dm.widthPixels, dm.heightPixels), Math.max(dm.widthPixels, dm.heightPixels));
    }
}
