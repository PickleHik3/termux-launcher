package com.termux.app.chrome.wallpaper;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.chrome.scene.ShaderErrors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The prompt a photo goes to an AI chat with, and how the chat's answer is read back. The prompt
 * is {@code res/raw/photo_shader_instructions.txt} with its {@link #PLACEHOLDER} line filled from
 * whether the shader has a depth image; it ends on the wishes line, so what the user types next
 * lands right under it. Used by the editor's copy and by {@link PhotoShaderNotification}.
 */
public final class PhotoShaderPrompt {

    /** The template's one line that says what the attached image is. */
    public static final String PLACEHOLDER = "{IMAGE}";
    /** {@link #PLACEHOLDER} when the shader has a depth image: the copied image is photo | depth. */
    public static final String IMAGE_WITH_DEPTH = "The attached image is the photo on the left and its depth map "
        + "on the right, side by side at the same size (white = near, black = far). Use the depth map to see "
        + "what is near and far; the shader receives the photo and the depth map separately, as iChannel0 and "
        + "iChannel1.";
    /** {@link #PLACEHOLDER} when it has none: the copied image is the photo alone. */
    public static final String IMAGE_NO_DEPTH = "The attached image is the photo. It has no depth map yet: "
        + "iChannel1 is empty for now.";

    private static final String FENCE = "```";

    private PhotoShaderPrompt() {}

    /**
     * The prompt from {@code template}: the image line filled in, line ends made {@code \n}, and
     * exactly one {@code \n} after the last line, so no blank line sits between the wishes line
     * and the user's own text.
     */
    @NonNull
    public static String fill(@NonNull String template, boolean depth) {
        String text = template.replace("\r\n", "\n").replace(PLACEHOLDER, depth ? IMAGE_WITH_DEPTH : IMAGE_NO_DEPTH);
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) end--;
        return text.substring(0, end) + "\n";
    }

    /** The prompt for a shader with or without a depth image, or null when the template cannot be read. */
    @Nullable
    public static String read(@NonNull Context context, boolean depth) {
        try (InputStream in = context.getResources().openRawResource(R.raw.photo_shader_instructions)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return fill(new String(out.toByteArray(), StandardCharsets.UTF_8), depth);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * The shader in an AI chat's answer: the first fenced block's body (its language tag and any
     * prose around it dropped; an unclosed fence runs to the end), or the whole answer when it
     * has no fence. Trimmed; empty when there is nothing.
     */
    @NonNull
    public static String extractCode(@Nullable String answer) {
        if (answer == null) return "";
        String text = answer.replace("\r\n", "\n");
        int open = text.indexOf(FENCE);
        if (open < 0) return text.trim();
        int bodyStart = text.indexOf('\n', open + FENCE.length());
        if (bodyStart < 0) return "";
        int close = text.indexOf(FENCE, bodyStart + 1);
        return (close < 0 ? text.substring(bodyStart + 1) : text.substring(bodyStart + 1, close)).trim();
    }

    /** What Copy error puts on the clipboard: {@code intro}, a blank line, then one error a line. */
    @NonNull
    public static String errorReport(@NonNull String intro, @Nullable List<ShaderErrors.Error> errors) {
        StringBuilder b = new StringBuilder(intro).append("\n\n");
        if (errors != null) {
            for (int i = 0; i < errors.size(); i++) {
                if (i > 0) b.append('\n');
                b.append(errors.get(i).toString());
            }
        }
        return b.toString();
    }

    /** The first error, as the failed notification's one line; empty when there is none. */
    @NonNull
    public static String firstError(@Nullable List<ShaderErrors.Error> errors) {
        return errors == null || errors.isEmpty() ? "" : errors.get(0).toString();
    }
}
