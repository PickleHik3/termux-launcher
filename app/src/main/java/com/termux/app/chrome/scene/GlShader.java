package com.termux.app.chrome.scene;

import android.opengl.GLES20;
import android.opengl.GLES30;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A user's fragment shader, built under {@link ShaderPrelude}, linked with a full-screen-triangle
 * vertex shader of the same GLSL version. GL thread, context current. The uniform table is read
 * once at link: a uniform the program does not have, or has with another type than the contract's,
 * is never set.
 */
final class GlShader {

    /** The vertex attribute the full-screen triangle feeds. */
    static final int POSITION = 0;

    private static final String VERTEX_ES3 = "#version 300 es\n"
        + "layout(location = 0) in vec2 aPos;\n"
        + "void main() { gl_Position = vec4(aPos, 0.0, 1.0); }\n";
    private static final String VERTEX_ES1 = "attribute vec2 aPos;\n"
        + "void main() { gl_Position = vec4(aPos, 0.0, 1.0); }\n";

    /** Thrown with the errors mapped onto the user's lines. */
    static final class CompileException extends RuntimeException {
        @NonNull final List<ShaderErrors.Error> errors;

        CompileException(@NonNull List<ShaderErrors.Error> errors) {
            super(ShaderErrors.format(errors));
            this.errors = errors;
        }
    }

    /** Our own program for a video: {@code iChannel0} is a {@code samplerExternalOES}, centre-cropped (cover). */
    private static final String PASSTHROUGH_FS = "#version 300 es\n"
        + "#extension GL_OES_EGL_image_external_essl3 : require\n"
        + "precision highp float;\n"
        + "uniform vec3 iResolution;\n"
        + "uniform vec3 iChannelResolution[4];\n"
        + "uniform mat4 uTexMatrix;\n"
        + "uniform samplerExternalOES iChannel0;\n"
        + "out vec4 tlFragColor;\n"
        + "void main() {\n"
        + "  vec2 uv = gl_FragCoord.xy / iResolution.xy;\n"
        + "  vec2 media = iChannelResolution[0].xy;\n"
        + "  if (media.x > 0.0 && media.y > 0.0) {\n"
        + "    float screenAspect = iResolution.x / iResolution.y;\n"
        + "    float mediaAspect = media.x / media.y;\n"
        + "    if (screenAspect > mediaAspect) uv.y = 0.5 + (uv.y - 0.5) * (mediaAspect / screenAspect);\n"
        + "    else uv.x = 0.5 + (uv.x - 0.5) * (screenAspect / mediaAspect);\n"
        + "  }\n"
        + "  vec2 tc = (uTexMatrix * vec4(uv, 0.0, 1.0)).xy;\n"
        + "  tlFragColor = vec4(texture(iChannel0, tc).rgb, 1.0);\n"
        + "}\n";

    final int program;
    @NonNull final ShaderPrelude.Built built;
    /** Location per {@link ShaderPrelude#UNIFORMS} index; -1 when the program lacks it. */
    @NonNull private final int[] mLoc = new int[ShaderPrelude.UNIFORMS.length];

    /** Location of {@code uTexMatrix}; -1 for a user shader. */
    private int mTexMatrixLoc = -1;

    private GlShader(int program, @NonNull ShaderPrelude.Built built) {
        this.program = program;
        this.built = built;
    }

