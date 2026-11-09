package com.termux.app.chrome.scene;

import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Converts an animated GIF to an MP4 once, at import, so playback uses the hardware decoder
 * instead of decoding the GIF on the CPU every frame (spec decision 9). The GIF is decoded by
 * {@link ImageDecoder} and drawn into the encoder's input surface at 30 fps while its own clock
 * advances; the encoder's output goes into a {@link MediaMuxer}. One loop is encoded; the end is
 * the drawable's {@code onAnimationEnd}, with the GIF's length plus a margin as the backstop. Call
 * on a worker thread: it blocks for about the length of the GIF.
 */
public final class GifToVideo {

    private static final String TAG = "GifToVideo";
    static final String MIME_AVC = MediaFormat.MIMETYPE_VIDEO_AVC;
    static final String MIME_HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC;
    static final int FPS = 30;
    private static final long FRAME_NS = 1_000_000_000L / FPS;
    private static final long MAX_MS = 60_000L;
    /** Slack past the GIF's own length before the loop is called over when no end callback came. */
    private static final long END_SLACK_MS = 1_500L;
    private static final long DRAIN_TIMEOUT_US = 10_000L;

    private GifToVideo() {}

    /** Called with the fraction done, 0..1. */
    public interface Progress {
        void onProgress(float fraction);
    }

    /** The converted file and what it holds. */
    public static final class Output {
        @NonNull public final File file;
        @NonNull public final String mime;
        public final int width, height;
        public final long durationMs;

        Output(@NonNull File file, @NonNull String mime, int width, int height, long durationMs) {
            this.file = file;
            this.mime = mime;
            this.width = width;
            this.height = height;
            this.durationMs = durationMs;
        }
    }

    // ---- pure parts ----------------------------------------------------------------------

    /**
     * The encoded size: the GIF's size scaled down so the longer side is at most {@code maxSide},
     * both sides rounded down to even, at least 2.
     */
    @NonNull
    static int[] encodedSize(int w, int h, int maxSide) {
        int longest = Math.max(w, h);
        float k = longest > maxSide && maxSide > 0 ? maxSide / (float) longest : 1f;
        int ew = Math.round(w * k) & ~1;
        int eh = Math.round(h * k) & ~1;
        return new int[] {Math.max(2, ew), Math.max(2, eh)};
    }

    /** About 6 Mbps for 1080p, in proportion to the pixels, kept between 1.5 and 12 Mbps. */
    static int bitrate(int w, int h) {
        long bps = 6_000_000L * w * h / (1920L * 1080L);
        return (int) Math.max(1_500_000L, Math.min(12_000_000L, bps));
    }

    /** HEVC only when a hardware HEVC encoder exists among {@code hevcEncoderNames}; else AVC. */
    @NonNull
    static String chooseMime(@NonNull List<String> hevcEncoderNames) {
        return Boolean.TRUE.equals(VideoHint.hardwareFor(hevcEncoderNames)) ? MIME_HEVC : MIME_AVC;
    }

