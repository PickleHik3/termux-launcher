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
import android.os.Looper;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    /** Parsed packs kept: the global one and the pinned one, no more (a parse is names, not pixels). */
    private static final int MAX_PARSED_PACKS = 2;
    /**
     * The parsed packs, shared by every thread that resolves icons (the icon store's, the main
     * thread's, the Icons mode's warm-up): a pack is parsed once, a background thread asking
     * while another parses it waits for that parse, and the main thread never parses or waits.
     * See {@link ParsedIconPackCache}.
     */
    private final ParsedIconPackCache parsedPacks =
        new ParsedIconPackCache(MAX_PARSED_PACKS, CACHE_VERSION_RECHECK_MS, SystemClock::elapsedRealtime);
    /**
     * Where a pack the main thread asked for is parsed: one thread, gone when idle. Shared by
     * every repository, since the work is rare and never parallel.
     */
    private static final ExecutorService PARSE_EXECUTOR = newIdleFriendlyExecutor();
    /** Told, on the parse thread, when a pack the main thread was answered "pending" for lands. */
    @Nullable private volatile Runnable packLandedListener;
    private final ParsedIconPackCache.Source parseSource = new ParsedIconPackCache.Source() {
        @Nullable @Override
        public IconPackInfo installed(@NonNull String packageName) {
            return buildInfo(packageName, isThemedPackage(packageName));
        }

        @Nullable @Override
        public IconPack parse(@NonNull IconPackInfo info) {
            return parseIconPack(info);
        }
    };
    /** Resources kept per pack APK in use: the global, the pinned and a picked icon's pack or two. */
    private static final int MAX_PACK_RESOURCES = 4;
    private final IconPackResourcesCache<Resources> packResources =
        new IconPackResourcesCache<>(MAX_PACK_RESOURCES);

    private static volatile IconPackRepository instance;

    /**
     * The process-wide repository every icon surface shares (the catalogue, the dock's pinned
     * icons, the notification cards), so a pack's appfilter is parsed once and its resources
     * loaded once rather than once per surface. Thread-safe: the parsed packs and the resources
     * cache each guard themselves.
     */
    @NonNull
    public static IconPackRepository getInstance(@NonNull Context context) {
        IconPackRepository current = instance;
        if (current != null) return current;
        synchronized (IconPackRepository.class) {
            if (instance == null) instance = new IconPackRepository(context);
            return instance;
        }
    }

    /**
     * A private repository with caches of its own. Only for discovery-only callers and tests;
     * anything that loads icons uses {@link #getInstance}.
     */
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

    /** A pack lookup's answer: the pack, or none, or "still being read". */
    public static final class Lookup {
        static final Lookup NONE = new Lookup(null, false);
        static final Lookup PENDING = new Lookup(null, true);

        /** The parsed pack; null when there is none, or while {@link #pending}. */
        @Nullable public final IconPack pack;
        /**
         * The pack is being parsed off the main thread, which asked: anything drawn from this
         * answer is a stand-in to show now and never keep. The listener set with
         * {@link #setPackLandedListener} is told when the parse lands.
         */
        public final boolean pending;

        Lookup(@Nullable IconPack pack, boolean pending) {
            this.pack = pack;
            this.pending = pending;
        }
    }

    /**
     * {@code packageName}'s parsed pack. Off the main thread it parses (slow the first time and
     * after an upgrade), or waits for the thread already parsing it. On the main thread it never
     * parses and never waits: a pack not parsed yet answers {@link Lookup#pending} and is parsed
     * in the background.
     */
    @NonNull
    public Lookup lookupIconPack(@Nullable String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return Lookup.NONE;
        if (!Looper.getMainLooper().isCurrentThread()) {
            IconPack pack = parsedPacks.get(packageName, parseSource);
            return pack == null ? Lookup.NONE : new Lookup(pack, false);
        }
        ParsedIconPackCache.Answer answer = parsedPacks.getNow(packageName, parseSource,
            PARSE_EXECUTOR, this::onPackLanded);
        if (answer.pending) return Lookup.PENDING;
        return answer.pack == null ? Lookup.NONE : new Lookup(answer.pack, false);
    }

    /**
     * {@code packageName}'s parsed pack, or null when it is not installed, cannot be read, or (on
     * the main thread) is still being parsed. See {@link #lookupIconPack}.
     */
    @Nullable
    public IconPack loadIconPack(@Nullable String packageName) {
        return lookupIconPack(packageName).pack;
    }

    /**
     * Who redraws when a pack the main thread was told is pending lands: the catalogue provider,
     * which has every surface drop its stand-ins. Runs on the parse thread.
     */
    public void setPackLandedListener(@Nullable Runnable listener) {
        packLandedListener = listener;
    }

    private void onPackLanded() {
        Runnable listener = packLandedListener;
        if (listener != null) listener.run();
    }

    @NonNull
    private static ExecutorService newIdleFriendlyExecutor() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            0, 1, 15L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @Nullable
    private IconPack parseIconPack(@NonNull IconPackInfo info) {
        String packageName = info.packageName;
        XmlResourceParser appfilterRes = null;
        XmlResourceParser drawableRes = null;
        InputStream appfilterStream = null;
        InputStream drawableStream = null;
        try {
            Resources resources = packResources(packageName);
            if (resources == null) return null;
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

            return IconPackXmlParser.parse(info, appfilter, drawable);
        } catch (Exception ignored) {
            return null;
        } finally {
            closeQuietly(appfilterRes);
            closeQuietly(drawableRes);
            closeQuietly(appfilterStream);
            closeQuietly(drawableStream);
        }
    }

    /**
     * Drops the pack resources and has every pack's installed version checked again on its next
     * use. A pack that did not change keeps its parse: parsing it again only reopened, for
     * seconds with a large pack, the window in which the main thread draws stand-ins.
     */
    public void clearCache() {
        parsedPacks.expire();
        packResources.clear();
    }

    /**
     * The resources of {@code packageName} as installed now, loaded once per APK and reused for
     * every drawable taken from it; null when the pack is not installed or cannot be read. See
     * {@link IconPackResourcesCache}.
     */
    @Nullable
    public Resources packResources(@Nullable String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return null;
        String apkPath;
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            apkPath = info.sourceDir == null ? "" : info.sourceDir;
        } catch (PackageManager.NameNotFoundException | RuntimeException notInstalled) {
            apkPath = null;
        }
        return packResources.get(packageName, apkPath, name -> {
            try {
                return context.createPackageContext(name, Context.CONTEXT_IGNORE_SECURITY)
                    .getResources();
            } catch (PackageManager.NameNotFoundException | RuntimeException unreadable) {
                return null;
            }
        });
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
