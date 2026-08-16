package com.termux.ai;

import android.os.Environment;

import androidx.annotation.NonNull;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.Locale;

/**
 * Fetch policy for media referenced by OpenAI-compatible chat requests.
 *
 * <p>Those requests arrive over the LauncherCtl HTTP API, so their content parts are attacker
 * controlled whenever the bearer token is known (LAN mode) or authentication has been turned off.
 * A media reference is therefore a read primitive pointed at this app's UID, and two classes of
 * target have to be taken away from it:
 *
 * <ul>
 *   <li><b>Local paths.</b> The app UID can read the whole Termux installation, including the
 *       prefix, shared preferences and the API token. Only media roots a user would plausibly
 *       point a model at stay reachable.</li>
 *   <li><b>Network destinations.</b> An unrestricted fetch turns the device into an SSRF pivot
 *       into the LAN, link-local metadata services and its own loopback services -- including this
 *       API server. Only addresses that are routable on the public internet stay reachable, and
 *       every redirect hop is re-checked because the first hop cannot vouch for the last.</li>
 * </ul>
 */
final class TaiMediaAccess {
    private static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;

    /** Directories under which a local media path may resolve. */
    private static final String[] ALLOWED_ROOTS = {
        TermuxConstants.TERMUX_HOME_DIR_PATH,
    };

    /**
     * Paths inside an allowed root that stay off limits: the launcherctl state directory holds the
     * API token and the notification history, which is exactly what a read primitive would want.
     */
    private static final String[] DENIED_SUBPATHS = {
        TermuxConstants.TERMUX_HOME_DIR_PATH + "/.launcherctl",
    };

    private TaiMediaAccess() {
    }

    /**
     * Resolves a local media path, rejecting anything outside the allowed roots.
     *
     * <p>Resolution is canonical, so {@code ..} segments and symlinks pointing out of a root are
     * caught after normalisation rather than before it.
     *
     * @return the canonical path to hand to the runtime.
     */
    @NonNull
    static String resolveLocalPath(@NonNull String rawPath) throws JSONException {
        String path = rawPath.trim();
        if (path.isEmpty()) throw new JSONException("media_fetch_failed:Empty media path");

        String canonical;
        try {
            canonical = new File(path).getCanonicalPath();
        } catch (IOException e) {
            throw new JSONException("media_fetch_failed:Media path could not be resolved");
        }

        for (String denied : DENIED_SUBPATHS) {
            if (isWithin(canonical, denied)) {
                throw new JSONException("media_access_denied:Media path is not readable through this endpoint");
            }
        }
        for (String root : allowedRoots()) {
            if (isWithin(canonical, root)) return canonical;
        }
        throw new JSONException("media_access_denied:Local media must live under "
            + TermuxConstants.TERMUX_HOME_DIR_PATH + " or shared storage");
    }

    /**
     * Downloads remote media, following redirects manually so every hop can be validated.
     */
    @NonNull
    static byte[] fetch(@NonNull String rawUrl, int maxBytes) throws JSONException {
        String current = rawUrl.trim();
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                URL url = new URL(current);
                requirePublicDestination(url);

                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                try {
                    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                    connection.setReadTimeout(READ_TIMEOUT_MS);
                    // Redirects are followed by hand: the platform would happily walk from a public
                    // host to 127.0.0.1 or 169.254.169.254 without telling us.
                    connection.setInstanceFollowRedirects(false);

                    int status = connection.getResponseCode();
                    if (status >= 300 && status < 400) {
                        String location = connection.getHeaderField("Location");
                        if (location == null || location.trim().isEmpty()) {
                            throw new JSONException("media_fetch_failed:Redirect without a destination");
                        }
                        current = new URL(url, location.trim()).toExternalForm();
                        continue;
                    }
                    if (status < 200 || status >= 300) {
                        throw new JSONException("media_fetch_failed:HTTP " + status + " while fetching media");
                    }

                    int length = connection.getContentLength();
                    if (length > maxBytes) throw new JSONException("media_fetch_failed:Media exceeds 25 MB");
                    return readBounded(connection.getInputStream(), maxBytes);
                } finally {
                    connection.disconnect();
                }
            }
        } catch (JSONException e) {
            throw e;
        } catch (Exception e) {
            throw new JSONException("media_fetch_failed:"
                + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        throw new JSONException("media_fetch_failed:Too many redirects while fetching media");
    }

    /**
     * Rejects a URL whose scheme is not HTTP(S), or that resolves to any address the device can
     * reach only because of where it sits: loopback, link-local, private ranges, multicast.
     *
     * <p>Every address the name resolves to is checked, not just the first, so a hostname with one
     * public and one private record cannot slip through. A name that is re-resolved to a private
     * address between this check and the connection is still possible in principle; the redirect
     * re-validation and the response size cap bound what that would yield.
     */
    private static void requirePublicDestination(@NonNull URL url) throws JSONException {
        String protocol = url.getProtocol() == null ? "" : url.getProtocol().toLowerCase(Locale.ROOT);
        if (!"http".equals(protocol) && !"https".equals(protocol)) {
            throw new JSONException("media_fetch_failed:Unsupported media URL scheme");
        }
        String host = url.getHost();
        if (host == null || host.trim().isEmpty()) {
            throw new JSONException("media_fetch_failed:Media URL has no host");
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (Exception e) {
            throw new JSONException("media_fetch_failed:Media host could not be resolved");
        }
        if (addresses.length == 0) {
            throw new JSONException("media_fetch_failed:Media host could not be resolved");
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new JSONException("media_access_denied:Media host resolves to a non-public address");
            }
        }
    }

    static boolean isPublicAddress(@NonNull InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
            || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            // Carrier-grade NAT, and the 172.16/12 block that isSiteLocalAddress already covers is
            // repeated here only for the ranges it misses: 100.64/10 and 192.0.0/24.
            if (first == 100 && second >= 64 && second <= 127) return false;
            if (first == 192 && second == 0 && (bytes[2] & 0xFF) == 0) return false;
            // 0.0.0.0/8 and the 240/4 reserved space.
            if (first == 0 || first >= 240) return false;
            return true;
        }
        if (address instanceof Inet6Address) {
            int first = bytes[0] & 0xFF;
            // Unique local addresses (fc00::/7) are the IPv6 equivalent of a private range.
            if ((first & 0xFE) == 0xFC) return false;
            return true;
        }
        return false;
    }

    @NonNull
    private static byte[] readBounded(@NonNull InputStream input, int maxBytes) throws IOException, JSONException {
        try (InputStream in = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new JSONException("media_fetch_failed:Media exceeds 25 MB");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    @NonNull
    private static String[] allowedRoots() {
        File shared = Environment.getExternalStorageDirectory();
        if (shared == null) return ALLOWED_ROOTS;
        String[] roots = new String[ALLOWED_ROOTS.length + 1];
        System.arraycopy(ALLOWED_ROOTS, 0, roots, 0, ALLOWED_ROOTS.length);
        roots[ALLOWED_ROOTS.length] = shared.getAbsolutePath();
        return roots;
    }

    private static boolean isWithin(@NonNull String canonicalPath, @NonNull String rawRoot) {
        String root;
        try {
            root = new File(rawRoot).getCanonicalPath();
        } catch (IOException e) {
            root = rawRoot;
        }
        if (canonicalPath.equals(root)) return true;
        return canonicalPath.startsWith(root.endsWith("/") ? root : root + "/");
    }
}
