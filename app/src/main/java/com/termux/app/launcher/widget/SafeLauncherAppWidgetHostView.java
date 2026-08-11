package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.termux.R;

/**
 * AppWidgetHostView that contains recoverable provider RemoteViews failures to one tile.
 *
 * <p>Only {@link RuntimeException} is caught. OutOfMemoryError, StackOverflowError, ThreadDeath,
 * linkage/VM errors, native-renderer faults and process kills cannot safely be recovered here and
 * are deliberately allowed to terminate or be handled by the platform. Nor can this boundary
 * prevent main-thread ANRs, failures before framework delivery, GPU/driver faults, or system-server
 * process death.
 */
public final class SafeLauncherAppWidgetHostView extends AppWidgetHostView {
    private static final String ERROR_TILE_TAG = "launcher_widget_error_tile";
    public interface FailureListener {
        void onRenderFailure(int appWidgetId, @NonNull String phase);
        void onRenderRecovered(int appWidgetId);
    }

    /** Test seam called immediately inside each guarded host-owned boundary. */
    public interface BoundaryProbe { void before(@NonNull String phase); }

    @Nullable private final FailureListener failureListener;
    @Nullable private BoundaryProbe boundaryProbe;
    private boolean showingLocalError;
    private boolean replacingPosted;

    public SafeLauncherAppWidgetHostView(@NonNull Context context,
                                         @Nullable FailureListener failureListener) {
        super(context);
        this.failureListener = failureListener;
    }

    public void setBoundaryProbeForTests(@Nullable BoundaryProbe probe) {
        boundaryProbe = probe;
    }

    public boolean isShowingLocalError() { return showingLocalError; }

    @Override
    protected View getErrorView() {
        if (!showingLocalError) {
            showingLocalError = true;
            if (failureListener != null) failureListener.onRenderFailure(getAppWidgetId(), "framework");
        }
        return createLocalErrorView();
    }

    @Override
    protected View getDefaultView() {
        return showingLocalError ? createLocalErrorView() : super.getDefaultView();
    }

    @Override
    public void updateAppWidget(@Nullable RemoteViews remoteViews) {
        boolean wasShowingError = showingLocalError;
        try {
            probe("update");
            super.updateAppWidget(remoteViews);
            boolean frameworkError = getChildCount() == 1
                && ERROR_TILE_TAG.equals(getChildAt(0).getTag());
            if (frameworkError) {
                showingLocalError = true;
            } else if (wasShowingError) {
                showingLocalError = false;
                if (failureListener != null) failureListener.onRenderRecovered(getAppWidgetId());
            }
        } catch (RuntimeException exception) {
            replaceWithError("update");
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            probe("measure");
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } catch (RuntimeException exception) {
            replaceWithError("measure");
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        try {
            probe("layout");
            super.onLayout(changed, left, top, right, bottom);
        } catch (RuntimeException exception) {
            replaceWithError("layout");
            super.onLayout(changed, left, top, right, bottom);
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        try {
            probe("draw");
            super.dispatchDraw(canvas);
        } catch (RuntimeException exception) {
            postReplaceWithError("draw");
        }
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        try {
            probe("touch");
            return super.dispatchTouchEvent(event);
        } catch (RuntimeException exception) {
            postReplaceWithError("touch");
            return false;
        }
    }

    private void probe(String phase) {
        BoundaryProbe probe = boundaryProbe;
        if (probe != null && !showingLocalError) probe.before(phase);
    }

    private void postReplaceWithError(String phase) {
        if (replacingPosted || showingLocalError) return;
        replacingPosted = true;
        new Handler(Looper.getMainLooper()).post(() -> {
            replacingPosted = false;
            replaceWithError(phase);
        });
    }

    private void replaceWithError(String phase) {
        if (showingLocalError) return;
        showingLocalError = true;
        // Let AppWidgetHostView replace its tracked mView/mViewMode content. Direct child
        // mutation leaves those private fields pointing at a detached provider view.
        String message = getContext().getString(R.string.launcher_widget_error);
        String providerLabel = safeProviderLabel();
        RemoteViews error = new RemoteViews(getContext().getPackageName(),
            R.layout.launcher_widget_error_tile);
        error.setTextViewText(R.id.launcher_widget_error_message,
            providerLabel == null ? message : message + "\n" + providerLabel);
        error.setContentDescription(R.id.launcher_widget_error_tile,
            providerLabel == null ? message : message + ", " + providerLabel);
        super.updateAppWidget(error);
        if (failureListener != null) failureListener.onRenderFailure(getAppWidgetId(), phase);
    }

    private View createLocalErrorView() {
        Context context = getContext();
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        int padding = Math.round(16f * getResources().getDisplayMetrics().density);
        tile.setPadding(padding, padding, padding, padding);
        tile.setBackgroundColor(Color.TRANSPARENT);
        tile.setClickable(false);
        tile.setFocusable(false);
        tile.setTag(ERROR_TILE_TAG);

        ImageView icon = new ImageView(context);
        Drawable warning = ContextCompat.getDrawable(context, R.drawable.ic_widget_error);
        icon.setImageDrawable(warning);
        tile.addView(icon, new LinearLayout.LayoutParams(padding * 2, padding * 2));

        String message = context.getString(R.string.launcher_widget_error);
        String providerLabel = safeProviderLabel();
        TextView label = new TextView(context);
        label.setGravity(Gravity.CENTER);
        label.setText(providerLabel == null ? message : message + "\n" + providerLabel);
        tile.addView(label, new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        tile.setContentDescription(providerLabel == null ? message : message + ", " + providerLabel);
        return tile;
    }

    @Nullable
    private String safeProviderLabel() {
        try {
            return getAppWidgetInfo() == null ? null
                : String.valueOf(getAppWidgetInfo().loadLabel(getContext().getPackageManager()));
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
