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
import android.os.Handler;
import android.os.HandlerThread;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
    static String chooseEncoderMime() {
        return chooseMime();
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
     * The work runs on its own looper thread (the drawable's end callback needs one); this call
     * blocks until it is done and reports progress to {@code progress} on the calling thread.
     */
    @WorkerThread
    @Nullable
    public static Output convert(@NonNull File gif, @NonNull File out, int maxSide, long expectedMs,
                                 @Nullable Progress progress, @NonNull AtomicBoolean cancel) {
        return convert(gif, out, maxSide, expectedMs, 0, progress, cancel);
    }

    /**
     * As above at {@code rateHz} frames per second: the drawable's current frame is drawn onto the
     * encoder surface every 1/rate of GIF time, not on every GIF frame. 0 means {@link #FPS}.
     */
    @WorkerThread
    @Nullable
    public static Output convert(@NonNull File gif, @NonNull File out, int maxSide, long expectedMs,
                                 int rateHz, @Nullable Progress progress, @NonNull AtomicBoolean cancel) {
        int rate = rateHz > 0 ? rateHz : FPS;
        String mime = chooseMime();
        Output result = run(gif, out, mime, maxSide, expectedMs, rate, progress, cancel);
        if (result == null && MIME_HEVC.equals(mime) && !cancel.get()) {
            result = run(gif, out, MIME_AVC, maxSide, expectedMs, rate, progress, cancel);
        }
        if (result == null) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
        }
        return result;
    }

    @Nullable
    private static Output run(@NonNull File gif, @NonNull File out, @NonNull String mime, int maxSide,
                              long expectedMs, int rate, @Nullable Progress progress, @NonNull AtomicBoolean cancel) {
        HandlerThread thread = new HandlerThread("GifToVideo");
        thread.start();
        try {
            Session s = new Session(new Handler(thread.getLooper()), gif, out, mime, maxSide, expectedMs, rate, cancel);
            s.mHandler.post(s::begin);
            long deadline = SystemClock.elapsedRealtime() + MAX_MS + 20_000L;
            try {
                while (!s.mDone.await(100L, TimeUnit.MILLISECONDS)) {
                    if (progress != null) progress.onProgress(s.mFraction);
                    if (SystemClock.elapsedRealtime() > deadline) {
                        cancel.set(true);
                        s.mDone.await(5L, TimeUnit.SECONDS);
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancel.set(true);
                s.mDone.await(5L, TimeUnit.SECONDS);
            }
            if (progress != null && s.mResult != null) progress.onProgress(1f);
            return s.mResult;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            thread.quitSafely();
        }
    }

    /** One encode, stepped on the handler's thread so the drawable's callbacks can be delivered. */
    private static final class Session {
        final Handler mHandler;
        final CountDownLatch mDone = new CountDownLatch(1);
        volatile float mFraction;
        volatile Output mResult;

        private final File mGif, mOut;
        private final String mMime;
        private final int mMaxSide;
        private final long mExpectedMs;
        private final int mRate;
        private final long mFrameNs;
        private final AtomicBoolean mCancel;
        private final AtomicBoolean mEnded = new AtomicBoolean();
        private final MediaCodec.BufferInfo mInfo = new MediaCodec.BufferInfo();
        private final int[] mSize = new int[2];

        private MediaCodec mCodec;
        private Surface mInput;
        private MediaMuxer mMuxer;
        private AnimatedImageDrawable mDrawable;
        private boolean mMuxerStarted;
        private boolean mOk;
        private int mTrack = -1;
        private long mStart, mNext, mCapMs;
        private long mFirstPtsUs = -1L, mLastPtsUs;
        private int mExtra = 1;   // one more frame after the end

        Session(@NonNull Handler handler, @NonNull File gif, @NonNull File out, @NonNull String mime, int maxSide,
                long expectedMs, int rate, @NonNull AtomicBoolean cancel) {
            mHandler = handler;
            mGif = gif;
            mOut = out;
            mMime = mime;
            mMaxSide = maxSide;
            mExpectedMs = expectedMs;
            mRate = rate;
            mFrameNs = 1_000_000_000L / rate;
            mCancel = cancel;
        }

        void begin() {
            try {
                Drawable d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(mGif), (decoder, info, src) -> {
                    int[] s = encodedSize(info.getSize().getWidth(), info.getSize().getHeight(), mMaxSide);
                    mSize[0] = s[0];
                    mSize[1] = s[1];
                    decoder.setTargetSize(s[0], s[1]);
                });
                if (!(d instanceof AnimatedImageDrawable)) {
                    Log.w(TAG, "not an animated GIF");
                    finish();
                    return;
                }
                mDrawable = (AnimatedImageDrawable) d;
                mDrawable.setRepeatCount(0);
                // Registered before start(); delivered on this thread's looper between steps.
                mDrawable.registerAnimationCallback(new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable who) {
                        mEnded.set(true);
                    }
                });
                MediaFormat format = MediaFormat.createVideoFormat(mMime, mSize[0], mSize[1]);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate(mSize[0], mSize[1]));
                format.setInteger(MediaFormat.KEY_FRAME_RATE, mRate);
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                mCodec = MediaCodec.createEncoderByType(mMime);
                mCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mInput = mCodec.createInputSurface();
                mCodec.start();
                mMuxer = new MediaMuxer(mOut.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                mCapMs = Math.min(MAX_MS, mExpectedMs > 0L ? mExpectedMs + END_SLACK_MS : MAX_MS);
                mStart = SystemClock.elapsedRealtimeNanos();
                mNext = mStart;
                mDrawable.start();
                mHandler.post(this::step);
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "could not convert the GIF with " + mMime, e);
                finish();
            }
        }

        /** One frame: draw, drain, then schedule the next step or finish. */
        void step() {
            try {
                if (mCancel.get()) {
                    finish();
                    return;
                }
                long elapsedMs = (SystemClock.elapsedRealtimeNanos() - mStart) / 1_000_000L;
                if (elapsedMs > mCapMs) {
                    complete();
                    return;
                }
                Canvas canvas = mInput.lockHardwareCanvas();
                try {
                    canvas.drawColor(0xff000000);
                    mDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                    mDrawable.draw(canvas);
                } finally {
                    mInput.unlockCanvasAndPost(canvas);
                }
                float f = mExpectedMs > 0L ? elapsedMs / (float) mExpectedMs : elapsedMs / (float) mCapMs;
                mFraction = Math.max(0f, Math.min(0.99f, f));
                drain(0L);
                if (mEnded.get() && --mExtra < 0) {
                    complete();
                    return;
                }
                mNext += mFrameNs;
                long wait = (mNext - SystemClock.elapsedRealtimeNanos()) / 1_000_000L;
                if (wait < -100L) mNext = SystemClock.elapsedRealtimeNanos();   // fell behind: do not burst
                mHandler.postDelayed(this::step, Math.max(0L, wait));
            } catch (RuntimeException e) {
                Log.w(TAG, "could not convert the GIF with " + mMime, e);
                finish();
            }
        }

        /** Sends end of stream, drains to it, stops the muxer; sets the result when it is whole. */
        private void complete() {
            try {
                mCodec.signalEndOfInputStream();
                long deadline = SystemClock.elapsedRealtime() + 10_000L;
                while (!drain(DRAIN_TIMEOUT_US) && SystemClock.elapsedRealtime() < deadline && !mCancel.get()) {
                    // Keep draining until the end of stream.
                }
                if (!mEosSeen || !mMuxerStarted || mFirstPtsUs < 0L) {
                    Log.w(TAG, "encoder gave no complete video (eos=" + mEosSeen + ", muxer=" + mMuxerStarted + ")");
                } else {
                    long durationMs = (mLastPtsUs - mFirstPtsUs) / 1000L + 1000L / mRate;
                    mMuxerStarted = false;
                    mMuxer.stop();
                    mOk = true;
                    mResult = new Output(mOut, mMime, mSize[0], mSize[1], durationMs);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "could not finish the GIF video with " + mMime, e);
            }
            finish();
        }

        private boolean mEosSeen;

        /** Moves ready encoder output to the muxer; true once the end of stream came through. */
        private boolean drain(long timeoutUs) {
            while (true) {
                int i = mCodec.dequeueOutputBuffer(mInfo, timeoutUs);
                if (i == MediaCodec.INFO_TRY_AGAIN_LATER) return mEosSeen;
                if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (mMuxerStarted) throw new IllegalStateException("format changed twice");
                    mTrack = mMuxer.addTrack(mCodec.getOutputFormat());
                    mMuxer.start();
                    mMuxerStarted = true;
                } else if (i >= 0) {
                    if (writeSample(mCodec, mMuxer, mTrack, i, mInfo, mMuxerStarted)) {
                        if (mFirstPtsUs < 0L) mFirstPtsUs = mInfo.presentationTimeUs;
                        mLastPtsUs = mInfo.presentationTimeUs;
                    }
                    if ((mInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) mEosSeen = true;
                    mCodec.releaseOutputBuffer(i, false);
                    if (mEosSeen) return true;
                }
            }
        }

        /** Releases everything, deletes the file on failure, and wakes the caller. */
        private void finish() {
            if (mCodec != null) {
                try {
                    mCodec.stop();
                } catch (RuntimeException ignored) {
                    // Not started or already stopped.
                }
                mCodec.release();
                mCodec = null;
            }
            if (mInput != null) {
                mInput.release();
                mInput = null;
            }
            if (mMuxer != null) {
                try {
                    if (mMuxerStarted) mMuxer.stop();
                } catch (RuntimeException ignored) {
                    // Nothing was written.
                }
                mMuxer.release();
                mMuxer = null;
            }
            if (mDrawable != null) {
                mDrawable.stop();
                mDrawable.clearAnimationCallbacks();
                mDrawable = null;
            }
            if (!mOk) {
                mResult = null;
                //noinspection ResultOfMethodCallIgnored
                mOut.delete();
            }
            mDone.countDown();
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
