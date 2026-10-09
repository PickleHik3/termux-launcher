package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The looks the cursor trail can take. {@link #DEFAULT} is kitty's own quad and
 * {@link #PIXIEDUST} adds kitty's pixiedust particles to it. {@link #MOTION_BLUR},
 * {@link #RAILGUN} and {@link #TORPEDO} are named after kitty's bundled {@code cursor-trail-*}
 * shaders but drawn this app's own way, each with one trait nobody can mistake (a soft lagging
 * smear, a thin hot beam with sparks, a tapered body with a wake), and {@link #COMET} is this
 * app's own.
 */
public enum CursorTrailStyle {
    DEFAULT("default"),
    MOTION_BLUR("motion_blur"),
    RAILGUN("railgun"),
    TORPEDO("torpedo"),
    PIXIEDUST("pixiedust"),
    COMET("comet");

    private final String mId;

    CursorTrailStyle(String id) {
        mId = id;
    }

    /** The stable id stored in preferences. */
    @NonNull
    public String id() {
        return mId;
    }

    /** The style with this id; null or unknown ids give {@link #DEFAULT}. */
    @NonNull
    public static CursorTrailStyle fromId(@Nullable String id) {
        if (id != null) {
            for (CursorTrailStyle style : values()) {
                if (style.mId.equals(id)) return style;
            }
        }
        return DEFAULT;
    }

    /** The style to draw: kitty.conf's id wins when non-null, else the preference, else default. */
    @NonNull
    public static CursorTrailStyle effective(@Nullable String kittyId, @Nullable String prefId) {
        return fromId(kittyId != null ? kittyId : prefId);
    }

    /**
     * The style kitty's {@code custom_shaders} entry names, or null when it names none this app
     * has an equivalent for.
     */
    @Nullable
    public static CursorTrailStyle fromKittyShaderName(@Nullable String name) {
        if (name == null) return null;
        switch (name) {
            case "cursor-trail-default": return DEFAULT;
            case "cursor-trail-motion-blur": return MOTION_BLUR;
            case "cursor-trail-railgun": return RAILGUN;
            case "cursor-trail-torpedo": return TORPEDO;
            case "cursor-trail-pixiedust": return PIXIEDUST;
            default: return null;
        }
    }
}
