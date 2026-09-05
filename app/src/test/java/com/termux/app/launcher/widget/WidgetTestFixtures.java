package com.termux.app.launcher.widget;

import android.app.Activity;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;

import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

final class WidgetTestFixtures {
    static final ComponentName PROVIDER = new ComponentName("provider.pkg", "Provider");
    static final ComponentName CONFIGURE = new ComponentName("provider.pkg", "Configure");

    static AppWidgetProviderInfo info(boolean configure) {
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = PROVIDER;
        info.configure = configure ? CONFIGURE : null;
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.applicationInfo = new ApplicationInfo();
        activityInfo.applicationInfo.uid = Process.myUid();
        ReflectionHelpers.setField(info, "providerInfo", activityInfo);
        return info;
    }

    static LauncherWidgetRepository repository() {
        return new LauncherWidgetRepository(new Memory());
    }

    static final class Memory implements LauncherWidgetRepository.Storage {
        String value; boolean fail;
        @Override public String read() { return value; }
        @Override public boolean write(String value) { if (fail) return false; this.value = value; return true; }
    }

    static final class Platform implements LauncherWidgetHostController.Platform {
        final Activity activity;
        final Map<Integer, AppWidgetProviderInfo> info = new LinkedHashMap<>();
        final ArrayList<Integer> deleted = new ArrayList<>();
        int nextId = 20;
        int allocations;
        int starts;
        int stops;
        int configureLaunches;
        int optionUpdates;
        boolean feature = true;
        boolean directBind;
        AppWidgetProviderInfo directlyBoundInfo;
        boolean configureAvailable = true;
        boolean failStart;
        RuntimeException deleteFailure;
        RuntimeException bindLaunchFailure;
        RuntimeException configureLaunchFailure;
        RuntimeException optionFailure;
        Intent bindIntent;
        int bindRequestCode;
        Bundle lastOptions;
        UserHandle configureProfile;

        Platform(Activity activity) { this.activity = activity; }
        @Override public boolean hasAppWidgetsFeature() { return feature; }
        @Override public int allocateAppWidgetId() { allocations++; return nextId++; }
        @Override public void deleteAppWidgetId(int id) {
            deleted.add(id);
            if (deleteFailure != null) throw deleteFailure;
            info.remove(id);
        }
        @Override public boolean bindIfAllowed(int id, UserHandle profile, ComponentName provider,
                                               Bundle options) {
            if (directBind) info.put(id, directlyBoundInfo == null
                ? WidgetTestFixtures.info(false) : directlyBoundInfo);
            return directBind;
        }
        @Override public void launchBindConsent(Intent intent, int requestCode) {
            if (bindLaunchFailure != null) throw bindLaunchFailure;
            bindIntent = intent; bindRequestCode = requestCode;
        }
        @Override public void launchConfiguration(int id, int requestCode, Bundle options) {
            if (configureLaunchFailure != null) throw configureLaunchFailure;
            configureLaunches++;
        }
        @Override public AppWidgetProviderInfo getInfo(int id) { return info.get(id); }
        @Override public int[] getOwnedIds() {
            int[] ids = new int[info.size()]; int i = 0;
            for (int id : info.keySet()) ids[i++] = id;
            return ids;
        }
        @Override public void startListening() {
            starts++;
            if (failStart) throw new RuntimeException("start");
        }
        @Override public void stopListening() { stops++; }
        @Override public AppWidgetHostView createView(int id, AppWidgetProviderInfo info) {
            AppWidgetHostView view = new AppWidgetHostView(activity);
            view.setAppWidget(id, info);
            // Robolectric's framework resources give the host view no default padding; a real
            // device pads every widget, and the grid's size reports must stay inside it.
            int pad = Math.round(8f * activity.getResources().getDisplayMetrics().density);
            view.setPadding(pad, pad, pad, pad);
            return view;
        }
        @Override public void updateOptions(int id, Bundle options) {
            if (optionFailure != null) throw optionFailure;
            optionUpdates++; lastOptions = new Bundle(options);
        }
        @Override public long profileSerial(UserHandle profile) { return 0; }
        @Override public boolean configureActivityAvailable(ComponentName configure,
                                                            UserHandle profile) {
            configureProfile = profile;
            return configureAvailable;
        }
    }
}
