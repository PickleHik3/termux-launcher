package com.termux.app.chrome.scene;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/**
 * The one-line description of an imported video or GIF (spec decision 9), built from plain values
 * so it can be tested without a device. It describes; it never recommends (the picker shows the
 * recommended-format line itself).
 */
public final class VideoHint {

    private VideoHint() {}

    /** What was read from the file; a missing field is {@code <= 0} (or null for text). */
    public static final class Fields {
        @Nullable public String mime;
        public int width, height;
        public float frameRate;
        public long durationMs;
        public long sizeBytes;
        /** True when a hardware decoder exists for the mime, false when only software ones do, null when unknown. */
        @Nullable public Boolean hardware;
    }

    /** The hint, parts joined with a middle dot; fields that are missing are left out. */
    @NonNull
    public static String describe(@NonNull Fields f) {
        boolean gif = "image/gif".equals(f.mime);
        StringBuilder out = new StringBuilder();
        append(out, codecName(f.mime));
        if (f.width > 0 && f.height > 0) append(out, f.width + "×" + f.height);
        if (!gif && f.frameRate > 0f) append(out, rate(f.frameRate) + " fps");
        if (f.durationMs > 0L) append(out, duration(f.durationMs));
        if (f.sizeBytes > 0L) append(out, size(f.sizeBytes));
        if (gif) {
            append(out, "decodes on the CPU");
        } else if (f.hardware != null) {
            append(out, f.hardware ? "hardware decoded" : "software decoded on this phone, expect more battery");
        }
        return out.toString();
    }

    private static void append(@NonNull StringBuilder out, @NonNull String part) {
        if (out.length() > 0) out.append(" · ");
        out.append(part);
    }

    @NonNull
    static String codecName(@Nullable String mime) {
        if (mime == null || mime.isEmpty()) return "Video";
        switch (mime.toLowerCase(Locale.ROOT)) {
            case "video/hevc": return "H.265";
            case "video/avc": return "H.264";
            case "video/av01": return "AV1";
            case "video/x-vnd.on2.vp9": return "VP9";
            case "video/x-vnd.on2.vp8": return "VP8";
            case "video/mp4v-es": return "MPEG-4";
            case "video/3gpp": return "H.263";
            case "image/gif": return "GIF";
            default:
                int slash = mime.indexOf('/');
                String tail = slash >= 0 ? mime.substring(slash + 1) : mime;
                return tail.isEmpty() ? "Video" : tail.toUpperCase(Locale.ROOT);
        }
    }

    @NonNull
    static String rate(float fps) {
        float rounded = Math.round(fps);
        if (Math.abs(fps - rounded) < 0.05f) return Integer.toString((int) rounded);
        return String.format(Locale.ROOT, "%.1f", fps);
    }

    @NonNull
    static String duration(long ms) {
        long s = Math.max(1L, Math.round(ms / 1000.0));
        if (s < 60L) return s + " s";
        return (s / 60L) + " min " + (s % 60L) + " s";
    }

    @NonNull
    static String size(long bytes) {
        if (bytes < 1_000_000L) return Math.max(1L, Math.round(bytes / 1000.0)) + " KB";
        return Math.round(bytes / 1_000_000.0) + " MB";
    }

    /** A decoder that is not one of Android's own software ones counts as hardware (spec decision 9). */
    static boolean isHardwareName(@Nullable String codecName) {
        if (codecName == null) return false;
        return !codecName.startsWith("OMX.google.") && !codecName.startsWith("c2.android.");
    }

    /** True when any of the decoder names is a hardware one; null when there are none (unknown). */
    @Nullable
    static Boolean hardwareFor(@NonNull List<String> decoderNames) {
        if (decoderNames.isEmpty()) return null;
        for (String name : decoderNames) if (isHardwareName(name)) return Boolean.TRUE;
        return Boolean.FALSE;
    }

    /** Size and length of a GIF, read from its header and frame delays. */
    public static final class GifInfo {
        public final int width, height;
        public final long durationMs;

        GifInfo(int width, int height, long durationMs) {
            this.width = width;
            this.height = height;
            this.durationMs = durationMs;
        }

        /** Null when the stream is not a GIF. A truncated file gives what was read so far. */
        @Nullable
        public static GifInfo read(@NonNull InputStream in) throws IOException {
            byte[] head = new byte[13];
            if (readFully(in, head) < 13) return null;
            if (head[0] != 'G' || head[1] != 'I' || head[2] != 'F') return null;
            int w = (head[6] & 0xff) | ((head[7] & 0xff) << 8);
            int h = (head[8] & 0xff) | ((head[9] & 0xff) << 8);
            int flags = head[10] & 0xff;
            if ((flags & 0x80) != 0) skipFully(in, 3L * (1 << ((flags & 7) + 1)));
            long delayCs = 0;
            int pendingDelay = 0;
            while (true) {
                int block = in.read();
                if (block < 0 || block == 0x3b) break;
                if (block == 0x21) {
                    int label = in.read();
                    if (label < 0) break;
                    if (label == 0xf9) {
                        int size = in.read();
                        if (size < 0) break;
                        byte[] gce = new byte[size];
                        if (readFully(in, gce) < size) break;
                        if (size >= 4) pendingDelay = (gce[1] & 0xff) | ((gce[2] & 0xff) << 8);
                    }
                    if (!skipSubBlocks(in)) break;
                } else if (block == 0x2c) {
                    byte[] desc = new byte[9];
                    if (readFully(in, desc) < 9) break;
                    int lf = desc[8] & 0xff;
                    if ((lf & 0x80) != 0) skipFully(in, 3L * (1 << ((lf & 7) + 1)));
                    if (in.read() < 0) break;
                    if (!skipSubBlocks(in)) break;
                    // A delay under 2 cs plays as 10 cs, as browsers and the platform decoder do.
                    delayCs += pendingDelay < 2 ? 10 : pendingDelay;
                    pendingDelay = 0;
                } else {
                    break;
                }
            }
            return new GifInfo(w, h, delayCs * 10L);
        }

        private static boolean skipSubBlocks(@NonNull InputStream in) throws IOException {
            while (true) {
                int n = in.read();
                if (n < 0) return false;
                if (n == 0) return true;
                if (!skipFully(in, n)) return false;
            }
        }

        private static int readFully(@NonNull InputStream in, @NonNull byte[] buf) throws IOException {
            int got = 0;
            while (got < buf.length) {
                int n = in.read(buf, got, buf.length - got);
                if (n < 0) break;
                got += n;
            }
            return got;
        }

        private static boolean skipFully(@NonNull InputStream in, long n) throws IOException {
            while (n > 0) {
                long s = in.skip(n);
                if (s <= 0) {
                    if (in.read() < 0) return false;
                    s = 1;
                }
                n -= s;
            }
            return true;
        }
    }
}
