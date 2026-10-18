package com.termux.app.terminal.inappkeyboard;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import juloo.keyboard2.KeyboardData;

/**
 * Where edited and user-made keyboard layouts live, and the text handling around them.
 *
 * <p>Everything is a plain file under {@code ~/.termux/keyboard}, so a terminal editor is as good
 * a way in as Settings, and deleting a file is the reset:
 * <ul>
 *   <li>{@code layout.xml} overrides {@link LauncherKeyboardLayouts#LAYOUT_MAIN}, as it always has;</li>
 *   <li>{@code layouts/<id>.xml} overrides the bundled layout of that catalogue id;</li>
 *   <li>{@code layouts/user-<slug>.xml} is a layout of the user's own. Catalogue ids are Android
 *       resource names, which cannot hold a hyphen, so the prefix can never collide with one.</li>
 * </ul>
 * A user layout's name is the {@code name} attribute of its {@code <keyboard>} tag, so the file
 * describes itself wherever it is copied.
 */
public final class KeyboardLayoutFiles {

    private static final String MAIN_RELATIVE_PATH = "keyboard/layout.xml";
    private static final String LAYOUTS_RELATIVE_DIR = "keyboard/layouts";
    private static final String EXTENSION = ".xml";
    private static final int MAX_SLUG_LENGTH = 32;
    private static final Pattern SAFE_ID = Pattern.compile("[a-z0-9_-]{1,64}");

    /** The shipped launcher layout with its explanatory header, as Settings seeds it. */
    public static final String MAIN_SEED_ASSET = "launcher-examples/keyboard-layout.xml";

    /** One of the user's own layouts, as the Layouts screen lists it. */
    public static final class UserLayout {
        public final String id;
        public final String name;

        UserLayout(@NonNull String id, @NonNull String name) {
            this.id = id;
            this.name = name;
        }
    }

    private KeyboardLayoutFiles() {
    }

    /** {@code ~/.termux}, the root every path here hangs off. */
    @NonNull
    public static File defaultHome() {
        return new File(TermuxConstants.TERMUX_DATA_HOME_DIR_PATH);
    }

    /** Whether {@code id} is fit to be a file name: no separators, no dots, nothing hidden. */
    public static boolean isSafeId(@Nullable String id) {
        return id != null && SAFE_ID.matcher(id).matches();
    }

    /** Whether {@code id} names one of the user's own layouts rather than a shipped one. */
    public static boolean isUserId(@Nullable String id) {
        return isSafeId(id) && id.startsWith(LauncherKeyboardLayouts.USER_PREFIX)
            && id.length() > LauncherKeyboardLayouts.USER_PREFIX.length();
    }

    /** The file that overrides or holds {@code id}, or null for an id no file may carry. */
    @Nullable
    public static File overrideFile(@NonNull File home, @Nullable String id) {
        if (LauncherKeyboardLayouts.LAYOUT_MAIN.equals(id))
            return new File(home, MAIN_RELATIVE_PATH);
        if (!isSafeId(id)) return null;
        return new File(new File(home, LAYOUTS_RELATIVE_DIR), id + EXTENSION);
    }

    /** Where {@code id}'s file lives, written the way a terminal user would type it. */
    @NonNull
    public static String displayPath(@NonNull String id) {
        return LauncherKeyboardLayouts.LAYOUT_MAIN.equals(id) ? "~/.termux/" + MAIN_RELATIVE_PATH
            : "~/.termux/" + LAYOUTS_RELATIVE_DIR + "/" + id + EXTENSION;
    }

