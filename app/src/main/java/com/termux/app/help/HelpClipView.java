package com.termux.app.help;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.termux.app.ReducedMotion;
import java.io.IOException;

/**
 * One recorded gesture, playing on a topic's page while the reader reads the instruction.
 *
 * <p>A card the width of the page, as tall as the recording's own shape, with the panel's corner
 * radius: silent, without controls, and looping from the moment the page appears, so the gesture is
 * simply happening next to the words rather than waiting to be asked for. When the phone is set to
 * play no animations the card holds the first frame instead, and a tap runs the gesture through
 * once.
 *
 * <p>Drawn on a {@link TextureView}, not a surface of its own: the help panel is a document that
 * floats over the launcher inside a scrolling column, and only a view in the hierarchy can be
 * clipped to the rounded card, scrolled with the page and lifted with the panel.
 *
 * <p>The player lives exactly as long as the surface under it. The page's own lifecycle — the
 * panel hidden, the body rebuilt for another page, the view detached — calls {@link #release()},
 * and the surface being taken away releases it too, so nothing decodes for a page nobody is
 * reading. A clip that will not open takes the card away with it and the page reads as it did
 * before; the reason goes to the debug trail, not to the reader.
 */
final class HelpClipView extends FrameLayout {

    private final HelpClips.Clip clip;
    private final TextureView video;
    /** True when the phone is set to play no animations: the card holds a frame, tap to play. */
    private final boolean still;

    private MediaPlayer player;
    private Surface surface;
    /** The size the decoder reports, which overrules the manifest's once it arrives. */
    private int videoWidth;
    private int videoHeight;
    /** A clip that failed once is not asked for again, and says so once. */
    private boolean failed;

    HelpClipView(Context context, HelpStyle style, HelpClips.Clip clip) {
        super(context);
        this.clip = clip;
        this.still = ReducedMotion.isEnabled(context);
        setBackground(style.clipCard());
        // The card's own outline, so the recording's square corners are rounded with the page's.
        setClipToOutline(true);
        setContentDescription(clip.alt == null || clip.alt.isEmpty() ? null : clip.alt);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        video = new TextureView(context);
        video.setOpaque(false);
        video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(@NonNull SurfaceTexture texture,
                                                            int width, int height) {
                surface = new Surface(texture);
                open();
            }

            @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture texture,
                                                              int width, int height) {}

            @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture texture) {
                release();
                return true;
            }

            @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture texture) {}
        });
        addView(video, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        if (still) {
            // The one gesture the card itself answers: play the frame it is holding, once.
            setClickable(true);
            setFocusable(true);
            setOnClickListener(new OnClickListener() {
                @Override public void onClick(View view) {
                    playOnce();
                }
            });
        }
    }

    /**
     * The card is the page's width and the recording's shape. The manifest's crop size answers
     * before the decoder has reported anything, so the page does not reflow once it does.
     */
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int sourceWidth = videoWidth > 0 ? videoWidth : clip.width;
        int sourceHeight = videoHeight > 0 ? videoHeight : clip.height;
        if (width <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            super.onMeasure(widthSpec, heightSpec);
            return;
        }
        int height = Math.max(1, Math.round(width * (float) sourceHeight / sourceWidth));
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    private void open() {
        if (failed || player != null || surface == null) return;
        MediaPlayer opening = new MediaPlayer();
        try (AssetFileDescriptor asset = getContext().getAssets().openFd(clip.assetPath)) {
            opening.setDataSource(asset.getFileDescriptor(), asset.getStartOffset(),
                asset.getLength());
        } catch (IOException | RuntimeException refused) {
            opening.release();
            fail(refused);
            return;
        }
        opening.setSurface(surface);
        // Every clip was recorded without an audio track; the volume is belt to that brace.
        opening.setVolume(0f, 0f);
        opening.setLooping(!still);
        opening.setOnVideoSizeChangedListener((source, width, height) -> {
            if (width <= 0 || height <= 0) return;
            if (width == videoWidth && height == videoHeight) return;
            videoWidth = width;
            videoHeight = height;
            requestLayout();
        });
        opening.setOnErrorListener((source, what, extra) -> {
            fail(new IOException("decoder said " + what + "/" + extra));
            return true;
        });
        opening.setOnPreparedListener(source -> {
            if (player != source) return;
            // A seek is what puts a frame on the surface without playing: the still shows the
            // gesture's starting point rather than a hole in the page.
            if (still) source.seekTo(0);
            else source.start();
        });
        player = opening;
        try {
            opening.prepareAsync();
        } catch (IllegalStateException refused) {
            fail(refused);
        }
    }

    /** Reduced motion: one run of the gesture for the reader who asked to see it. */
    private void playOnce() {
        MediaPlayer playing = player;
        if (playing == null) return;
        try {
            if (playing.isPlaying()) return;
            playing.seekTo(0);
            playing.start();
        } catch (IllegalStateException notReady) {
            HelpLog.d("help clip " + clip.id + ": not ready to play (" + notReady + ")");
        }
    }

    /** Let go of the decoder. Safe at any time, and again afterwards. */
    void release() {
        MediaPlayer going = player;
        player = null;
        if (going != null) {
            going.setOnPreparedListener(null);
            going.setOnErrorListener(null);
            going.setOnVideoSizeChangedListener(null);
            try {
                going.reset();
            } catch (RuntimeException ignored) {
                // A player that never reached a state worth resetting still has to be released.
            }
            going.release();
        }
        Surface held = surface;
        surface = null;
        if (held != null) held.release();
    }

    private void fail(Throwable reason) {
        if (!failed) HelpLog.d("help clip " + clip.id + ": no clip on the page (" + reason + ")");
        failed = true;
        release();
        // The page reads as it did before the clips existed: no card, and no explanation for it.
        setVisibility(GONE);
    }

    @Override protected void onDetachedFromWindow() {
        release();
        super.onDetachedFromWindow();
    }

    @VisibleForTesting
    boolean isPlayerOpen() {
        return player != null;
    }

    @VisibleForTesting
    boolean isStill() {
        return still;
    }

    @VisibleForTesting
    String clipId() {
        return clip.id;
    }
}
