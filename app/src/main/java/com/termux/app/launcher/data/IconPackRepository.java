package com.termux.app.launcher.data;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.model.IconPackInfo;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class IconPackRepository {
    private static final long CACHE_VERSION_RECHECK_MS = 10_000L;
    public static final String ACTION_NOVA_THEME = "com.novalauncher.THEME";
    public static final String ACTION_ADW_ACTIVITY_STARTER_THEME = "org.adw.ActivityStarter.THEMES";
    public static final String ACTION_ADW_THEME = "org.adw.launcher.THEMES";
    public static final String ACTION_ADW_PICK_ICON = "org.adw.launcher.icons.ACTION_PICK_ICON";
    public static final String ACTION_LAWNCHAIR_THEMED_ICON = "app.lawnchair.icons.THEMED_ICON";
    public static final String ACTION_APEX_THEME = "com.anddoes.launcher.THEME";
    public static final String ACTION_GO_THEME = "com.gau.go.launcherex.theme";

    private static final String[] DISCOVERY_ACTIONS = {
        ACTION_NOVA_THEME,
        ACTION_ADW_ACTIVITY_STARTER_THEME,
        ACTION_ADW_THEME,
        ACTION_ADW_PICK_ICON,
        ACTION_LAWNCHAIR_THEMED_ICON,
        ACTION_APEX_THEME,
        ACTION_GO_THEME
    };

    private final Context context;
    private final PackageManager packageManager;
    private final Map<String, IconPack> parsedPacks = new LinkedHashMap<>();
    private final Map<String, Long> lastVersionChecks = new LinkedHashMap<>();

    public IconPackRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.packageManager = this.context.getPackageManager();
    }

    @NonNull
    public List<IconPackInfo> discoverIconPacks() {
        Map<String, IconPackInfo> out = new LinkedHashMap<>();
        for (String action : DISCOVERY_ACTIONS) {
            Intent intent = new Intent(action);
            List<ResolveInfo> matches = packageManager.queryIntentActivities(intent, 0);
            for (ResolveInfo match : matches) {
                if (match == null || match.activityInfo == null || match.activityInfo.packageName == null) continue;
                IconPackInfo info = buildInfo(match.activityInfo.packageName, ACTION_LAWNCHAIR_THEMED_ICON.equals(action));
                if (info != null) out.put(info.packageName, info);
            }
        }
        List<IconPackInfo> sorted = new ArrayList<>(out.values());
        Collections.sort(sorted, (left, right) -> left.label.compareToIgnoreCase(right.label));
        return sorted;
    }

    /**
     * Identity of one installed pack: its package and the version code its resources came from —
     * the same pair {@link #loadIconPack} already compares to decide whether a parsed pack is
     * still current. Empty for "no pack", so it reads as a stable token for the system default.
     */
    @NonNull
    public String packIdentity(@Nullable String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return "";
        String trimmed = packageName.trim();
        IconPackInfo info = buildInfo(trimmed, false);
        return info == null ? trimmed : trimmed + ":" + info.versionCode;
    }

    /**
     * Identity of the whole icon-pack configuration now in force — the global pack and the pinned
     * one. Anything that caches treated artwork keys on this, so replacing a pack, or upgrading
     * one in place, can never hand back a render made under the previous resources.
     */
    @NonNull
    public String activeIconPackIdentity(@Nullable String globalPackPackage,
                                         @Nullable String pinnedPackPackage) {
        return packIdentity(globalPackPackage) + "/" + packIdentity(pinnedPackPackage);
    }

    @Nullable
    public IconPack loadIconPack(@Nullable String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return null;
        IconPack cached = parsedPacks.get(packageName);
        long now = SystemClock.elapsedRealtime();
        Long checkedAt = lastVersionChecks.get(packageName);
        if (checkedAt != null && now - checkedAt < CACHE_VERSION_RECHECK_MS) {
            return cached;
        }
        IconPackInfo info = buildInfo(packageName, isThemedPackage(packageName));
        lastVersionChecks.put(packageName, now);
        if (info == null) {
            parsedPacks.remove(packageName);
            return null;
        }
        if (cached != null && cached.info.versionCode == info.versionCode) return cached;
        parsedPacks.remove(packageName);

        XmlResourceParser appfilterRes = null;
        XmlResourceParser drawableRes = null;
        InputStream appfilterStream = null;
        InputStream drawableStream = null;
        try {
            Context iconPackContext = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY);
            Resources resources = iconPackContext.getResources();
            AssetManager assets = resources.getAssets();

            XmlPullParser appfilter = null;
            int appfilterId = findXmlOrRawResource(resources, packageName, "appfilter");
            if (appfilterId != 0) {
                if ("xml".equals(resources.getResourceTypeName(appfilterId))) {
                    appfilterRes = resources.getXml(appfilterId);
                    appfilter = appfilterRes;
                } else {
                    appfilterStream = resources.openRawResource(appfilterId);
                    appfilter = newPullParser(appfilterStream);
                }
            } else {
                appfilterStream = openAssetIfExists(assets, "appfilter.xml");
                if (appfilterStream != null) appfilter = newPullParser(appfilterStream);
            }

            XmlPullParser drawable = null;
            int drawableId = findXmlOrRawResource(resources, packageName, "drawable");
            if (drawableId != 0) {
                if ("xml".equals(resources.getResourceTypeName(drawableId))) {
                    drawableRes = resources.getXml(drawableId);
                    drawable = drawableRes;
                } else {
                    drawableStream = resources.openRawResource(drawableId);
                    drawable = newPullParser(drawableStream);
                }
            } else {
                drawableStream = openAssetIfExists(assets, "drawable.xml");
                if (drawableStream != null) drawable = newPullParser(drawableStream);
            }

            IconPack pack = IconPackXmlParser.parse(info, appfilter, drawable);
            parsedPacks.put(packageName, pack);
            return pack;
        } catch (Exception ignored) {
            return null;
        } finally {
            closeQuietly(appfilterRes);
            closeQuietly(drawableRes);
            closeQuietly(appfilterStream);
            closeQuietly(drawableStream);
        }
    }

    public void clearCache() {
        parsedPacks.clear();
        lastVersionChecks.clear();
    }

    @Nullable
    private IconPackInfo buildInfo(@NonNull String packageName, boolean themed) {
        try {
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            CharSequence label = packageManager.getApplicationLabel(appInfo);
            PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            long versionCode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                versionCode = packageInfo.getLongVersionCode();
            } else {
                versionCode = packageInfo.versionCode;
            }
            return new IconPackInfo(packageName, label == null ? packageName : label.toString(), versionCode, themed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isThemedPackage(@NonNull String packageName) {
        Intent intent = new Intent(ACTION_LAWNCHAIR_THEMED_ICON);
        intent.setPackage(packageName);
        return !packageManager.queryIntentActivities(intent, 0).isEmpty();
    }

    private static int findXmlOrRawResource(@NonNull Resources resources, @NonNull String packageName, @NonNull String name) {
        int id = resources.getIdentifier(name, "xml", packageName);
        if (id != 0) return id;
        return resources.getIdentifier(name, "raw", packageName);
    }

    @Nullable
    private static InputStream openAssetIfExists(@NonNull AssetManager assets, @NonNull String name) {
        try {
            return assets.open(name);
        } catch (Exception ignored) {
            return null;
        }
    }

    @NonNull
    private static XmlPullParser newPullParser(@NonNull InputStream inputStream) throws Exception {
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(inputStream, null);
        return parser;
    }

    private static void closeQuietly(@Nullable AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }
}
