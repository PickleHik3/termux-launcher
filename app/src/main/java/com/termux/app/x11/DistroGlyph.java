package com.termux.app.x11;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

import java.util.Locale;

/**
 * Which logo stands for a {@code proot-distro} container on its apps' icons. Pure: the name a
 * container goes by ({@link X11Apps#containerOf}) in, one glyph out, so the aliases the tool
 * accepts ({@code archlinux}, {@code ubuntu-lts}, an image such as {@code menci/archlinuxarm})
 * are pinned by a test and not discovered on a phone.
 */
public enum DistroGlyph {
    DEBIAN("debian", R.drawable.ic_distro_debian),
    UBUNTU("ubuntu", R.drawable.ic_distro_ubuntu),
    ALPINE("alpine", R.drawable.ic_distro_alpine),
    ARCH("arch", R.drawable.ic_distro_arch),
    FEDORA("fedora", R.drawable.ic_distro_fedora),
    OPENSUSE("opensuse", R.drawable.ic_distro_opensuse),
    VOID("void", R.drawable.ic_distro_void),
    MANJARO("manjaro", R.drawable.ic_distro_manjaro),
    KALI("kali", R.drawable.ic_distro_kali),
    ROCKY("rocky", R.drawable.ic_distro_rocky),
    ALMALINUX("alma", R.drawable.ic_distro_almalinux),
    GENTOO("gentoo", R.drawable.ic_distro_gentoo),
    NIXOS("nix", R.drawable.ic_distro_nixos),
    /** Any distro this build has no logo for. */
    GENERIC("", R.drawable.ic_distro_linux);

    /** What a normalised container name starts with to be this distro. */
    @NonNull private final String stem;
    @DrawableRes public final int drawableRes;

    DistroGlyph(@NonNull String stem, @DrawableRes int drawableRes) {
        this.stem = stem;
        this.drawableRes = drawableRes;
    }

    /**
     * The glyph for a container name, case-insensitive, an image path reduced to its last segment;
     * {@link #GENERIC} for an unknown name. Null for the prefix (empty name), which wears none.
     */
    @Nullable
    public static DistroGlyph forContainer(@Nullable String name) {
        if (name == null) return null;
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.isEmpty()) return null;
        n = n.substring(n.lastIndexOf('/') + 1);
        for (DistroGlyph glyph : values()) {
            if (!glyph.stem.isEmpty() && n.startsWith(glyph.stem)) return glyph;
        }
        return GENERIC;
    }

    /**
     * What to call a container in an accessibility label: its last name segment with a capital,
     * {@code Debian}; empty for the prefix.
     */
    @NonNull
    public static String displayName(@Nullable String name) {
        if (name == null) return "";
        String n = name.trim();
        n = n.substring(n.lastIndexOf('/') + 1);
        return n.isEmpty() ? "" : n.substring(0, 1).toUpperCase(Locale.ROOT) + n.substring(1);
    }

    /** {@link #displayName} of the container an app's ref lives in; empty for any other app. */
    @NonNull
    public static String displayNameOf(@NonNull com.termux.app.launcher.model.AppRef ref) {
        if (!X11Apps.isLinuxApp(ref)) return "";
        return displayName(X11Apps.containerOf(X11Apps.desktopId(ref)));
    }
}