    @NonNull
    private static String chooseMime() {
        List<String> names = new ArrayList<>();
        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (!info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (type.equalsIgnoreCase(MIME_HEVC)) {
                        names.add(info.getName());
                        break;
                    }
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "could not list the encoders", e);
        }
        return chooseMime(names);
    }

    // ---- conversion ----------------------------------------------------------------------

    /**
     * Converts {@code gif} into {@code out} (a .mp4 path). Returns the result, or null on any
     * failure (logged; {@code out} is deleted). {@code maxSide} caps the longer side;
     * {@code expectedMs} is the GIF's length when known (<= 0 when not); {@code cancel} stops it.
     */
    @WorkerThread
    @Nullable
    public static Output convert(@NonNull File gif, @NonNull File out, int maxSide, long expectedMs,
                                 @Nullable Progress progress, @NonNull AtomicBoolean cancel) {
        String mime = chooseMime();
        Output result = null;
        try {
            result = encode(gif, out, mime, maxSide, expectedMs, progress, cancel);
            if (result == null && MIME_HEVC.equals(mime) && !cancel.get()) {
                result = encode(gif, out, MIME_AVC, maxSide, expectedMs, progress, cancel);
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "GIF conversion failed", e);
        }
        if (result == null) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
        }
        return result;
    }

    @Nullable
    private static Output encode(@NonNull File gif, @NonNull File out, @NonNull String mime, int maxSide,
                                 long expectedMs, @Nullable Progress progress, @NonNull AtomicBoolean cancel) {
        MediaCodec codec = null;
        Surface input = null;
        MediaMuxer muxer = null;
        AnimatedImageDrawable drawable = null;
        boolean muxerStarted = false;
        boolean ok = false;
        try {
            final int[] size = new int[2];
            Drawable d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(gif), (decoder, info, src) -> {
                int[] s = encodedSize(info.getSize().getWidth(), info.getSize().getHeight(), maxSide);
                size[0] = s[0];
                size[1] = s[1];
                decoder.setTargetSize(s[0], s[1]);
            });
            if (!(d instanceof AnimatedImageDrawable)) {
                Log.w(TAG, "not an animated GIF");
                return null;
            }
            drawable = (AnimatedImageDrawable) d;
            drawable.setRepeatCount(0);
            final AtomicBoolean ended = new AtomicBoolean();
            drawable.registerAnimationCallback(new Animatable2.AnimationCallback() {
                @Override
                public void onAnimationEnd(Drawable who) {
                    ended.set(true);
                }
            });

            MediaFormat format = MediaFormat.createVideoFormat(mime, size[0], size[1]);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate(size[0], size[1]));
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            codec = MediaCodec.createEncoderByType(mime);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            input = codec.createInputSurface();
            codec.start();
            muxer = new MediaMuxer(out.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            long capMs = Math.min(MAX_MS, expectedMs > 0L ? expectedMs + END_SLACK_MS : MAX_MS);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int track = -1;
            long start = SystemClock.elapsedRealtimeNanos();
            long next = start;
            long firstPtsUs = -1L;
            long lastPtsUs = 0L;
            int extra = 1;   // one more frame after the end
            drawable.start();
            while (!cancel.get()) {
                long elapsedMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000L;
                if (elapsedMs > capMs) break;
                Canvas canvas = input.lockHardwareCanvas();
                try {
                    canvas.drawColor(0xff000000);
                    drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                    drawable.draw(canvas);
                } finally {
                    input.unlockCanvasAndPost(canvas);
                }
                if (progress != null) {
                    float f = expectedMs > 0L ? elapsedMs / (float) expectedMs : elapsedMs / (float) capMs;
                    progress.onProgress(Math.max(0f, Math.min(0.99f, f)));
                }
                // Drain what is ready; the track is added when the encoder reports its format.
                while (true) {
                    int i = codec.dequeueOutputBuffer(info, 0L);
                    if (i == MediaCodec.INFO_TRY_AGAIN_LATER) break;
                    if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (muxerStarted) throw new IllegalStateException("format changed twice");
                        track = muxer.addTrack(codec.getOutputFormat());
                        muxer.start();
                        muxerStarted = true;
                    } else if (i >= 0) {
                        if (writeSample(codec, muxer, track, i, info, muxerStarted)) {
                            if (firstPtsUs < 0L) firstPtsUs = info.presentationTimeUs;
                            lastPtsUs = info.presentationTimeUs;
                        }
                        codec.releaseOutputBuffer(i, false);
                    }
                }
                if (ended.get() && --extra < 0) break;
                next += FRAME_NS;
                long wait = (next - SystemClock.elapsedRealtimeNanos()) / 1_000_000L;
                if (wait > 0L) {
                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        cancel.set(true);
                    }
                } else if (wait < -100L) {
                    next = SystemClock.elapsedRealtimeNanos();   // fell behind: do not burst
                }
            }
            if (cancel.get()) return null;
            codec.signalEndOfInputStream();
            long deadline = SystemClock.elapsedRealtime() + 10_000L;
            boolean eos = false;
            while (!eos && SystemClock.elapsedRealtime() < deadline && !cancel.get()) {
                int i = codec.dequeueOutputBuffer(info, DRAIN_TIMEOUT_US);
                if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        track = muxer.addTrack(codec.getOutputFormat());
                        muxer.start();
                        muxerStarted = true;
                    }
                } else if (i >= 0) {
                    if (writeSample(codec, muxer, track, i, info, muxerStarted)) {
                        if (firstPtsUs < 0L) firstPtsUs = info.presentationTimeUs;
                        lastPtsUs = info.presentationTimeUs;
                    }
                    eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(i, false);
                }
            }
            if (!eos || !muxerStarted || firstPtsUs < 0L) {
                Log.w(TAG, "encoder gave no complete video (eos=" + eos + ", muxer=" + muxerStarted + ")");
                return null;
            }
            long durationMs = (lastPtsUs - firstPtsUs) / 1000L + 1000L / FPS;
            muxerStarted = false;
            muxer.stop();
            ok = true;
            if (progress != null) progress.onProgress(1f);
            return new Output(out, mime, size[0], size[1], durationMs);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "could not convert the GIF with " + mime, e);
            return null;
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (RuntimeException ignored) {
                    // Not started or already stopped.
                }
                codec.release();
            }
            if (input != null) input.release();
            if (muxer != null) {
                try {
                    if (muxerStarted) muxer.stop();
                } catch (RuntimeException ignored) {
                    // Nothing was written.
                }
                muxer.release();
            }
            if (drawable != null) {
                drawable.stop();
                drawable.clearAnimationCallbacks();
            }
            if (!ok) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
            }
        }
    }

    /** Writes the output buffer to the muxer unless it is codec config or empty; true when written. */
    private static boolean writeSample(@NonNull MediaCodec codec, @NonNull MediaMuxer muxer, int track, int index,
                                       @NonNull MediaCodec.BufferInfo info, boolean muxerStarted) {
        if (!muxerStarted || info.size <= 0 || (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) return false;
        ByteBuffer buf = codec.getOutputBuffer(index);
        if (buf == null) return false;
        buf.position(info.offset);
        buf.limit(info.offset + info.size);
        muxer.writeSampleData(track, buf, info);
        return true;
    }
}
