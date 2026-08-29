package com.termux.app.launcher.web;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tells a web address apart from a search phrase, and turns the addresses into URLs a browser
 * will accept.
 *
 * <p>Only {@code http} and {@code https} survive. The palette hands whatever it produces to
 * {@code Intent.ACTION_VIEW}, and a typed {@code intent://} or {@code content://} string would
 * be a way to aim that at something other than a web page, so schemes are an allow-list rather
 * than a block-list.
 *
 * <p>Free of Android types so the rules can be unit-tested directly.
 */
public final class LauncherWebLinks {

    /** {@code host.tld}, optional port, optional path — the shape a bare domain has. */
    private static final Pattern BARE_HOST = Pattern.compile(
        "^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*"
            + "\\.[A-Za-z]{2,}(:\\d{1,5})?([/?#]\\S*)?$");

    /** A development host, which has no dotted domain to recognise it by. */
    private static final Pattern LOCAL_HOST = Pattern.compile(
        "^(localhost|127\\.0\\.0\\.1|\\[::1])(:\\d{1,5})?([/?#]\\S*)?$");

    private LauncherWebLinks() {
    }

    /**
     * The absolute http(s) URL {@code input} names, or null when it is a phrase to search for.
     *
     * <p>A bare domain becomes {@code https}, a local host becomes {@code http} — there is no
     * certificate on {@code localhost:8080} and defaulting it to https would only ever fail.
     */
    @Nullable
    public static String normalizeUrl(@Nullable String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.isEmpty() || containsWhitespace(trimmed)) return null;
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            // Nothing but a scheme is not an address.
            String rest = trimmed.substring(trimmed.indexOf("//") + 2);
            return rest.isEmpty() ? null : trimmed;
        }
        if (lower.contains("://")) return null;          // some other scheme: not ours to open
        if (LOCAL_HOST.matcher(trimmed).matches()) return "http://" + trimmed;
        if (BARE_HOST.matcher(trimmed).matches()) return "https://" + trimmed;
        return null;
    }

    /** Whether {@code input} is an address rather than something to search for. */
    public static boolean looksLikeUrl(@Nullable String input) {
        return normalizeUrl(input) != null;
    }

    /**
     * The host of an absolute URL, without a leading {@code www.}, for labelling a row. Null
     * when there is no host to show.
     */
    @Nullable
    public static String hostOf(@Nullable String url) {
        if (url == null) return null;
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) return null;
        String rest = url.substring(schemeEnd + 3);
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String host = rest.substring(0, end);
        int at = host.indexOf('@');
        if (at >= 0) host = host.substring(at + 1);
        int colon = host.indexOf(':');
        if (colon >= 0) host = host.substring(0, colon);
        if (host.startsWith("www.")) host = host.substring(4);
        return host.isEmpty() ? null : host;
    }

    /** A short, human label for a URL: its host, else the URL itself. */
    @NonNull
    public static String labelFor(@NonNull String url) {
        String host = hostOf(url);
        return host == null ? url : host;
    }

    private static boolean containsWhitespace(@NonNull String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) return true;
        }
        return false;
    }
}