    /** A file-name-safe form of a layout name: lowercase ASCII words joined by hyphens. */
    @NonNull
    public static String slug(@Nullable String name) {
        StringBuilder slug = new StringBuilder();
        if (name != null) {
            boolean pendingHyphen = false;
            for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
                if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                    if (pendingHyphen && slug.length() > 0) slug.append('-');
                    pendingHyphen = false;
                    slug.append(c);
                } else {
                    pendingHyphen = true;
                }
                if (slug.length() >= MAX_SLUG_LENGTH) break;
            }
        }
        while (slug.length() > 0 && slug.charAt(slug.length() - 1) == '-')
            slug.setLength(slug.length() - 1);
        // A name in another script has no ASCII to keep; the label still carries it.
        return slug.length() == 0 ? "layout" : slug.toString();
    }

    /** A fresh user layout id for {@code name}, numbered past any id already {@code taken}. */
    @NonNull
    public static String newUserId(@NonNull String name, @NonNull Collection<String> taken) {
        String base = LauncherKeyboardLayouts.USER_PREFIX + slug(name);
        if (!taken.contains(base)) return base;
        for (int n = 2; ; n++) {
            String candidate = base + "-" + n;
            if (!taken.contains(candidate)) return candidate;
        }
    }

    /** Ids of the shipped layouts the user has edited, {@code main} included. */
    @NonNull
    public static Set<String> overriddenIds(@NonNull File home) {
        Set<String> ids = new HashSet<>();
        File main = overrideFile(home, LauncherKeyboardLayouts.LAYOUT_MAIN);
        if (main != null && main.isFile()) ids.add(LauncherKeyboardLayouts.LAYOUT_MAIN);
        for (String id : listIds(home)) {
            if (!isUserId(id)) ids.add(id);
        }
        return ids;
    }

    /** The user's own layouts, sorted by name, each named from its file. */
    @NonNull
    public static List<UserLayout> userLayouts(@NonNull File home) {
        List<UserLayout> layouts = new ArrayList<>();
        for (String id : listIds(home)) {
            if (!isUserId(id)) continue;
            String name = null;
            try {
                name = nameAttribute(read(overrideFile(home, id)));
            } catch (IOException ignored) {
                // Listed under its id; opening it in the editor says what is wrong.
            }
            if (name == null || name.trim().isEmpty())
                name = LauncherKeyboardLayouts.fallbackUserLabel(id);
            layouts.add(new UserLayout(id, name.trim()));
        }
        Collections.sort(layouts, (a, b) -> a.name.compareToIgnoreCase(b.name));
        return layouts;
    }

    @NonNull
    private static List<String> listIds(@NonNull File home) {
        List<String> ids = new ArrayList<>();
        File[] files = new File(home, LAYOUTS_RELATIVE_DIR).listFiles();
        if (files == null) return ids;
        for (File file : files) {
            String fileName = file.getName();
            if (!fileName.endsWith(EXTENSION) || !file.isFile()) continue;
            String id = fileName.substring(0, fileName.length() - EXTENSION.length());
            if (isSafeId(id) && !LauncherKeyboardLayouts.LAYOUT_MAIN.equals(id)) ids.add(id);
        }
        return ids;
    }

    /** The file as text, refusing anything past the keyboard's own size cap. */
    @NonNull
    public static String read(@Nullable File file) throws IOException {
        if (file == null) throw new IOException("no file");
        if (file.length() > TermuxInAppKeyboardLayoutLoader.MAX_LAYOUT_BYTES)
            throw new IOException("layout exceeds " + TermuxInAppKeyboardLayoutLoader.MAX_LAYOUT_BYTES
                + " bytes");
        try (InputStream input = new FileInputStream(file)) {
            return readBounded(input);
        }
    }

    @NonNull
    private static String readBounded(@NonNull InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > TermuxInAppKeyboardLayoutLoader.MAX_LAYOUT_BYTES)
                throw new IOException("layout exceeds "
                    + TermuxInAppKeyboardLayoutLoader.MAX_LAYOUT_BYTES + " bytes");
            output.write(buffer, 0, read);
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * Writes {@code text} to {@code file} through a temporary file in the same directory and a
     * rename, so the keyboard — or a terminal reading along — never sees half a layout.
     */
    public static void writeAtomically(@NonNull File file, @NonNull String text)
        throws IOException {
        File dir = file.getParentFile();
        if (dir == null) throw new IOException("no parent directory");
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory())
            throw new IOException("could not create " + dir);
        File temp = File.createTempFile("." + file.getName(), ".tmp", dir);
        try {
            try (FileOutputStream output = new FileOutputStream(temp)) {
                output.write(text.getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            if (!temp.renameTo(file))
                throw new IOException("could not replace " + file);
        } finally {
            if (temp.exists()) //noinspection ResultOfMethodCallIgnored
                temp.delete();
        }
    }

    /** Removes {@code file}: a reset for an edited layout, a delete for a user's own. */
    public static boolean delete(@Nullable File file) {
        return file != null && (!file.exists() || file.delete());
    }

    /**
     * Why {@code text} is not a layout the keyboard can load, or null when it is one. The same
     * check the keyboard runs, so a layout the editor passes is a layout that loads.
     */
    @Nullable
    public static String validationError(@NonNull String text) {
        if (text.getBytes(StandardCharsets.UTF_8).length
            > TermuxInAppKeyboardLayoutLoader.MAX_LAYOUT_BYTES)
            return "Layout is larger than " + (TermuxInAppKeyboardLayoutLoader.MAX_LAYOUT_BYTES / 1024)
                + " KiB";
        try {
            KeyboardData.load_string_exn(text);
            return null;
        } catch (Exception e) {
            return describeError(e);
        }
    }

    /** A parser failure as one readable line: where it is, then what it is. */
    @NonNull
    static String describeError(@NonNull Throwable error) {
        int line = -1;
        int column = -1;
        String message = error.getMessage();
        if (error instanceof KeyboardData.ParseException) {
            KeyboardData.ParseException parseError = (KeyboardData.ParseException) error;
            line = parseError.lineNumber;
            column = parseError.columnNumber;
            String position = parseError.positionDescription;
            if (message != null && position != null && message.endsWith(" " + position))
                message = message.substring(0, message.length() - position.length() - 1);
        } else if (error instanceof XmlPullParserException) {
            XmlPullParserException parseError = (XmlPullParserException) error;
            line = parseError.getLineNumber();
            column = parseError.getColumnNumber();
            if (message != null) {
                int cut = message.indexOf(" (position:");
                if (cut > 0) message = message.substring(0, cut);
            }
        }
        if (message == null || message.trim().isEmpty())
            message = error.getClass().getSimpleName();
        if (line <= 0) return message.trim();
        return "Line " + line + (column > 0 ? ", column " + column : "") + ": " + message.trim();
    }

    // ------------------------------------------------------------------ sources

    /** The launcher layout as the editor opens it: the user's layout.xml, else the shipped one. */
    @NonNull
    public static String mainSource(@NonNull Context context, @NonNull File home)
        throws Exception {
        File file = overrideFile(home, LauncherKeyboardLayouts.LAYOUT_MAIN);
        if (file != null && file.isFile()) return read(file);
        try (InputStream input = context.getAssets().open(MAIN_SEED_ASSET)) {
            return mainSeed(readBounded(input));
        } catch (IOException missing) {
            return bundledSource(context.getResources(), juloo.keyboard2.R.xml.termux_launcher_qwerty);
        }
    }

    /**
     * The example file with its "this is an example, copy it into place" paragraph dropped: in
     * the editor it is no longer an example, and the rest of the header still explains the keys.
     */
    @NonNull
    static String mainSeed(@NonNull String asset) {
        int start = asset.indexOf("  THIS FILE IS AN EXAMPLE");
        if (start < 0) return asset;
        String endMarker = "termux-reload-settings\n\n";
        int end = asset.indexOf(endMarker, start);
        if (end < 0) return asset;
        return asset.substring(0, start) + asset.substring(end + endMarker.length());
    }

    /**
     * A bundled layout's source, rebuilt from its compiled resource. The APK keeps no source
     * text for an XML resource, only the parsed tree, so comments and spacing are gone; what
     * comes back has the same tags and attribute values the keyboard itself reads, laid out
     * two spaces per level.
     */
    @NonNull
    public static String bundledSource(@NonNull Resources resources, int xmlResId)
        throws Exception {
        XmlResourceParser parser = resources.getXml(xmlResId);
        try {
            return serialize(parser);
        } finally {
            parser.close();
        }
    }

    @NonNull
    static String serialize(@NonNull XmlPullParser parser) throws Exception {
        StringBuilder out = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            .append(BUNDLED_HEADER);
        int depth = 0;
        boolean tagOpen = false;
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT;
             event = parser.next()) {
            switch (event) {
                case XmlPullParser.START_TAG:
                    if (tagOpen) out.append(">\n");
                    indent(out, depth).append('<').append(parser.getName());
                    appendAttributes(out, parser);
                    tagOpen = true;
                    depth++;
                    break;
                case XmlPullParser.END_TAG:
                    depth--;
                    if (tagOpen) {
                        out.append("/>\n");
                        tagOpen = false;
                    } else {
                        indent(out, depth).append("</").append(parser.getName()).append(">\n");
                    }
                    break;
                case XmlPullParser.TEXT:
                    String text = parser.getText() == null ? "" : parser.getText().trim();
                    if (text.isEmpty()) break;
                    if (tagOpen) {
                        out.append(">\n");
                        tagOpen = false;
                    }
                    indent(out, depth).append(escape(text)).append('\n');
                    break;
                default:
                    break;
            }
        }
        return out.toString();
    }

    /**
     * Opens a rebuilt layout. The shipped source's own comments do not survive compilation, so
     * this says what the slot names mean instead.
     */
    private static final String BUNDLED_HEADER = "<!--\n"
        + "  Rebuilt from a layout that ships with Termux Launcher.\n\n"
        + "  Each key: c is the tap; nw n ne / w e / sw s se are the swipes, laid out like the\n"
        + "  key itself. A value starting with `loc ` is hidden but still reachable.\n\n"
        + "  Guide: https://picklehik3.github.io/termux-launcher-site/docs/#wiki/keyboard-layout\n"
        + "-->\n";

    /** The compiled slot names in the order a key reads: tap, then the swipes row by row. */
    private static final String[][] KEY_SLOTS = {
        {"key0", "c"}, {"key1", "nw"}, {"key7", "n"}, {"key2", "ne"}, {"key5", "w"},
        {"key6", "e"}, {"key3", "sw"}, {"key8", "s"}, {"key4", "se"}
    };

    /**
     * A tag's attributes. On a key, the slots come first under their compass names, in the order
     * the key is drawn, then everything else (width, shift, ...) as compiled.
     */
    private static void appendAttributes(@NonNull StringBuilder out,
                                         @NonNull XmlPullParser parser) {
        int count = parser.getAttributeCount();
        boolean[] written = new boolean[count];
        if ("key".equals(parser.getName())) {
            for (String[] slot : KEY_SLOTS) {
                for (int i = 0; i < count; i++) {
                    String name = parser.getAttributeName(i);
                    if (written[i] || !(slot[0].equals(name) || slot[1].equals(name))) continue;
                    appendAttribute(out, slot[1], parser.getAttributeValue(i));
                    written[i] = true;
                }
            }
        }
        for (int i = 0; i < count; i++) {
            if (!written[i])
                appendAttribute(out, parser.getAttributeName(i), parser.getAttributeValue(i));
        }
    }

    private static void appendAttribute(@NonNull StringBuilder out, @NonNull String name,
                                        @Nullable String value) {
        out.append(' ').append(name).append("=\"").append(escape(value)).append('"');
    }

    @NonNull
    private static StringBuilder indent(@NonNull StringBuilder out, int depth) {
        for (int i = 0; i < depth; i++) out.append("  ");
        return out;
    }

    // ------------------------------------------------------------------ name attribute

    /** The {@code name} attribute of the {@code <keyboard>} tag, unescaped, or null. */
    @Nullable
    public static String nameAttribute(@NonNull String xml) {
        int[] span = nameValueSpan(xml);
        if (span == null || span[0] < 0) return null;
        return unescape(xml.substring(span[0], span[1]));
    }

    /** {@code xml} with the {@code <keyboard>} tag's {@code name} set to {@code name}. */
    @NonNull
    public static String withNameAttribute(@NonNull String xml, @NonNull String name) {
        int[] span = nameValueSpan(xml);
        if (span == null) return xml;
        String escaped = escape(name);
        if (span[0] >= 0) return xml.substring(0, span[0]) + escaped + xml.substring(span[1]);
        // No name yet: it goes straight after the tag name.
        int at = span[2];
        return xml.substring(0, at) + " name=\"" + escaped + "\"" + xml.substring(at);
    }

    /**
     * Locates the root {@code <keyboard>} tag's {@code name} value: {start, end, tagNameEnd}, with
     * start and end {@code -1} when the tag has no name, or null when there is no such tag.
     * Comments, the XML declaration and a doctype ahead of it are skipped, and attributes are
     * walked one by one so a value that happens to contain {@code name=} is never mistaken for it.
     */
    @Nullable
    private static int[] nameValueSpan(@NonNull String xml) {
        int i = 0;
        int length = xml.length();
        while (true) {
            i = xml.indexOf('<', i);
            if (i < 0) return null;
            if (xml.startsWith("<!--", i)) {
                int end = xml.indexOf("-->", i + 4);
                if (end < 0) return null;
                i = end + 3;
            } else if (xml.startsWith("<?", i)) {
                int end = xml.indexOf("?>", i + 2);
                if (end < 0) return null;
                i = end + 2;
            } else if (xml.startsWith("<!", i)) {
                int end = xml.indexOf('>', i + 2);
                if (end < 0) return null;
                i = end + 1;
            } else {
                break;
            }
        }
        int tagNameEnd = i + "<keyboard".length();
        if (!xml.startsWith("<keyboard", i) || tagNameEnd >= length) return null;
        char after = xml.charAt(tagNameEnd);
        if (!Character.isWhitespace(after) && after != '>' && after != '/') return null;
        int p = tagNameEnd;
        while (p < length) {
            while (p < length && Character.isWhitespace(xml.charAt(p))) p++;
            if (p >= length || xml.charAt(p) == '>' || xml.charAt(p) == '/') break;
            int nameStart = p;
            while (p < length && !Character.isWhitespace(xml.charAt(p))
                && xml.charAt(p) != '=' && xml.charAt(p) != '>' && xml.charAt(p) != '/') p++;
            String attribute = xml.substring(nameStart, p);
            while (p < length && Character.isWhitespace(xml.charAt(p))) p++;
            if (p >= length || xml.charAt(p) != '=') return null;
            p++;
            while (p < length && Character.isWhitespace(xml.charAt(p))) p++;
            if (p >= length) return null;
            char quote = xml.charAt(p);
            if (quote != '"' && quote != '\'') return null;
            int valueStart = p + 1;
            int valueEnd = xml.indexOf(quote, valueStart);
            if (valueEnd < 0) return null;
            if (attribute.equals("name")) return new int[] {valueStart, valueEnd, tagNameEnd};
            p = valueEnd + 1;
        }
        return new int[] {-1, -1, tagNameEnd};
    }

    @NonNull
    static String escape(@Nullable String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&': out.append("&amp;"); break;
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '"': out.append("&quot;"); break;
                case '\n': out.append("&#10;"); break;
                case '\t': out.append("&#9;"); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }

    @NonNull
    static String unescape(@NonNull String value) {
        if (value.indexOf('&') < 0) return value;
        return value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&apos;", "'").replace("&#10;", "\n").replace("&#9;", "\t")
            .replace("&amp;", "&");
    }
}
