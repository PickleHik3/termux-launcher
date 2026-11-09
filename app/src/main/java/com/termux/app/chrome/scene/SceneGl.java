package com.termux.app.chrome.scene;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLES30;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * The scene's GL pipeline on one context: the user's shader drawn into a texture at the render
 * scale (so Quarter costs a quarter of the pixels), then that texture stretched onto whatever
 * surface is current, and the glass pass's two blur halves. Everything is made once per context
 * and resized only when a size changes; a frame allocates nothing. GL thread only.
 *
 * <p>Glass blur: separable Gaussian with sigma in low-res pixels; the horizontal half reads the
 * scene texture at the low-res pixel spacing straight into a low-res texture, the vertical half
 * writes the window surface over the glass's {@code ImageReader}. Edges clamp, like the
 * {@code RenderEffect} blur it replaces.</p>
 */
final class SceneGl {

    /** The longest blur half-kernel, in taps. */
    static final int MAX_BLUR_RADIUS = 32;

    private static final String QUAD_VS = "#version 300 es\n"
        + "layout(location = 0) in vec2 aPos;\n"
        + "uniform float uFlipY;\n"
        + "out vec2 vUv;\n"
        + "void main() {\n"
        + "    vUv = aPos * 0.5 + 0.5;\n"
        + "    if (uFlipY > 0.5) vUv.y = 1.0 - vUv.y;\n"
        + "    gl_Position = vec4(aPos, 0.0, 1.0);\n"
        + "}\n";
    private static final String BLIT_FS = "#version 300 es\n"
        + "precision mediump float;\n"
        + "uniform sampler2D uTex;\n"
        + "in vec2 vUv;\n"
        + "out vec4 o;\n"
        + "void main() { o = vec4(texture(uTex, vUv).rgb, 1.0); }\n";
    private static final String BLUR_FS = "#version 300 es\n"
        + "precision mediump float;\n"
        + "uniform sampler2D uTex;\n"
        + "uniform vec2 uStep;\n"
        + "uniform float uSigma;\n"
        + "uniform int uRadius;\n"
        + "in vec2 vUv;\n"
        + "out vec4 o;\n"
        + "void main() {\n"
        + "    vec3 acc = texture(uTex, vUv).rgb;\n"
        + "    float sum = 1.0;\n"
        + "    float k = -0.5 / (uSigma * uSigma);\n"
        + "    for (int i = 1; i <= uRadius; i++) {\n"
        + "        float x = float(i);\n"
        + "        float w = exp(k * x * x);\n"
        + "        acc += w * (texture(uTex, vUv + uStep * x).rgb + texture(uTex, vUv - uStep * x).rgb);\n"
        + "        sum += 2.0 * w;\n"
        + "    }\n"
        + "    o = vec4(acc / sum, 1.0);\n"
        + "}\n";

    @NonNull final SceneEgl egl;
    private final int mVbo;
    private final int mBlit, mBlitTex, mBlitFlip;
    private final int mBlur, mBlurTex, mBlurFlip, mBlurStep, mBlurSigma, mBlurRadius;
    private final int mBlack;
    @NonNull private final int[] mName = new int[1];

    private int mFbo, mFboTex, mFboW, mFboH;
    private int mLowFbo, mLowTex, mLowW, mLowH;
    @Nullable private GlShader mShader;
    @NonNull final SceneChannel[] channels = new SceneChannel[ShaderPrelude.CHANNELS];