    /** The video passthrough program (spec decision 2); throws {@link IllegalStateException} when the GPU rejects it. */
    @NonNull
    static GlShader buildPassthrough() {
        int vs = compile(GLES20.GL_VERTEX_SHADER, VERTEX_ES3, null);
        int fs;
        try {
            fs = compile(GLES20.GL_FRAGMENT_SHADER, PASSTHROUGH_FS, null);
        } catch (IllegalStateException e) {
            GLES20.glDeleteShader(vs);
            throw e;
        }
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glBindAttribLocation(program, POSITION, "aPos");
        GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("video program did not link: " + log);
        }
        ShaderPrelude.Built built = new ShaderPrelude.Built(PASSTHROUGH_FS, ShaderPrelude.Flavour.ES3_MAIN, 0, 0,
            new int[0]);
        GlShader shader = new GlShader(program, built);
        shader.readUniforms();
        shader.mTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix");
        return shader;
    }

    @NonNull
    static GlShader build(@NonNull String userSource) {
        ShaderPrelude.Built built = ShaderPrelude.build(userSource);
        int vs = compile(GLES20.GL_VERTEX_SHADER, built.flavour.es3() ? VERTEX_ES3 : VERTEX_ES1, null);
        int fs;
        try {
            fs = compile(GLES20.GL_FRAGMENT_SHADER, built.source, built);
        } catch (CompileException e) {
            GLES20.glDeleteShader(vs);
            throw e;
        }
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glBindAttribLocation(program, POSITION, "aPos");
        GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            List<ShaderErrors.Error> errors = ShaderErrors.parse(log, built);
            throw new CompileException(errors.isEmpty()
                ? Collections.singletonList(new ShaderErrors.Error(-1, "The shader did not link."))
                : errors);
        }
        GlShader shader = new GlShader(program, built);
        shader.readUniforms();
        return shader;
    }

    /** {@code built} null: our own shader, its log is not mapped. */
    static int compile(int type, @NonNull String source, @Nullable ShaderPrelude.Built built) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] != 0) return shader;
        String log = GLES20.glGetShaderInfoLog(shader);
        GLES20.glDeleteShader(shader);
        if (built == null) throw new IllegalStateException("built-in shader failed: " + log);
        List<ShaderErrors.Error> errors = ShaderErrors.parse(log, built);
        throw new CompileException(errors.isEmpty()
            ? Collections.singletonList(new ShaderErrors.Error(-1, "The shader did not compile."))
            : errors);
    }

    private void readUniforms() {
        java.util.Arrays.fill(mLoc, -1);
        int[] count = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_ACTIVE_UNIFORMS, count, 0);
        int[] size = new int[1];
        int[] type = new int[1];
        for (int i = 0; i < count[0]; i++) {
            String name = GLES20.glGetActiveUniform(program, i, size, 0, type, 0);
            if (name == null) continue;
            int bracket = name.indexOf('[');
            String base = bracket >= 0 ? name.substring(0, bracket) : name;
            for (int u = 0; u < ShaderPrelude.UNIFORMS.length; u++) {
                ShaderPrelude.Uniform want = ShaderPrelude.UNIFORMS[u];
                if (want.name.equals(base) && glType(want.type) == type[0]) {
                    mLoc[u] = GLES20.glGetUniformLocation(program, want.arrayLength > 0 ? base + "[0]" : base);
                }
            }
        }
        GLES20.glUseProgram(program);
        for (int c = 0; c < ShaderPrelude.CHANNELS; c++) {
            int loc = mLoc[ShaderPrelude.I_CHANNEL0 + c];
            if (loc >= 0) GLES20.glUniform1i(loc, c);
        }
    }

    private static int glType(@NonNull String type) {
        switch (type) {
            case "float": return GLES20.GL_FLOAT;
            case "vec2": return GLES20.GL_FLOAT_VEC2;
            case "vec3": return GLES20.GL_FLOAT_VEC3;
            case "vec4": return GLES20.GL_FLOAT_VEC4;
            case "int": return GLES20.GL_INT;
            case "sampler2D": return GLES20.GL_SAMPLER_2D;
            default: return 0;
        }
    }

    boolean has(int index) {
        return mLoc[index] >= 0;
    }

    /** Sets every uniform the program has from {@code in}; the program is made current. */
    void apply(@NonNull ShaderInputs in) {
        GLES20.glUseProgram(program);
        if (mTexMatrixLoc >= 0) GLES20.glUniformMatrix4fv(mTexMatrixLoc, 1, false, in.texMatrix, 0);
        int[] l = mLoc;
        float t = (float) in.timeSec;
        if (l[ShaderPrelude.I_RESOLUTION] >= 0) GLES20.glUniform3f(l[ShaderPrelude.I_RESOLUTION], in.width, in.height, 1f);
        if (l[ShaderPrelude.I_TIME] >= 0) GLES20.glUniform1f(l[ShaderPrelude.I_TIME], t);
        if (l[ShaderPrelude.I_TIME_DELTA] >= 0) GLES20.glUniform1f(l[ShaderPrelude.I_TIME_DELTA], in.deltaSec);
        if (l[ShaderPrelude.I_FRAME] >= 0) GLES20.glUniform1i(l[ShaderPrelude.I_FRAME], in.frame);
        if (l[ShaderPrelude.I_FRAME_RATE] >= 0) GLES20.glUniform1f(l[ShaderPrelude.I_FRAME_RATE], in.frameRate);
        if (l[ShaderPrelude.I_MOUSE] >= 0) GLES20.glUniform4fv(l[ShaderPrelude.I_MOUSE], 1, in.mouse, 0);
        if (l[ShaderPrelude.I_DATE] >= 0) GLES20.glUniform4fv(l[ShaderPrelude.I_DATE], 1, in.date, 0);
        if (l[ShaderPrelude.I_CHANNEL_RESOLUTION] >= 0) {
            GLES20.glUniform3fv(l[ShaderPrelude.I_CHANNEL_RESOLUTION], ShaderPrelude.CHANNELS, in.channelResolution, 0);
        }
        if (l[ShaderPrelude.A_RESOLUTION] >= 0) GLES20.glUniform2f(l[ShaderPrelude.A_RESOLUTION], in.width, in.height);
        if (l[ShaderPrelude.A_TIME] >= 0) GLES20.glUniform1f(l[ShaderPrelude.A_TIME], t);
        if (l[ShaderPrelude.A_TOUCH] >= 0) GLES20.glUniform2fv(l[ShaderPrelude.A_TOUCH], 1, in.touch, 0);
        if (l[ShaderPrelude.A_POINTER_COUNT] >= 0) GLES20.glUniform1i(l[ShaderPrelude.A_POINTER_COUNT], in.pointerCount);
        if (l[ShaderPrelude.A_POINTERS] >= 0) {
            GLES20.glUniform3fv(l[ShaderPrelude.A_POINTERS], ShaderPrelude.MAX_POINTERS, in.pointers, 0);
        }
        if (l[ShaderPrelude.A_BATTERY] >= 0) GLES20.glUniform1f(l[ShaderPrelude.A_BATTERY], in.battery);
        if (l[ShaderPrelude.A_OFFSET] >= 0) GLES20.glUniform2fv(l[ShaderPrelude.A_OFFSET], 1, in.offset, 0);
        if (l[ShaderPrelude.A_DATE] >= 0) GLES20.glUniform4fv(l[ShaderPrelude.A_DATE], 1, in.date, 0);
        if (l[ShaderPrelude.TL_PAGE_OFFSET] >= 0) GLES20.glUniform1f(l[ShaderPrelude.TL_PAGE_OFFSET], in.pageOffset);
        if (l[ShaderPrelude.TL_BATTERY] >= 0) GLES20.glUniform1f(l[ShaderPrelude.TL_BATTERY], in.battery);
        if (l[ShaderPrelude.TL_KEYBOARD] >= 0) GLES20.glUniform1f(l[ShaderPrelude.TL_KEYBOARD], in.keyboard);
        if (l[ShaderPrelude.TL_DARK] >= 0) GLES20.glUniform1f(l[ShaderPrelude.TL_DARK], in.dark);
        for (int c = 0; c < 3; c++) {
            int loc = l[ShaderPrelude.TL_COLOR0 + c];
            if (loc >= 0) GLES20.glUniform3fv(loc, 1, in.colors, c * 3);
        }
    }

    void release() {
        GLES30.glUseProgram(0);
        GLES20.glDeleteProgram(program);
    }
}
