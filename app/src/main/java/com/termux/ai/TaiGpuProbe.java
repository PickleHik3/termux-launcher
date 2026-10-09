package com.termux.ai;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The Java side of {@code tai_gpu_probe.c}: whether an OpenCL driver exists and what the first
 * Vulkan device is. Both answers feed {@link TaiPlatformCaps}. Never throws: a phone where the shim
 * cannot load, or a driver that misbehaves, answers {@code null} ("no answer"), and the policy then
 * treats the GPU as unconfirmed rather than failing.
 */
final class TaiGpuProbe {
    private static final String TAG = "TaiGpuProbe";

    /** The first Vulkan physical device: who made it (the vendorID) and what it calls itself. */
    static final class VulkanDevice {
        final int vendorId;
        final long deviceId;
        @NonNull final String name;

        VulkanDevice(int vendorId, long deviceId, @NonNull String name) {
            this.vendorId = vendorId;
            this.deviceId = deviceId;
            this.name = name;
        }

        /** Parses the native {@code "0xVENDOR|0xDEVICE|name"} line; {@code null} when it is malformed. */
        @Nullable
        static VulkanDevice parse(@Nullable String line) {
            if (line == null) return null;
            String[] parts = line.split("\\|", 3);
            if (parts.length < 3) return null;
            try {
                return new VulkanDevice(Integer.decode(parts[0].trim()), Long.decode(parts[1].trim()), parts[2].trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /** The first OpenCL GPU device's name and driver string; either may be empty. */
    static final class OpenClDevice {
        @NonNull final String name;
        @NonNull final String driver;

        OpenClDevice(@NonNull String name, @NonNull String driver) {
            this.name = name;
            this.driver = driver;
        }

        /** Parses the native {@code "name|driver"} line; {@code null} when it is malformed. */
        @Nullable
        static OpenClDevice parse(@Nullable String line) {
            if (line == null) return null;
            String[] parts = line.split("\\|", 2);
            if (parts.length < 2) return null;
            return new OpenClDevice(parts[0].trim(), parts[1].trim());
        }
    }

    private static volatile boolean loadAttempted;
    private static volatile boolean loaded;

    private TaiGpuProbe() {}

    /** {@code true}/{@code false} from the driver, or {@code null} when the native shim is unavailable. */
    @Nullable
    static Boolean openClAvailable() {
        if (!load()) return null;
        try {
            return nativeOpenClAvailable();
        } catch (Throwable t) {
            Log.w(TAG, "OpenCL probe threw", t);
            return null;
        }
    }

    /** The first OpenCL GPU device, or {@code null} when OpenCL has none or the shim is unavailable. */
    @Nullable
    static OpenClDevice openClDevice() {
        if (!load()) return null;
        try {
            return OpenClDevice.parse(nativeOpenClDevice());
        } catch (Throwable t) {
            Log.w(TAG, "OpenCL device probe threw", t);
            return null;
        }
    }

    /** The first Vulkan device, or {@code null} when there is none or the shim is unavailable. */
    @Nullable
    static VulkanDevice vulkanDevice() {
        if (!load()) return null;
        try {
            return VulkanDevice.parse(nativeVulkanDevice());
        } catch (Throwable t) {
            Log.w(TAG, "Vulkan probe threw", t);
            return null;
        }
    }

    private static synchronized boolean load() {
        if (loadAttempted) return loaded;
        loadAttempted = true;
        try {
            System.loadLibrary("tai_xnnpack");
            loaded = true;
        } catch (Throwable t) {
            // UnsatisfiedLinkError on an ABI without the shim (and on the JVM, in unit tests).
            Log.w(TAG, "libtai_xnnpack.so not available; GPU probe skipped", t);
        }
        return loaded;
    }

    private static native boolean nativeOpenClAvailable();

    @Nullable
    private static native String nativeOpenClDevice();

    @Nullable
    private static native String nativeVulkanDevice();
}
