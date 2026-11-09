package com.termux.app.chrome.scene;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Re-encodes a stored video at a lower frame rate (15 or 30) so the hardware decoder runs at the
 * draw rate instead of the clip's. The video track is decoded by a hardware decoder straight onto
 * the encoder's input surface (no copy through the CPU); frames are dropped by presentation time
 * ({@link #keep}); audio is not carried. The result is an MP4 of the same size with the source's
 * rotation hint. Call on a worker thread; it blocks for about the clip's length or less.
 */
public final class VideoTranscode {

    private static final String TAG = "VideoTranscode";
    private static final long DEQUEUE_US = 10_000L;
    /** No frame moved for this long: the codecs are stuck. */
    private static final long STALL_MS = 20_000L;
    private static final int MAX_BITRATE = 20_000_000;

    private VideoTranscode() {}

    // ---- pure parts ----------------------------------------------------------------------

    /** The gap between kept frames, in microseconds. */
    static long stepUs(int targetFps) {
        return 1_000_000L / Math.max(1, targetFps);
    }

    /**
     * True when a decoded frame at {@code ptsUs} is kept: it is due ({@code nextKeepUs}), allowing
     * an eighth of a step of timestamp jitter so a 60 fps clip with rounded timestamps still gives 30.
     */
    static boolean keep(long ptsUs, long nextKeepUs, long stepUs) {
        return ptsUs + stepUs / 8L >= nextKeepUs;
    }

    /** The due time after keeping a frame at {@code ptsUs}: one step on, never behind the frame (a gap does not burst). */
    static long advance(long ptsUs, long nextKeepUs, long stepUs) {
        long next = nextKeepUs + stepUs;
        return next <= ptsUs ? ptsUs + stepUs : next;
    }

    /** GifToVideo's rule for the area (about 6 Mbps at 1080p30), in proportion to the rate, 1.5 to 20 Mbps. */
    static int bitrate(int w, int h, int targetFps) {
        long bps = (long) GifToVideo.bitrate(w, h) * Math.max(1, targetFps) / 30L;
        return (int) Math.max(1_500_000L, Math.min(MAX_BITRATE, bps));
    }

    // ---- conversion ----------------------------------------------------------------------

    /**
     * Converts {@code src} into {@code out} (a .mp4 path) at {@code targetFps}. Null on failure or
     * cancel ({@code out} is deleted); progress is the source position over its length.
     */
    @WorkerThread
    @Nullable
    public static GifToVideo.Output convert(@NonNull File src, @NonNull File out, int targetFps,
                                            @Nullable GifToVideo.Progress progress, @NonNull AtomicBoolean cancel) {
        String mime = GifToVideo.chooseEncoderMime();
        GifToVideo.Output result = run(src, out, mime, targetFps, progress, cancel);
        if (result == null && GifToVideo.MIME_HEVC.equals(mime) && !cancel.get()) {
            result = run(src, out, GifToVideo.MIME_AVC, targetFps, progress, cancel);
        }
        if (result == null) {
            //noinspection ResultOfMethodCallIgnored
            out.delete();
        } else if (progress != null) {
            progress.onProgress(1f);
        }
        return result;
    }

    @Nullable
    private static GifToVideo.Output run(@NonNull File src, @NonNull File out, @NonNull String encMime,
                                         int targetFps, @Nullable GifToVideo.Progress progress,
                                         @NonNull AtomicBoolean cancel) {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        MediaMuxer muxer = null;
        Surface input = null;
        boolean muxerStarted = false;
        boolean ok = false;
        long firstOutUs = -1L, lastOutUs = 0L;
        int w = 0, h = 0;
        try {
            extractor.setDataSource(src.getPath());
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String m = f.getString(MediaFormat.KEY_MIME);
                if (m != null && m.startsWith("video/")) {
                    track = i;
                    format = f;
                    break;
                }
            }
            if (track < 0 || format == null) return null;
            extractor.selectTrack(track);
            String decMime = format.getString(MediaFormat.KEY_MIME);
            w = Math.max(2, format.getInteger(MediaFormat.KEY_WIDTH) & ~1);
            h = Math.max(2, format.getInteger(MediaFormat.KEY_HEIGHT) & ~1);
            int rotation = format.containsKey(MediaFormat.KEY_ROTATION) ? format.getInteger(MediaFormat.KEY_ROTATION) : 0;
            long durationUs = format.containsKey(MediaFormat.KEY_DURATION) ? format.getLong(MediaFormat.KEY_DURATION) : 0L;

            MediaFormat enc = MediaFormat.createVideoFormat(encMime, w, h);
            enc.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            enc.setInteger(MediaFormat.KEY_BIT_RATE, bitrate(w, h, targetFps));
            enc.setInteger(MediaFormat.KEY_FRAME_RATE, targetFps);
            enc.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
            encoder = MediaCodec.createEncoderByType(encMime);
            encoder.configure(enc, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            input = encoder.createInputSurface();
            encoder.start();

            decoder = MediaCodec.createDecoderByType(decMime);
            decoder.configure(format, input, null, 0);
            decoder.start();

            muxer = new MediaMuxer(out.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            if (rotation != 0) muxer.setOrientationHint(rotation);

            MediaCodec.BufferInfo decInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo encInfo = new MediaCodec.BufferInfo();
            long step = stepUs(targetFps);
            long nextKeep = Long.MIN_VALUE;
            long baseUs = -1L;
            boolean inputDone = false, decoderDone = false, encoderDone = false;
            int muxTrack = -1;
            long lastMove = SystemClock.elapsedRealtime();
            long lastReport = 0L;

            while (!encoderDone) {
                if (cancel.get()) return null;
                if (SystemClock.elapsedRealtime() - lastMove > STALL_MS) {
                    Log.w(TAG, "no frame moved for " + STALL_MS + " ms with " + encMime);
                    return null;
                }
                boolean moved = false;
                if (!inputDone) {
                    int i = decoder.dequeueInputBuffer(0L);
                    if (i >= 0) {
                        ByteBuffer buf = decoder.getInputBuffer(i);
                        int n = buf == null ? -1 : extractor.readSampleData(buf, 0);
                        if (n < 0) {
                            decoder.queueInputBuffer(i, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long t = extractor.getSampleTime();
                            decoder.queueInputBuffer(i, 0, n, t, 0);
                            extractor.advance();
                            if (progress != null && durationUs > 0L && t >= 0L) {
                                long now = SystemClock.elapsedRealtime();
                                if (now - lastReport > 100L) {
                                    lastReport = now;
                                    progress.onProgress(Math.max(0f, Math.min(0.99f, t / (float) durationUs)));
                                }
                            }
                        }
                        moved = true;
                    }
                }
                if (!decoderDone) {
                    int i = decoder.dequeueOutputBuffer(decInfo, 0L);
                    if (i >= 0) {
                        boolean eos = (decInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        boolean render = false;
                        long pts = decInfo.presentationTimeUs;
                        if (decInfo.size > 0) {
                            if (baseUs < 0L) {
                                baseUs = pts;
                                nextKeep = pts;
                            }
                            if (keep(pts, nextKeep, step)) {
                                render = true;
                                nextKeep = advance(pts, nextKeep, step);
                            }
                        }
                        if (render) decoder.releaseOutputBuffer(i, (pts - baseUs) * 1000L);
                        else decoder.releaseOutputBuffer(i, false);
                        if (eos) {
                            encoder.signalEndOfInputStream();
                            decoderDone = true;
                        }
                        moved = true;
                    }
                    // INFO_OUTPUT_FORMAT_CHANGED / BUFFERS_CHANGED need nothing: the output is a surface.
                }
                int e = encoder.dequeueOutputBuffer(encInfo, moved ? 0L : DEQUEUE_US);
                if (e == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) throw new IllegalStateException("format changed twice");
                    muxTrack = muxer.addTrack(encoder.getOutputFormat());
                    muxer.start();
                    muxerStarted = true;
                    moved = true;
                } else if (e >= 0) {
                    boolean config = (encInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                    if (!config && encInfo.size > 0 && muxerStarted) {
                        ByteBuffer buf = encoder.getOutputBuffer(e);
                        if (buf != null) {
                            buf.position(encInfo.offset);
                            buf.limit(encInfo.offset + encInfo.size);
                            muxer.writeSampleData(muxTrack, buf, encInfo);
                            if (firstOutUs < 0L) firstOutUs = encInfo.presentationTimeUs;
                            lastOutUs = encInfo.presentationTimeUs;
                        }
                    }
                    if ((encInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) encoderDone = true;
                    encoder.releaseOutputBuffer(e, false);
                    moved = true;
                }
                if (moved) lastMove = SystemClock.elapsedRealtime();
            }
            if (!muxerStarted || firstOutUs < 0L) {
                Log.w(TAG, "encoder gave no video with " + encMime);
                return null;
            }
            muxerStarted = false;
            muxer.stop();
            ok = true;
            long durationMs = (lastOutUs - firstOutUs) / 1000L + 1000L / Math.max(1, targetFps);
            return new GifToVideo.Output(out, encMime, w, h, durationMs);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "could not convert " + src.getName() + " with " + encMime, e);
            return null;
        } finally {
            if (decoder != null) {
                try {
                    decoder.stop();
                } catch (RuntimeException ignored) {
                    // Not started.
                }
                decoder.release();
            }
            if (encoder != null) {
                try {
                    encoder.stop();
                } catch (RuntimeException ignored) {
                    // Not started.
                }
                encoder.release();
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
            extractor.release();
            if (!ok) {
                //noinspection ResultOfMethodCallIgnored
                out.delete();
            }
        }
    }
}