    /** Makes a context on this thread; throws {@link SceneEgl.EglException} when there is none. */
    SceneGl() {
        egl = new SceneEgl();
        FloatBuffer tri = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        tri.put(new float[] {-1f, -1f, 3f, -1f, -1f, 3f}).position(0);
        GLES20.glGenBuffers(1, mName, 0);
        mVbo = mName[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 6 * 4, tri, GLES20.GL_STATIC_DRAW);

        mBlit = link(QUAD_VS, BLIT_FS);
        mBlitTex = GLES20.glGetUniformLocation(mBlit, "uTex");
        mBlitFlip = GLES20.glGetUniformLocation(mBlit, "uFlipY");
        mBlur = link(QUAD_VS, BLUR_FS);
        mBlurTex = GLES20.glGetUniformLocation(mBlur, "uTex");
        mBlurFlip = GLES20.glGetUniformLocation(mBlur, "uFlipY");
        mBlurStep = GLES20.glGetUniformLocation(mBlur, "uStep");
        mBlurSigma = GLES20.glGetUniformLocation(mBlur, "uSigma");
        mBlurRadius = GLES20.glGetUniformLocation(mBlur, "uRadius");

        GLES20.glGenTextures(1, mName, 0);
        mBlack = mName[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mBlack);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE, ByteBuffer.allocateDirect(4));
        texParams(GLES20.GL_NEAREST);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    private static int link(@NonNull String vs, @NonNull String fs) {
        int v = GlShader.compile(GLES20.GL_VERTEX_SHADER, vs, null);
        int f = GlShader.compile(GLES20.GL_FRAGMENT_SHADER, fs, null);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glBindAttribLocation(p, GlShader.POSITION, "aPos");
        GLES20.glLinkProgram(p);
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);
        return p;
    }

    private static void texParams(int filter) {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    /** Swaps in {@code shader} (null for none); the old one is deleted. */
    void setShader(@Nullable GlShader shader) {
        GlShader old = mShader;
        mShader = shader;
        if (old != null && old != shader) old.release();
    }

    @Nullable
    GlShader shader() {
        return mShader;
    }

    /** Puts {@code channel} at {@code index} (attaching it); the old one is released. */
    void setChannel(int index, @Nullable SceneChannel channel) {
        SceneChannel old = channels[index];
        channels[index] = channel;
        if (old != null && old != channel) old.release();
        if (channel != null) channel.attach();
    }

    int sceneWidth() {
        return mFboW;
    }

    int sceneHeight() {
        return mFboH;
    }

    /** Draws the shader into the scene texture at {@code w} x {@code h}; false with no shader. */
    boolean renderScene(@NonNull ShaderInputs in, int w, int h, long frameTimeNanos) {
        GlShader shader = mShader;
        if (shader == null || w <= 0 || h <= 0) return false;
        ensureSceneTarget(w, h);
        for (int c = 0; c < channels.length; c++) {
            SceneChannel ch = channels[c];
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + c);
            if (ch != null && ch.textureId() != 0) {
                ch.update(frameTimeNanos);
                GLES20.glBindTexture(ch.external() ? GLES11Ext.GL_TEXTURE_EXTERNAL_OES
                    : GLES20.GL_TEXTURE_2D, ch.textureId());
                in.channelResolution[c * 3] = ch.width();
                in.channelResolution[c * 3 + 1] = ch.height();
                in.channelResolution[c * 3 + 2] = 1f;
            } else {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mBlack);
                in.channelResolution[c * 3] = 0f;
                in.channelResolution[c * 3 + 1] = 0f;
                in.channelResolution[c * 3 + 2] = 0f;
            }
        }
        in.width = w;
        in.height = h;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFbo);
        GLES20.glViewport(0, 0, w, h);
        shader.apply(in);
        drawTriangle();
        return true;
    }

    /** The scene texture stretched over the current surface's default framebuffer ({@code w} x {@code h}). */
    void blitScene(int w, int h, boolean flipY) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, w, h);
        GLES20.glUseProgram(mBlit);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFboTex);
        GLES20.glUniform1i(mBlitTex, 0);
        GLES20.glUniform1f(mBlitFlip, flipY ? 1f : 0f);
        drawTriangle();
    }

    /** Glass, first half: the scene texture blurred horizontally into the low-res texture. */
    void blurSceneHorizontally(int lw, int lh, float sigma) {
        ensureLowTarget(lw, lh);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mLowFbo);
        GLES20.glViewport(0, 0, lw, lh);
        blur(mFboTex, 1f / lw, 0f, sigma);
    }

    /** Glass, second half: the low-res texture blurred vertically onto the current surface. */
    void blurLowVertically(int lw, int lh, float sigma) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, lw, lh);
        blur(mLowTex, 0f, 1f / lh, sigma);
    }

    private void blur(int texture, float stepX, float stepY, float sigma) {
        GLES20.glUseProgram(mBlur);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glUniform1i(mBlurTex, 0);
        GLES20.glUniform1f(mBlurFlip, 0f);
        GLES20.glUniform2f(mBlurStep, stepX, stepY);
        GLES20.glUniform1f(mBlurSigma, Math.max(0.3f, sigma));
        GLES20.glUniform1i(mBlurRadius, blurRadius(sigma));
        drawTriangle();
    }

    /** Taps each side for {@code sigma}: none below 0.3 (no blur), about 3 sigma, at most {@link #MAX_BLUR_RADIUS}. */
    static int blurRadius(float sigma) {
        if (!(sigma >= 0.3f)) return 0;
        return Math.min(MAX_BLUR_RADIUS, (int) Math.ceil(sigma * 3f));
    }

    private void drawTriangle() {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        GLES20.glEnableVertexAttribArray(GlShader.POSITION);
        GLES20.glVertexAttribPointer(GlShader.POSITION, 2, GLES20.GL_FLOAT, false, 0, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3);
    }

    private void ensureSceneTarget(int w, int h) {
        if (mFbo != 0 && w == mFboW && h == mFboH) return;
        deleteSceneTarget();
        mFboTex = texture(w, h);
        mFbo = framebuffer(mFboTex);
        mFboW = w;
        mFboH = h;
    }

    private void ensureLowTarget(int w, int h) {
        if (mLowFbo != 0 && w == mLowW && h == mLowH) return;
        deleteLowTarget();
        mLowTex = texture(w, h);
        mLowFbo = framebuffer(mLowTex);
        mLowW = w;
        mLowH = h;
    }

    private int texture(int w, int h) {
        GLES20.glGenTextures(1, mName, 0);
        int tex = mName[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES30.glTexStorage2D(GLES20.GL_TEXTURE_2D, 1, GLES30.GL_RGBA8, w, h);
        texParams(GLES20.GL_LINEAR);
        return tex;
    }

    private int framebuffer(int tex) {
        GLES20.glGenFramebuffers(1, mName, 0);
        int fbo = mName[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, tex, 0);
        return fbo;
    }

    private void deleteSceneTarget() {
        if (mFbo != 0) deleteFramebuffer(mFbo);
        if (mFboTex != 0) deleteTexture(mFboTex);
        mFbo = mFboTex = mFboW = mFboH = 0;
    }

    /** Frees the glass pass's low-res texture (the glass pass closed). */
    void deleteLowTarget() {
        if (mLowFbo != 0) deleteFramebuffer(mLowFbo);
        if (mLowTex != 0) deleteTexture(mLowTex);
        mLowFbo = mLowTex = mLowW = mLowH = 0;
    }

    private void deleteTexture(int tex) {
        mName[0] = tex;
        GLES20.glDeleteTextures(1, mName, 0);
    }

    private void deleteFramebuffer(int fbo) {
        mName[0] = fbo;
        GLES20.glDeleteFramebuffers(1, mName, 0);
    }

    /** The scene texture's pixels, bottom row first, RGBA, into {@code out} (sized w * h * 4). */
    void readScene(@NonNull ByteBuffer out) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFbo);
        GLES20.glPixelStorei(GLES20.GL_PACK_ALIGNMENT, 1);
        out.position(0);
        GLES20.glReadPixels(0, 0, mFboW, mFboH, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, out);
    }

    /** Whether the GL ran out of memory since the last call (counted as a lost context). */
    boolean outOfMemory() {
        boolean oom = false;
        int err;
        while ((err = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            if (err == GLES20.GL_OUT_OF_MEMORY) oom = true;
        }
        return oom;
    }

    /** Frees every GL name and the context. */
    void release() {
        egl.makeCurrentOffscreen();
        setShader(null);
        for (int c = 0; c < channels.length; c++) setChannel(c, null);
        deleteSceneTarget();
        deleteLowTarget();
        deleteTexture(mBlack);
        GLES20.glDeleteProgram(mBlit);
        GLES20.glDeleteProgram(mBlur);
        mName[0] = mVbo;
        GLES20.glDeleteBuffers(1, mName, 0);
        egl.release();
    }

    /**
     * After a lost context: the GL names are gone with it, so only EGL is let go (deleting names on
     * a dead context is at best a no-op). Channels are released for their non-GL parts.
     */
    void abandon() {
        for (SceneChannel ch : channels) {
            if (ch != null) ch.release();
        }
        java.util.Arrays.fill(channels, null);
        mShader = null;
        egl.release();
    }
}
