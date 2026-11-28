package com.termux.app.look;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * A supplier of named Looks for {@link TerlLookStore}: the shipped Looks
 * ({@link TerlLooks#shipped()}). Custom is the store's own, never a source's.
 */
public interface TerlLookSource {

    /** One Look's numbers for every surface that wears it. */
    final class Look {
        @NonNull public final String id;
        public final int opacity;
        public final int blur;

        public Look(@NonNull String id, int opacity, int blur) {
            this.id = id;
            this.opacity = TerlProperty.OPACITY.clamp(opacity);
            this.blur = TerlProperty.BLUR.clamp(blur);
        }
    }

    /** The Looks this source has now, in filmstrip order. */
    @NonNull
    List<Look> looks();

    @Nullable
    default Look find(@NonNull String id) {
        for (Look look : looks()) if (look.id.equals(id)) return look;
        return null;
    }
}
