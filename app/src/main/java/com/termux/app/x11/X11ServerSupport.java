package com.termux.app.x11;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import dalvik.system.BaseDexClassLoader;

/**
 * Whether this install can run the display at all: the build carries the server
 * ({@code BuildConfig.X11_SERVER}) and the library loader can find {@code libXlorie.so} for the
 * ABI the app runs as. The library ships for arm64-v8a and x86_64 only, so a 32-bit phone gets a
 * universal or split APK without it, and loading the display there throws UnsatisfiedLinkError.
 * Everything that offers or starts the display asks here, not the constant.
 */
public final class X11ServerSupport {

    static final String LIB = "Xlorie";

    private static volatile Boolean sAvailable;

    private X11ServerSupport() {}

    public static boolean isAvailable(@NonNull Context context) {
        if (!com.termux.BuildConfig.X11_SERVER) return false;
        Boolean cached = sAvailable;
        if (cached != null) return cached;
        boolean result = canLoad(context.getClassLoader());
        sAvailable = result;
        return result;
    }

    /**
     * Asks the class loader the same question {@code System.loadLibrary} will: it resolves the
     * app's own ABI, whether the library sits extracted or uncompressed inside the APK. A loader
     * that cannot answer counts as no: a missing display entry beats a crashing home screen.
     */
    static boolean canLoad(@Nullable ClassLoader loader) {
        if (!(loader instanceof BaseDexClassLoader)) return false;
        try {
            return ((BaseDexClassLoader) loader).findLibrary(LIB) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
