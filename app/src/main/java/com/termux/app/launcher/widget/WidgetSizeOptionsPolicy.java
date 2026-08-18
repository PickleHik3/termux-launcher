package com.termux.app.launcher.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.SizeF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/** Pure conversion and deduplication for resize-end app-widget option updates. */
public final class WidgetSizeOptionsPolicy {
    private static final String PORTRAIT_WIDTH = "launcher_widget_portrait_width";
    private static final String PORTRAIT_HEIGHT = "launcher_widget_portrait_height";
    private static final String LANDSCAPE_WIDTH = "launcher_widget_landscape_width";
    private static final String LANDSCAPE_HEIGHT = "launcher_widget_landscape_height";

    public static final class Result {
        public final boolean valid;
        public final boolean changed;
        @NonNull public final Bundle options;

        private Result(boolean valid, boolean changed, @NonNull Bundle options) {
            this.valid = valid;
            this.changed = changed;
            this.options = new Bundle(options);
        }
    }

    private WidgetSizeOptionsPolicy() {}

    @NonNull
    public static Result calculate(@Nullable Bundle previous, int widthPx, int heightPx,
                                   float density, int orientation, int sdkInt) {
        Bundle old = previous == null ? new Bundle() : new Bundle(previous);
        if (widthPx <= 0 || heightPx <= 0 || density <= 0f
            || Float.isNaN(density) || Float.isInfinite(density)) {
            return new Result(false, false, old);
        }
        int widthDp = Math.max(1, Math.round(widthPx / density));
        int heightDp = Math.max(1, Math.round(heightPx / density));
        Bundle out = new Bundle(old);
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            out.putInt(LANDSCAPE_WIDTH, widthDp);
            out.putInt(LANDSCAPE_HEIGHT, heightDp);
        } else {
            out.putInt(PORTRAIT_WIDTH, widthDp);
            out.putInt(PORTRAIT_HEIGHT, heightDp);
        }

        ArrayList<int[]> sizes = knownSizes(out);
        int minWidth = Integer.MAX_VALUE;
        int minHeight = Integer.MAX_VALUE;
        int maxWidth = 0;
        int maxHeight = 0;
        for (int[] size : sizes) {
            minWidth = Math.min(minWidth, size[0]);
            minHeight = Math.min(minHeight, size[1]);
            maxWidth = Math.max(maxWidth, size[0]);
            maxHeight = Math.max(maxHeight, size[1]);
        }
        out.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minWidth);
        out.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, minHeight);
        out.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, maxWidth);
        out.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, maxHeight);
        out.putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
            AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
        if (sdkInt >= Build.VERSION_CODES.S) {
            ArrayList<SizeF> platformSizes = new ArrayList<>();
            for (int[] size : sizes) platformSizes.add(new SizeF(size[0], size[1]));
            out.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, platformSizes);
        } else {
            out.remove(AppWidgetManager.OPTION_APPWIDGET_SIZES);
        }
        return new Result(true, !effectivelyEqual(old, out, sdkInt), out);
    }

    private static ArrayList<int[]> knownSizes(Bundle options) {
        ArrayList<int[]> result = new ArrayList<>(2);
        Set<String> seen = new LinkedHashSet<>();
        addSize(options, PORTRAIT_WIDTH, PORTRAIT_HEIGHT, seen, result);
        addSize(options, LANDSCAPE_WIDTH, LANDSCAPE_HEIGHT, seen, result);
        return result;
    }

    private static void addSize(Bundle options, String widthKey, String heightKey,
                                Set<String> seen, ArrayList<int[]> result) {
        int width = options.getInt(widthKey, 0);
        int height = options.getInt(heightKey, 0);
        String key = width + "x" + height;
        if (width > 0 && height > 0 && seen.add(key) && result.size() < 8) {
            result.add(new int[] {width, height});
        }
    }

    public static boolean effectivelyEqual(@Nullable Bundle first, @Nullable Bundle second,
                                           int sdkInt) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        String[] integerKeys = {
            PORTRAIT_WIDTH, PORTRAIT_HEIGHT, LANDSCAPE_WIDTH, LANDSCAPE_HEIGHT,
            AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
            AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
            AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
            AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
            AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY
        };
        for (String key : integerKeys) {
            if (first.getInt(key, Integer.MIN_VALUE) != second.getInt(key, Integer.MIN_VALUE)) {
                return false;
            }
        }
        if (sdkInt >= Build.VERSION_CODES.S) {
            ArrayList<SizeF> a = first.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
            ArrayList<SizeF> b = second.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
            return a == null ? b == null : a.equals(b);
        }
        return true;
    }
}
