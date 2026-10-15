package com.termux.app.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.Application;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.termux.R;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.data.LauncherConfigRepository;
import com.termux.app.launcher.icon.AsyncIconBinder;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.model.PinnedAppItem;
import com.termux.app.launcher.model.PinnedItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowPackageManager;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;

/**
 * Removing pins from the "Edit pinned apps" sheet as fast as a thumb can: every removal lands,
 * and the apps list's ticks show what Done will save rather than what was pinned on entry.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PinnedAppsEditorRemoveTest {

    private static final String[] LABELS = {"Clock", "Chrome", "Photos"};
    private static final int WIDTH_PX = 1080;
    private static final int HEIGHT_PX = 2400;

    private LauncherConfigRepository repository;
    private Context sheetContext;

    @Before public void setUp() throws Exception {
        AsyncIconBinder.setSynchronousForTesting(true);
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        sheetContext = new ContextThemeWrapper(activity, R.style.Theme_TermuxApp_DayNight_NoActionBar);

        Context app = activity.getApplicationContext();
        seedLauncherApps(app);
        // A provider left by another test holds that test's context and catalogue.
        ReflectionHelpers.setStaticField(LauncherAppDataProvider.class, "instance", null);
        LauncherAppDataProvider provider = LauncherAppDataProvider.getInstance(app);
        awaitCatalogLoad(provider);

        List<PinnedItem> pins = new ArrayList<>();
        for (String label : LABELS) pins.add(new PinnedAppItem(entryLabelled(provider, label).appRef.copy()));
        repository = LauncherConfigRepository.getInstance(app);
        repository.savePinnedItems(pins);
        assertEquals(3, repository.loadPinnedItems().size());
    }

    @After public void restoreAsynchronousIcons() {
        AsyncIconBinder.setSynchronousForTesting(false);
    }

    @Test public void removingTheFirstRowThreeTimesQuicklyLeavesNothingPinnedAndNothingTicked() {
        PinnedAppsEditor.show(sheetContext, null);
        shadowOf(Looper.getMainLooper()).idle();
        Dialog sheet = ShadowDialog.getLatestDialog();
        assertNotNull("the editor is showing", sheet);
        View decor = sheet.getWindow().getDecorView();
        layOut(decor);

        RecyclerView order = find(decor, RecyclerView.class);
        ListView apps = find(decor, ListView.class);
        assertNotNull(order);
        assertNotNull(apps);
        assertEquals(3, order.getAdapter().getItemCount());
        for (String label : LABELS) assertTrue(label + " starts ticked", isTicked(apps, label));

        // The row now at the top, each time, before any animation or layout has run.
        for (int tap = 0; tap < 3; tap++) {
            RecyclerView.ViewHolder first = holderAt(order, 0);
            assertNotNull("tap " + (tap + 1) + " finds a row at the top", first);
            ((ViewGroup) first.itemView).getChildAt(3).performClick();
        }
        assertEquals("every tap removed a pin", 0, order.getAdapter().getItemCount());

        layOut(decor);
        for (String label : LABELS) assertFalse(label + " is no longer ticked", isTicked(apps, label));

        MaterialButton done = findButton(decor, "Done");
        assertNotNull(done);
        done.performClick();
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue("Done saves no pins", repository.loadPinnedItems().isEmpty());
    }

    @Nullable
    private static RecyclerView.ViewHolder holderAt(RecyclerView recycler, int adapterPosition) {
        for (int i = 0; i < recycler.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = recycler.getChildViewHolder(recycler.getChildAt(i));
            if (holder != null && holder.getAdapterPosition() == adapterPosition) return holder;
        }
        return null;
    }

    private static boolean isTicked(ListView apps, String label) {
        for (int i = 0; i < apps.getChildCount(); i++) {
            ViewGroup row = (ViewGroup) apps.getChildAt(i);
            if (label.contentEquals(((TextView) row.getChildAt(1)).getText())) {
                return ((MaterialCheckBox) row.getChildAt(2)).isChecked();
            }
        }
        throw new AssertionError(label + " has no row in the apps list");
    }

    private static void layOut(View decor) {
        decor.measure(View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY));
        decor.layout(0, 0, WIDTH_PX, HEIGHT_PX);
    }

    @Nullable
    private static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = find(group.getChildAt(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Nullable
    private static MaterialButton findButton(View view, String text) {
        if (view instanceof MaterialButton && text.contentEquals(((MaterialButton) view).getText())) {
            return (MaterialButton) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                MaterialButton found = findButton(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static LauncherAppEntry entryLabelled(LauncherAppDataProvider provider, String label) {
        String pkg = packageFor(label);
        for (LauncherAppEntry entry : provider.getAllApps()) {
            if (pkg.equals(entry.appRef.packageName)) return entry;
        }
        throw new AssertionError("the catalogue has no " + label + ": " + provider.getAllApps().size() + " apps");
    }

    private static void seedLauncherApps(Context context) {
        ShadowPackageManager packageManager = shadowOf(context.getPackageManager());
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        for (String label : LABELS) {
            String pkg = packageFor(label);
            PackageInfo packageInfo = new PackageInfo();
            packageInfo.packageName = pkg;
            packageInfo.applicationInfo = new ApplicationInfo();
            packageInfo.applicationInfo.packageName = pkg;
            packageManager.installPackage(packageInfo);

            ResolveInfo resolveInfo = new ResolveInfo();
            resolveInfo.nonLocalizedLabel = label;
            resolveInfo.activityInfo = new ActivityInfo();
            resolveInfo.activityInfo.packageName = pkg;
            resolveInfo.activityInfo.name = pkg + ".MainActivity";
            // The catalogue reads the activity's own label, not the resolve info's.
            resolveInfo.activityInfo.nonLocalizedLabel = label;
            resolveInfo.activityInfo.applicationInfo = new ApplicationInfo();
            resolveInfo.activityInfo.applicationInfo.packageName = pkg;
            packageManager.addResolveInfoForIntent(launcherIntent, resolveInfo);
        }
    }

    private static String packageFor(String label) {
        return "com.example." + label.toLowerCase(java.util.Locale.US);
    }

    private static void awaitCatalogLoad(LauncherAppDataProvider provider) throws Exception {
        long deadline = System.nanoTime() + 2_000_000_000L;
        provider.warmAsync(null);
        while (!provider.hasLoadedApps() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10L);
        }
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("the catalogue loads before the editor opens", provider.hasLoadedApps());
    }
}
