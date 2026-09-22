package com.termux.app.launcher.widget;

import android.app.Activity;
import android.app.Application;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RemoteViews;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** What a provider card is shaped like, and what it draws once its artwork lands. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetPickerCardTest {

    /** The card is the widget's shape, snapped, so a 4x1 never reads as a 2x2. */
    @Test public void cardsAreSizedFromTheSpanAndSnappedToATemplate() {
        Harness harness = new Harness(item("Tall", 2, 2), item("Wide", 3, 1));
        harness.open();
        float density = harness.activity.getResources().getDisplayMetrics().density;

        View square = harness.slotAt(1);
        assertEquals(WidgetPickerCardTemplate.forSpan(2, 2).widthPx(density),
            square.getLayoutParams().width);
        assertEquals(WidgetPickerCardTemplate.forSpan(2, 2).heightPx(density),
            square.getLayoutParams().height);

        View wide = harness.slotAt(2);
        assertEquals(WidgetPickerCardTemplate.forSpan(4, 1).widthPx(density),
            wide.getLayoutParams().width);
        assertEquals(WidgetPickerCardTemplate.forSpan(4, 1).heightPx(density),
            wide.getLayoutParams().height);
        assertTrue(wide.getLayoutParams().width > square.getLayoutParams().width);
        assertTrue(wide.getLayoutParams().height < square.getLayoutParams().height);
    }

    /** A live preview is a real host view in the card, with no bound id, not a picture of one. */
    @Test public void aLivePreviewBecomesAHostViewAndHidesTheBitmapSlot() {
        Harness harness = new Harness(item("Agenda", 4, 2));
        harness.loader.artwork = WidgetPreviewArtwork.live(WidgetPreviewArtwork.TIER_GENERATED,
            new RemoteViews(harness.activity.getPackageName(), R.layout.launcher_widget_error_tile));
        harness.open();
        FrameLayout slot = (FrameLayout) harness.slotAt(1);
        AppWidgetHostView host = hostIn(slot);
        assertNotNull(host);
        assertEquals(View.GONE, ((ImageView) slot.findViewWithTag("preview")).getVisibility());
        // Scaled into the card rather than laid out at the card's size.
        float density = harness.activity.getResources().getDisplayMetrics().density;
        int slotWidth = WidgetPickerCardTemplate.forSpan(4, 2).widthPx(density);
        assertEquals(240, host.getLayoutParams().width); // the provider's own pixels
        assertEquals(slotWidth / 240f, host.getScaleX(), 0.001f);
        assertEquals(host.getScaleX(), host.getScaleY(), 0f);
        assertEquals(0f, host.getPivotX(), 0f);
    }

    /** A bitmap tier stays a bitmap, and a provider with nothing keeps the stand-in glyph. */
    @Test public void theBitmapTierStaysAnImageAndAnEmptyProviderKeepsTheGlyph() {
        Harness harness = new Harness(item("Clock", 1, 1));
        harness.loader.artwork = WidgetPreviewArtwork.image(new ColorDrawable(7));
        harness.open();
        FrameLayout slot = (FrameLayout) harness.slotAt(1);
        assertNull(hostIn(slot));
        ImageView preview = slot.findViewWithTag("preview");
        assertEquals(View.VISIBLE, preview.getVisibility());
        assertNotNull(preview.getDrawable());

        Harness empty = new Harness(item("Clock", 1, 1));
        empty.loader.artwork = null;
        empty.open();
        ImageView glyph = ((FrameLayout) empty.slotAt(1)).findViewWithTag("preview");
        assertEquals(View.VISIBLE, glyph.getVisibility());
        assertEquals(ImageView.ScaleType.CENTER_INSIDE, glyph.getScaleType());
    }

    /**
     * A preview whose layout cannot be inflated — a broken or hostile provider — must not take the
     * sheet down: the card reports it, falls back and gets a flat answer instead.
     */
    @Test public void aPreviewThatWillNotInflateIsReportedAndFallsBackToTheBitmap() {
        Harness harness = new Harness(item("Broken", 2, 2));
        harness.loader.artwork = WidgetPreviewArtwork.live(WidgetPreviewArtwork.TIER_PREVIEW_LAYOUT,
            new RemoteViews(harness.activity.getPackageName(), 0x7f999999));
        harness.loader.fallback = WidgetPreviewArtwork.image(new ColorDrawable(5));
        harness.open();
        FrameLayout slot = (FrameLayout) harness.slotAt(1);
        assertNull(hostIn(slot));
        assertEquals(1, harness.loader.failuresReported);
        assertEquals(2, harness.loader.requests); // the bind, then the flat retry
        ImageView preview = slot.findViewWithTag("preview");
        assertEquals(View.VISIBLE, preview.getVisibility());
        assertNotNull(preview.getDrawable());
    }

    private static AppWidgetHostView hostIn(ViewGroup slot) {
        for (int i = 0; i < slot.getChildCount(); i++) {
            if (slot.getChildAt(i) instanceof AppWidgetHostView) {
                return (AppWidgetHostView) slot.getChildAt(i);
            }
        }
        return null;
    }

    private static WidgetProviderItem item(String label, int columns, int rows) {
        AppWidgetProviderInfo info = WidgetTestFixtures.info(false);
        info.provider = new ComponentName("pkg", label);
        info.widgetCategory = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN;
        info.minWidth = 240; info.minHeight = 120;
        return new WidgetProviderItem(0, info, label, columns, rows, 1, 1, true);
    }

    /** One app row with its cards open, laid out, so the holders exist and are bound. */
    private static final class Harness {
        final Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        final FakeLoader loader = new FakeLoader();
        final WidgetPickerAdapter adapter = new WidgetPickerAdapter(item -> { });
        final RecyclerView list;
        final WidgetAppGroup group;

        Harness(WidgetProviderItem... items) {
            activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
            list = new RecyclerView(activity);
            list.setLayoutManager(new LinearLayoutManager(activity));
            list.setAdapter(adapter);
            activity.setContentView(list);
            adapter.setPreviewLoader(loader);
            List<WidgetProviderItem> providers = new ArrayList<>();
            Collections.addAll(providers, items);
            group = new WidgetAppGroup(0, "pkg", "App", new ColorDrawable(1), providers);
        }

        void open() {
            adapter.submit(Collections.singletonList(group));
            adapter.toggleSection(group);
            list.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1600, View.MeasureSpec.EXACTLY));
            list.layout(0, 0, 1000, 1600);
        }

        View slotAt(int position) {
            RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(position);
            assertNotNull("no holder at " + position, holder);
            View slot = holder.itemView.findViewWithTag("slot");
            assertNotNull(slot);
            return slot;
        }
    }

    private static final class FakeLoader implements WidgetPickerAdapter.PreviewLoader {
        WidgetPreviewArtwork artwork;
        WidgetPreviewArtwork fallback;
        int requests;
        int failuresReported;

        @Override public void loadPreview(WidgetProviderItem item,
                                          WidgetProviderCatalogLoader.PreviewCallback callback) {
            requests++;
            callback.onPreview(item, failuresReported > 0 ? fallback : artwork);
        }
        @Override public void notePreviewRenderFailed(WidgetProviderItem item) {
            failuresReported++;
        }
        @Override public void releasePreviews() { }
    }
}
