package com.termux.app.launcher.widget.builtin;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.launcher.icon.DrawablePixels;
import com.termux.app.launcher.widget.LauncherWidgetRecord;
import com.termux.app.launcher.widget.WidgetAppGroup;
import com.termux.app.launcher.widget.WidgetGridMetrics;
import com.termux.app.launcher.widget.WidgetGridView;
import com.termux.app.launcher.widget.WidgetPreviewArtwork;
import com.termux.app.launcher.widget.WidgetProviderCatalogLoader;
import com.termux.app.launcher.widget.WidgetProviderItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The built-in widgets' side of the widgets page: it makes the views the grid shows, keeps them
 * across renders so a resize reaches the view already on screen, lists the catalogue for the
 * picker and draws the picker's cards. One per page, owning one {@link BuiltinWidgetServices}.
 */
public final class BuiltinWidgetHost implements WidgetGridView.BuiltinFactory,
    WidgetProviderCatalogLoader.BuiltinSource {
    /** Where the page's look comes from: the direction setting and whether the page is glass. */
    public interface StyleSource {
        @NonNull BuiltinWidgetStyle.Direction direction();
        boolean glass();
    }

    private final Context context;
    private final BuiltinWidgetServices services;
    private final StyleSource styleSource;
    private final Map<Integer, BuiltinWidgetView> views = new HashMap<>();
    @Nullable private BuiltinWidgetStyle style;

    public BuiltinWidgetHost(@NonNull Context context, @NonNull BuiltinWidgetServices services,
                             @NonNull StyleSource styleSource) {
        this.context = context;
        this.services = services;
        this.styleSource = styleSource;
    }

    @NonNull public BuiltinWidgetServices services() { return services; }

    /** The current style, resolved once and kept until {@link #onStyleChanged()}. */
    @NonNull public BuiltinWidgetStyle style() {
        if (style == null) {
            style = BuiltinWidgetStyle.resolve(context, styleSource.direction(),
                styleSource.glass(), services.host().monoTypeface());
        }
        return style;
    }

    /** The theme, the Look or the direction setting changed: every live view is restyled. */
    public void onStyleChanged() {
        style = null;
        BuiltinWidgetStyle next = style();
        for (BuiltinWidgetView view : views.values()) view.applyStyle(next);
    }

    // ----- grid factory ---------------------------------------------------------------------

    @Override @Nullable public View viewFor(@NonNull LauncherWidgetRecord record) {
        BuiltinWidgetKind kind = BuiltinWidgetKind.fromId(record.builtinKind);
        if (kind == null) return null;
        BuiltinWidgetView view = views.get(record.appWidgetId);
        if (view == null || view.kind() != kind) {
            view = BuiltinWidgetFactory.create(context, kind, services, style());
            views.put(record.appWidgetId, view);
        }
        view.bind(record.cell.columnSpan(), record.cell.rowSpan(), record.sizeOptions());
        return view;
    }

    @Override public void release(int appWidgetId) {
        views.remove(appWidgetId);
    }

    /** The live view for {@code appWidgetId}, when it is on the page now. */
    @Nullable public BuiltinWidgetView viewOf(int appWidgetId) { return views.get(appWidgetId); }

    /** True when the widget offers settings, so edit mode shows its cog. */
    public boolean hasSettings(@NonNull LauncherWidgetRecord record) {
        BuiltinWidgetView view = views.get(record.appWidgetId);
        if (view != null) return !view.configFields().isEmpty();
        BuiltinWidgetKind kind = BuiltinWidgetKind.fromId(record.builtinKind);
        if (kind == null) return false;
        return !BuiltinWidgetFactory.create(context, kind, services, style()).configFields().isEmpty();
    }

    /** The settings sheet's fields for {@code record}, from its live view or a throwaway one. */
    @NonNull public List<BuiltinWidgetView.ConfigField> configFields(@NonNull LauncherWidgetRecord record) {
        BuiltinWidgetView view = views.get(record.appWidgetId);
        if (view == null) {
            BuiltinWidgetKind kind = BuiltinWidgetKind.fromId(record.builtinKind);
            if (kind == null) return new ArrayList<>();
            view = BuiltinWidgetFactory.create(context, kind, services, style());
        }
        return view.configFields();
    }

    // ----- picker source --------------------------------------------------------------------

    @Override @NonNull public WidgetAppGroup group(@NonNull WidgetGridMetrics metrics) {
        List<WidgetProviderItem> items = new ArrayList<>();
        int columns = metrics.definition().columns, rows = metrics.definition().rows;
        for (BuiltinWidgetKind kind : BuiltinWidgetKind.values()) {
            boolean fits = kind.defaultColumns <= columns && kind.defaultRows <= rows;
            items.add(WidgetProviderItem.builtin(0L, kind.id, context.getString(kind.label),
                Math.min(kind.defaultColumns, Math.max(1, columns)),
                Math.min(kind.defaultRows, Math.max(1, rows)), fits));
        }
        Drawable icon = null;
        try { icon = context.getPackageManager().getApplicationIcon(context.getPackageName()); }
        catch (android.content.pm.PackageManager.NameNotFoundException | RuntimeException ignored) { }
        return new WidgetAppGroup(0L, LauncherWidgetRecord.BUILTIN_PROVIDER_PACKAGE,
            context.getString(R.string.builtin_widget_group), icon, items);
    }

    @Override @Nullable public WidgetPreviewArtwork preview(@NonNull WidgetProviderItem item,
                                                           int extentPx) {
        BuiltinWidgetKind kind = BuiltinWidgetKind.fromId(item.builtinKind);
        if (kind == null) return null;
        Drawable drawn = renderPreview(kind, item.columnSpan, item.rowSpan, extentPx);
        return drawn == null ? null : WidgetPreviewArtwork.image(drawn);
    }

    /**
     * Draws {@code kind} at the real size of its default cell span — 88×92dp cells, 8dp gaps —
     * and shrinks the picture to the card. Sample data stands in for whatever is not live.
     */
    @Nullable private Drawable renderPreview(@NonNull BuiltinWidgetKind kind, int columns, int rows,
                                             int extentPx) {
        float density = context.getResources().getDisplayMetrics().density;
        int width = Math.round((columns * 88 + (columns - 1) * 8) * density);
        int height = Math.round((rows * 92 + (rows - 1) * 8) * density);
        try {
            BuiltinWidgetView view = BuiltinWidgetFactory.create(context, kind, services, style());
            view.setPreview(true);
            view.bind(columns, rows, null);
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, width, height);
            float scale = Math.min(1f, extentPx / (float) Math.max(width, height));
            int outWidth = Math.max(1, Math.round(width * scale));
            int outHeight = Math.max(1, Math.round(height * scale));
            Bitmap bitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(scale, scale);
            view.draw(canvas);
            return new BitmapDrawable(context.getResources(), bitmap);
        } catch (RuntimeException | OutOfMemoryError exception) {
            return null;
        }
    }

    /** Settings written by the sheet, as the bundle a record keeps. */
    @NonNull public static Bundle configOf(@NonNull Map<String, String> values) {
        Bundle bundle = new Bundle();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                bundle.putString(entry.getKey(), entry.getValue());
            }
        }
        return bundle;
    }

    public void destroy() {
        views.clear();
        services.destroy();
    }

    /** Keeps {@link DrawablePixels} in the picture: the picker budgets previews by held bytes. */
    static int previewBytes(@Nullable Drawable drawable) { return DrawablePixels.heldBytes(drawable); }
}
