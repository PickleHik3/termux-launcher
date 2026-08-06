package com.termux.app.onboarding;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Plays a bundled screen-recording clip on an onboarding page. The clips are 16:9 and the view
 * measures itself to the same ratio, so the card is filled edge to edge and nothing shows
 * through around the video.
 */
final class OnboardingClipView extends FrameLayout implements TextureView.SurfaceTextureListener {

    private static final String[] CLIP_ASSETS = {
        "onboarding/essentials.webm",
        "onboarding/apps.webm",
        "onboarding/workspace.webm",
    };

    private final TextureView texture;
    @Nullable private final String asset;
    @Nullable private MediaPlayer player;
    private int videoWidth;
    private int videoHeight;

    OnboardingClipView(@NonNull Context context, int scene) {
        super(context);
        asset = scene >= 0 && scene < CLIP_ASSETS.length ? CLIP_ASSETS[scene] : null;

        float density = getResources().getDisplayMetrics().density;
        float radius = 28f * density;

        // Shown only until the first video frame renders.
        GradientDrawable card = new GradientDrawable();
        card.setCornerRadius(radius);
        card.setColor(Color.rgb(12, 25, 37));
        card.setStroke(Math.round(density), Color.argb(95, 130, 239, 239));
        setBackground(card);

        texture = new TextureView(context);
        texture.setAlpha(0f);
        texture.setOpaque(false);
        texture.setSurfaceTextureListener(this);
        addView(texture, new LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        super.onMeasure(widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(Math.round(width * 9f / 16f), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyVideoTransform();
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture,
                                          int width, int height) {
        if (asset == null) return;
        try {
            AssetFileDescriptor descriptor = getContext().getAssets().openFd(asset);
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(descriptor.getFileDescriptor(),
                descriptor.getStartOffset(), descriptor.getLength());
            descriptor.close();
            mediaPlayer.setSurface(new Surface(surfaceTexture));
            mediaPlayer.setLooping(true);
            mediaPlayer.setVolume(0f, 0f);
            mediaPlayer.setOnVideoSizeChangedListener((mp, videoW, videoH) -> {
                videoWidth = videoW;
                videoHeight = videoH;
                applyVideoTransform();
            });
            mediaPlayer.setOnInfoListener((mp, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START)
                    texture.animate().alpha(1f).setDuration(260L).start();
                return false;
            });
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                releasePlayer();
                return true;
            });
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.prepareAsync();
            player = mediaPlayer;
        } catch (Exception ignored) {
            releasePlayer();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture,
                                            int width, int height) {
        applyVideoTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
        releasePlayer();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {}

    /** Centre-crop, so a clip always covers the card even if the ratios drift apart. */
    private void applyVideoTransform() {
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (videoWidth <= 0 || videoHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return;
        float scale = Math.max(
            (float) viewWidth / videoWidth, (float) viewHeight / videoHeight);
        float scaleX = videoWidth * scale / viewWidth;
        float scaleY = videoHeight * scale / viewHeight;
        Matrix matrix = new Matrix();
        matrix.setScale(scaleX, scaleY);
        matrix.postTranslate(
            (viewWidth - viewWidth * scaleX) / 2f, (viewHeight - viewHeight * scaleY) / 2f);
        texture.setTransform(matrix);
    }

    private void releasePlayer() {
        if (player == null) return;
        MediaPlayer releasing = player;
        player = null;
        releasing.setOnInfoListener(null);
        releasing.setOnErrorListener(null);
        releasing.setOnPreparedListener(null);
        releasing.setOnVideoSizeChangedListener(null);
        try {
            releasing.release();
        } catch (Exception ignored) {}
    }
}
