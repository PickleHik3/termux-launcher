package com.termux.app.theme.templates;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills a template's placeholders from the exported palette.
 *
 * <p>The syntax is the subset of noctalia's template language the launcher ports:
 * {@code {{ colors.<token>.<mode>.<format> }}} and {@code {{ mode }}}, spaces inside the braces
 * optional. All three modes ({@code default}, {@code dark}, {@code light}) read the one palette the
 * launcher has — only the active contrast level is ever rendered — so they are accepted and
 * resolved identically rather than silently dropped from ported templates.
 *
 * <p>Those two are the only placeholders. Every other {@code {{ … }}} is left exactly as written,
 * because the files being rendered are other people's formats — an oh-my-posh theme is a Go template
 * of {@code {{ .Path }}} and {@code {{ if .Root }}} that has to survive intact.
 *
 * <p>What the port does not understand, on the other hand, stops it: a {@code colors.} placeholder
 * naming an unknown token or format, and noctalia's {@code <* … *>} blocks, fail the whole template
 * with a reason rather than write a half-rendered config over a tool the user relies on. Deliberately
 * free of Android imports so the rules stay testable as plain arithmetic.
 */
public final class ThemeTemplateRenderer {

    /** The palette key carrying {@code dark} or {@code light}. */
    public static final String KEY_MODE = "mode";

    private static final String FALLBACK_MODE = "dark";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]*)\\}\\}");

    private static final String COLORS_PREFIX = "colors.";

    private ThemeTemplateRenderer() {
    }

    /** A rendered template, or the reason it was skipped. */
    public static final class Result {

        /** The rendered text, or {@code null} when the template failed. */
        public final String text;

        /** Why the template was skipped, or {@code null} when it rendered. */
        public final String failure;

        private Result(String text, String failure) {
            this.text = text;
            this.failure = failure;
        }

        public boolean isSuccess() {
            return failure == null;
        }
    }

    /** The mode the palette describes, falling back to dark when the palette predates the key. */
    public static String modeOf(Properties palette) {
        if (palette == null) return FALLBACK_MODE;
        String mode = palette.getProperty(KEY_MODE);
        return mode == null || mode.trim().isEmpty() ? FALLBACK_MODE : mode.trim();
    }

    /**
     * Render {@code template} against {@code palette}.
     *
     * @return a {@link Result} whose {@link Result#failure} is set when nothing should be written.
     */
    public static Result render(String template, Properties palette) {
        return render(template, PaletteSet.of(palette));
    }

    /**
     * Render {@code template} against a whole palette set.
     *
     * @return a {@link Result} whose {@link Result#failure} is set when nothing should be written.
     */
    public static Result render(String template, PaletteSet palettes) {
        Properties palette = palettes == null ? null : palettes.active();
        if (template == null) return failed("the template file is missing");
        if (template.contains("<*")) return failed("block syntax (<* … *>) is not supported");
        String mode = modeOf(palette);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer out = new StringBuffer(template.length());
        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            String value;
            if (KEY_MODE.equals(expression)) {
                value = mode;
            } else if (expression.startsWith(COLORS_PREFIX)) {
                value = resolve(expression, palette);
                if (value == null) return failed("cannot resolve {{ " + expression + " }}");
            } else {
                // Not ours. Two of the shipped templates are Go templates — {{ .Path }},
                // {{ if .Root }} — which a renderer with opinions about every pair of braces it
                // meets would destroy.
                value = matcher.group();
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return new Result(out.toString(), null);
    }

    /** The value for {@code colors.<token>.<mode>.<format>}, or {@code null} if it means nothing. */
    private static String resolve(String expression, Properties palette) {
        String[] parts = expression.split("\\.");
        if (parts.length != 4 || !"colors".equals(parts[0])) return null;
        String token = parts[1];
        String mode = parts[2];
        String format = parts[3];
        if (!"default".equals(mode) && !"dark".equals(mode) && !"light".equals(mode)) return null;
        String raw = palette == null ? null : palette.getProperty(token);
        if (raw == null) return null;
        int color = parseColor(raw);
        if (color < 0) return null;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        switch (format) {
            case "hex":
                return "#" + hex(color);
            case "hex_stripped":
                return hex(color);
            case "rgb":
                return "rgb(" + red + ", " + green + ", " + blue + ")";
            case "rgba":
                return "rgba(" + red + ", " + green + ", " + blue + ", 1.0)";
            case "red":
                return String.valueOf(red);
            case "green":
                return String.valueOf(green);
            case "blue":
                return String.valueOf(blue);
            default:
                return null;
        }
    }

    /** {@code #rrggbb}, {@code rrggbb} or {@code #aarrggbb} as {@code 0xRRGGBB}, else {@code -1}. */
    private static int parseColor(String raw) {
        String digits = raw.trim();
        if (digits.startsWith("#")) digits = digits.substring(1);
        if (digits.length() == 8) digits = digits.substring(2);
        if (digits.length() != 6) return -1;
        try {
            return Integer.parseInt(digits, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String hex(int color) {
        return String.format("%06x", color);
    }

    private static Result failed(String reason) {
        return new Result(null, reason);
    }
}
