package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.termux.app.SuggestionBarView;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.data.LauncherConfigSnapshot;
import com.termux.app.launcher.model.AppRef;
import com.termux.app.launcher.model.LauncherAppEntry;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class AppDrawerAcceptedDropRefreshIntegrationTest {
    @Test public void acceptedProductionDropRecomposesVisibleItemsWithoutReopenInEveryPickupView() {
        for (AppDrawerViewType type : new AppDrawerViewType[] {
            AppDrawerViewType.VERTICAL, AppDrawerViewType.HORIZONTAL
        }) {
            Fixture fixture = new Fixture(type);
            assertNotNull(type.name(), fixture.visibleItemAt(0));
            assertNotNull(type.name(), fixture.visibleItemAt(1));

            LauncherConfigSnapshot before = fixture.repository.loadSnapshot();
            fixture.content.onDragStateChanged(true);
            assertTrue(type.name(), fixture.drag.applyDropMutation(
                fixture.source.appRef.stableId(), before.revision,
                AppDrawerItem.app(fixture.target)));

            // ACTION_DROP has committed, but the source holder remains stable until ACTION_DRAG_ENDED.
            assertNotNull(type.name(), fixture.visibleItemAt(1));
            assertEquals(type.name(), 1, fixture.repository.loadSnapshot().folders.size());

            fixture.content.onDragStateChanged(false);
            fixture.idleAndLayout();
            assertEquals(type.name(), AppDrawerItem.Kind.FOLDER,
                fixture.visibleItemAt(0).kind);
            assertNull(type.name(), fixture.visibleItemAt(1));
        }
    }

    private static final class Fixture {
        final Activity activity;
        final SuggestionBarView dock;
        final AppDrawerContentView content;
        final LauncherConfigRepository repository;
        final AppDrawerDragController drag;
        final LauncherAppEntry source = app("com.example.alpha", "Alpha");
        final LauncherAppEntry target = app("com.example.beta", "Beta");

        Fixture(AppDrawerViewType type) {
            activity = Robolectric.buildActivity(Activity.class).setup().get();
            repository = new LauncherConfigRepository(new Store());
            dock = new SuggestionBarView(activity, null);
            dock.setConfigRepository(repository);
            List<LauncherAppEntry> apps = Arrays.asList(source, target);
            ReflectionHelpers.setField(dock, "allApps", apps);

            AppDrawerSearchController search = new AppDrawerSearchController();
            search.setHost(new AppDrawerSearchController.Host() {
                @Override public boolean isSearchActive() { return true; }
                @Override public void onSearchCommitRequested() { }
                @Override public void onSearchDismissRequested() { }
            });
            content = new AppDrawerContentView(activity, dock);
            content.setInteractive(true);
            content.setViewType(type);
            float density = activity.getResources().getDisplayMetrics().density;
            content.setMetrics(AppDrawerGridMetrics.resolve(720, density, 30));
            content.setHorizontalMetrics(AppDrawerHorizontalGridMetrics.resolve(
                720, content.horizontalPagerUsableHeight(1280), density, 11, 4, 2));
            content.bind(null, search);
            search.setCatalogue(apps);

            FrameLayout root = new FrameLayout(activity);
            dock.setVisibility(View.GONE);
            root.addView(dock, new FrameLayout.LayoutParams(1, 1));
            root.addView(content, new FrameLayout.LayoutParams(720, 1280));
            activity.setContentView(root);
            drag = new AppDrawerDragController(dock,
                new AppDrawerDragOverlayView(activity), content);
            idleAndLayout();
        }

        AppDrawerItem visibleItemAt(int index) {
            if (content.getViewType() == AppDrawerViewType.HORIZONTAL) {
                return content.getHorizontalAdapter().itemAt(index);
            }
            RecyclerView.Adapter<?> raw = content.getGrid().getAdapter();
            return raw instanceof AppDrawerAppsAdapter
                ? ((AppDrawerAppsAdapter) raw).itemAt(index) : null;
        }

        void idleAndLayout() {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
            View root = activity.findViewById(android.R.id.content);
            root.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 720, 1280);
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
        }
    }

    private static LauncherAppEntry app(String packageName, String label) {
        return new LauncherAppEntry(new AppRef(packageName, "Main"), label,
            new ColorDrawable(Color.RED));
    }

    private static final class Store implements LauncherConfigRepository.PreferencesStore {
        String raw = "";
        @Override public String getPinnedItemsV2() { return raw; }
        @Override public int getPinnedItemsSchemaVersion() { return 0; }
        @Override public boolean commitPinnedItems(String value, int version) {
            raw = value;
            return true;
        }
        @Override public String getLegacyDefaultButtons() { return ""; }
    }
}
