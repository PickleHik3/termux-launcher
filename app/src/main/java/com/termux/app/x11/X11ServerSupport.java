package com.termux.app.x11;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Whether this install can run the display at all: the build carries the server
 * ({@code BuildConfig.X11_SERVER}) and the APK has {@code libXlorie.so} for the ABI Android
 * installed it with. The library ships for arm64-v8a and x86_64 only, so a 32-bit phone gets a
 * universal or split APK without it, and loading the display there would throw
 * UnsatisfiedLinkError. Everything that offers or starts the display asks here, not the constant.
 *
 * <p>Libraries are packaged uncompressed inside the APK (useLegacyPackaging false), so
 * {@code nativeLibraryDir} is normally empty and the APK's own entries are the signal; the
 * directory is still checked first for installs that extracted them.
 */
public final class X11ServerSupport {

    static final String LIB_NAME = "libXlorie.so";

    private static volatile Boolean sAvailable;

    private X11ServerSupport() {}

    public static boolean isAvailable(@NonNull Context context) {
        if (!com.termux.BuildConfig.X11_SERVER) return false;
        Boolean cached = sAvailable;
        if (cached != null) return cached;
        Context app = context.getApplicationContext();
        boolean result = detect(app != null ? app : context);
        sAvailable = result;
        return result;
    }

    private static boolean detect(@NonNull Context context) {
        try {
            ApplicationInfo info = context.getApplicationInfo();
            if (info.nativeLibraryDir != null
                    && new File(info.nativeLibraryDir, LIB_NAME).isFile()) {
                return true;
            }
            Set<String> abisWithLibs = new HashSet<>();
            Set<String> abisWithServer = new HashSet<>();
            scan(info.sourceDir, abisWithLibs, abisWithServer);
            if (info.splitSourceDirs != null) {
                for (String split : info.splitSourceDirs) scan(split, abisWithLibs, abisWithServer);
            }
            return decide(Build.SUPPORTED_ABIS, abisWithLibs, abisWithServer);
        } catch (Throwable t) {
            // Unknown is treated as absent: a missing display entry beats a crashing home screen.
            return false;
        }
    }

    /**
     * Android installs the libraries of the first of the device's ABIs for which the package has
     * any; the server is available when that ABI's set includes it.
     */
    static boolean decide(@Nullable String[] supportedAbis, @NonNull Set<String> abisWithLibs,
                          @NonNull Set<String> abisWithServer) {
        if (supportedAbis == null) return false;
        for (String abi : supportedAbis) {
            if (abisWithLibs.contains(abi) || abisWithServer.contains(abi)) {
                return abisWithServer.contains(abi);
            }
        }
        return false;
    }

    private static void scan(@Nullable String apkPath, Set<String> abisWithLibs,
                             Set<String> abisWithServer) {
        if (apkPath == null) return;
        try (ZipFile zip = new ZipFile(apkPath)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith("lib/")) continue;
                int slash = name.indexOf('/', 4);
                if (slash < 0) continue;
                String abi = name.substring(4, slash);
                abisWithLibs.add(abi);
                if (name.endsWith("/" + LIB_NAME)) abisWithServer.add(abi);
            }
        } catch (Exception ignored) {
            // An unreadable split just contributes nothing.
        }
    }
}
