package com.termux.app.chrome.scene;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Debug-only (debug.tl.scene_trace_inval = 1, debuggable builds): a thin FrameLayout wrapped around
 * the activity's content root that records every invalidation and layout request bubbling up to
 * the window, and every pre-draw, draw and global layout the ViewRootImpl runs. Every ~2 s it logs
 * under tag {@code TLSceneInval}: the counts per target view class/id, one sampled stack trace per
 * distinct target class, the scene's own draw count, and the traversal-side counters. If the
 * scene draws 30/s and pre-draw/draw climb 30/s while no target is counted, the traversal is
 * scheduled below the view layer (ViewRootImpl or SurfaceView internals), not by a view.
 */
final class SceneInvalTrace extends FrameLayout {

    static final String TAG = "TLSceneInval";
    private static final long REPORT_MS = 2000L;
    private static final int MAX_STACKS = 12;

    private final Map<String, int[]> mTargets = new HashMap<>();
    private final Map<String, String> mStacks = new HashMap<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final LongSupplier mSceneDraws;
    private int mLayoutRequests, mPreDraws, mDraws, mGlobalLayouts, mInsets;
    private String mLayoutStack;
    private long mLastScene;
    private boolean mRunning;

    private final ViewTreeObserver.OnPreDrawListener mPreDraw = () -> { mPreDraws++; return true; };
    private final ViewTreeObserver.OnDrawListener mDraw = () -> mDraws++;
    private final ViewTreeObserver.OnGlobalLayoutListener mLayout = () -> mGlobalLayouts++;
    private final Runnable mReport = this::report;

    SceneInvalTrace(@NonNull Context context, @NonNull LongSupplier sceneDraws) {
        super(context);
        mSceneDraws = sceneDraws;
    }

    @Override
    public void onDescendantInvalidated(@NonNull View child, @NonNull View target) {
        String key = target.getClass().getName() + "#" + idOf(target);
        int[] n = mTargets.get(key);
        if (n == null) {
            n = new int[1];
            mTargets.put(key, n);
            if (mStacks.size() < MAX_STACKS) mStacks.put(key, stack());
        }
        n[0]++;
        super.onDescendantInvalidated(child, target);
    }

    @Override
    public void requestLayout() {
        mLayoutRequests++;
        if (mLayoutStack == null && isAttachedToWindow()) mLayoutStack = stack();
        super.requestLayout();
    }

    @Override
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        mInsets++;
        return super.dispatchApplyWindowInsets(insets);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ViewTreeObserver o = getViewTreeObserver();
        o.addOnPreDrawListener(mPreDraw);
        o.addOnDrawListener(mDraw);
        o.addOnGlobalLayoutListener(mLayout);
        mRunning = true;
        mLastScene = mSceneDraws.getAsLong();
        mHandler.postDelayed(mReport, REPORT_MS);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mRunning = false;
        mHandler.removeCallbacks(mReport);
        ViewTreeObserver o = getViewTreeObserver();
        if (o.isAlive()) {
            o.removeOnPreDrawListener(mPreDraw);
            o.removeOnDrawListener(mDraw);
            o.removeOnGlobalLayoutListener(mLayout);
        }
    }

    private void report() {
        if (!mRunning) return;
        long scene = mSceneDraws.getAsLong();
        StringBuilder sb = new StringBuilder("per ~2s: sceneDraws=").append(scene - mLastScene)
            .append(" preDraw=").append(mPreDraws).append(" draw=").append(mDraws)
            .append(" globalLayout=").append(mGlobalLayouts).append(" layoutReq=")
            .append(mLayoutRequests).append(" insets=").append(mInsets);
        Log.i(TAG, sb.toString());
        mTargets.entrySet().stream()
            .sorted((a, b) -> b.getValue()[0] - a.getValue()[0]).limit(8)
            .forEach(e -> Log.i(TAG, "  inval " + e.getValue()[0] + " x " + e.getKey()));
        if (mLayoutStack != null) Log.i(TAG, "  layout stack: " + mLayoutStack);
        for (Map.Entry<String, String> e : mStacks.entrySet()) {
            Log.i(TAG, "  stack for " + e.getKey() + ": " + e.getValue());
        }
        mStacks.clear();
        mLayoutStack = null;
        mTargets.clear();
        mLastScene = scene;
        mPreDraws = mDraws = mGlobalLayouts = mLayoutRequests = mInsets = 0;
        mHandler.postDelayed(mReport, REPORT_MS);
    }

    private static String idOf(View v) {
        int id = v.getId();
        if (id == View.NO_ID) return "noid";
        try {
            return v.getResources().getResourceEntryName(id);
        } catch (Exception e) {
            return Integer.toString(id);
        }
    }

    private static String stack() {
        StackTraceElement[] st = new Throwable().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int i = 2; i < st.length && n < 14; i++, n++) {
            sb.append("\n    ").append(st[i]);
        }
        return sb.toString();
    }
}
