package com.termux.app.chrome.scene;

import androidx.annotation.NonNull;

/**
 * The cover-crop and container-rotation math of the video passthrough, pure so it is tested
 * without a GPU. The program maps a target pixel's uv (0..1, origin bottom-left) to a texture
 * coordinate with one matrix: {@code tc = M * (uv, 0, 1)}. {@link #fill} builds M as
 * {@code surfaceMatrix * rawFromTarget}, where {@code rawFromTarget} first picks the part of the
 * displayed picture that covers the target (centre-cropped, no stretch) and then turns displayed
 * coordinates back into the decoded frame's own, undoing the container's rotation.
 */
final class VideoCrop {

    private VideoCrop() {}

    /**
     * The part of the displayed picture (u, v of 0..1, origin bottom-left) that covers the target:
     * {@code {u0, v0, u1, v1}}, centred. A non-positive size means no crop (the whole picture).
     */
    @NonNull
    static float[] coverRect(int displayW, int displayH, int targetW, int targetH) {
        float[] r = {0f, 0f, 1f, 1f};
        coverRect(displayW, displayH, targetW, targetH, r);
        return r;
    }

    static void coverRect(int displayW, int displayH, int targetW, int targetH, @NonNull float[] out4) {
        out4[0] = 0f;
        out4[1] = 0f;
        out4[2] = 1f;
        out4[3] = 1f;
        if (displayW <= 0 || displayH <= 0 || targetW <= 0 || targetH <= 0) return;
        float media = displayW / (float) displayH;
        float screen = targetW / (float) targetH;
        if (screen > media) {
            // The target is wider: the picture fills its width, the top and bottom are cut.
            float keep = media / screen;
            out4[1] = 0.5f - keep / 2f;
            out4[3] = 0.5f + keep / 2f;
        } else {
            float keep = screen / media;
            out4[0] = 0.5f - keep / 2f;
            out4[2] = 0.5f + keep / 2f;
        }
    }

    /**
     * Where a point of the displayed picture lies in the decoded frame, for a container rotation
     * of {@code rotation} degrees (0, 90, 180, 270; the frame is shown turned clockwise by it).
     * {@code out2} gets the frame's (u, v).
     */
    static void rawFromDisplayed(int rotation, float u, float v, @NonNull float[] out2) {
        float cx = u - 0.5f, cy = v - 0.5f;
        switch (((rotation % 360) + 360) % 360) {
            case 90:
                out2[0] = 0.5f - cy;
                out2[1] = 0.5f + cx;
                break;
            case 180:
                out2[0] = 0.5f - cx;
                out2[1] = 0.5f - cy;
                break;
            case 270:
                out2[0] = 0.5f + cy;
                out2[1] = 0.5f - cx;
                break;
            default:
                out2[0] = u;
                out2[1] = v;
                break;
        }
    }

    /**
     * Writes M (column-major 4x4) into {@code out16}.
     *
     * @param surface the SurfaceTexture's transform matrix, column-major
     * @param displayW the picture's size as shown (already turned by the rotation)
     */
    static void fill(@NonNull float[] out16, @NonNull float[] surface, int displayW, int displayH,
                     int rotation, int targetW, int targetH) {
        float[] r = new float[4];   // small, once per frame; see fillNoAlloc for the reusable form
        fill(out16, surface, displayW, displayH, rotation, targetW, targetH, r, new float[2]);
    }

    /** As {@link #fill} with the scratch arrays supplied (4 and 2 floats), for the per-frame call. */
    static void fill(@NonNull float[] out16, @NonNull float[] surface, int displayW, int displayH,
                     int rotation, int targetW, int targetH, @NonNull float[] rect4, @NonNull float[] pt2) {
        coverRect(displayW, displayH, targetW, targetH, rect4);
        // uv -> displayed (du, dv): scale and offset by the cover rect.
        float sx = rect4[2] - rect4[0], sy = rect4[3] - rect4[1];
        // The affine map (x, y) -> (a x + c y + tx, b x + d y + ty) from target uv to frame uv, found
        // from where the origin and the two unit steps go.
        rawFromDisplayed(rotation, rect4[0], rect4[1], pt2);
        float ox = pt2[0], oy = pt2[1];
        rawFromDisplayed(rotation, rect4[0] + sx, rect4[1], pt2);
        float ax = pt2[0] - ox, ay = pt2[1] - oy;
        rawFromDisplayed(rotation, rect4[0], rect4[1] + sy, pt2);
        float cx = pt2[0] - ox, cy = pt2[1] - oy;
        // out = surface * A, A column-major: col0 = (ax, ay), col1 = (cx, cy), col3 = (ox, oy).
        for (int col = 0; col < 4; col++) {
            float a0, a1, a2, a3;
            switch (col) {
                case 0: a0 = ax; a1 = ay; a2 = 0f; a3 = 0f; break;
                case 1: a0 = cx; a1 = cy; a2 = 0f; a3 = 0f; break;
                case 2: a0 = 0f; a1 = 0f; a2 = 1f; a3 = 0f; break;
                default: a0 = ox; a1 = oy; a2 = 0f; a3 = 1f; break;
            }
            for (int row = 0; row < 4; row++) {
                out16[col * 4 + row] = surface[row] * a0 + surface[4 + row] * a1
                    + surface[8 + row] * a2 + surface[12 + row] * a3;
            }
        }
    }
}
