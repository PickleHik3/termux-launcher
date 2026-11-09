package com.termux.app.chrome.scene;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a pasted fragment shader into source the GL compiler takes, under the Shadertoy contract
 * with ShaderEditor's names as aliases (spec shader-video-wallpapers-2026-11-09, decision 3). Pure.
 *
 * <p>Which dialect: a {@code #version} line decides when there is one (300 and up is GLSL ES 3.00,
 * anything lower is ES 1.00); without one a {@code void main()} is a ShaderEditor-style ES 1.00
 * shader writing {@code gl_FragColor}, and anything else is a Shadertoy-style {@code mainImage}
 * compiled as {@code #version 300 es}. When the text defines {@code mainImage} and no {@code main},
 * a {@code main} is appended that calls it with {@code gl_FragCoord.xy} and writes the colour with
 * alpha 1 (the scene surface is opaque).</p>
 *
 * <p>The prelude declares a uniform only when the text names it and does not declare it itself:
 * ShaderEditor shaders declare their own {@code uniform vec2 resolution;}, and a second declaration
 * would not compile; Shadertoy code is free to use {@code time} as a local. The renderer sets only
 * the uniforms the linked program has, and only when their type is the contract's.</p>
 *
 * <p>Line numbers: the {@code #version} and {@code #extension} lines are moved to the top (their
 * own lines left blank, so the user's lines keep their numbers), the prelude follows, then the
 * user's text. {@link Built#userLine} maps a compiler line back to the user's.</p>
 */
public final class ShaderPrelude {

    public enum Flavour {
        /** {@code #version 300 es}, the generated {@code main} calls {@code mainImage}. */
        ES3_MAIN_IMAGE,
        /** {@code #version 300 es} (or higher) with the user's own {@code main}. */
        ES3_MAIN,
        /** GLSL ES 1.00 with the user's own {@code main} writing {@code gl_FragColor}. */
        ES1_MAIN,
        /** GLSL ES 1.00 ({@code #version 100}), the generated {@code main} calls {@code mainImage}. */
        ES1_MAIN_IMAGE;

        public boolean es3() {
            return this == ES3_MAIN_IMAGE || this == ES3_MAIN;
        }

        public boolean wrapped() {
            return this == ES3_MAIN_IMAGE || this == ES1_MAIN_IMAGE;
        }
    }

    /** One uniform the prelude may declare: its GLSL type, name and array length (0 for none). */
    public static final class Uniform {
        @NonNull public final String type;
        @NonNull public final String name;
        public final int arrayLength;

        Uniform(@NonNull String type, @NonNull String name, int arrayLength) {
            this.type = type;
            this.name = name;
            this.arrayLength = arrayLength;
        }

        @NonNull
        String declaration() {
            return "uniform " + type + " " + name + (arrayLength > 0 ? "[" + arrayLength + "]" : "") + ";";
        }
    }

    /** ShaderEditor's {@code pointers[]} length (its {@code MAX_POINTERS}). */
    public static final int MAX_POINTERS = 10;
    public static final int CHANNELS = 4;

    // Indices into UNIFORMS, for the renderer's location table.
    public static final int I_RESOLUTION = 0, I_TIME = 1, I_TIME_DELTA = 2, I_FRAME = 3,
        I_FRAME_RATE = 4, I_MOUSE = 5, I_DATE = 6, I_CHANNEL0 = 7, I_CHANNEL_RESOLUTION = 11,
        A_RESOLUTION = 12, A_TIME = 13, A_TOUCH = 14, A_POINTER_COUNT = 15, A_POINTERS = 16,
        A_BATTERY = 17, A_OFFSET = 18, A_DATE = 19, TL_PAGE_OFFSET = 20, TL_BATTERY = 21,
        TL_KEYBOARD = 22, TL_DARK = 23, TL_COLOR0 = 24;

    /**
     * Every uniform of the contract, in prelude order. Shadertoy: iResolution .. iChannelResolution.
     * ShaderEditor aliases, with ShaderEditor's own types (checked against markusfisch/ShaderEditor
     * {@code ShaderRenderer}, {@code BuiltinUniforms}, {@code BuiltinSystemUniforms}, MIT):
     * resolution vec2 px, time float s, touch vec2 px (y up), pointerCount int, pointers vec3[10]
     * (x, y px, touch size), battery float 0..1, offset vec2 (wallpaper offset), date vec4 (year,
     * month from 0, day, seconds since midnight). Launcher extras: tlPageOffset 0..1, tlBattery
     * 0..1, tlKeyboard (fraction of the height), tlDark 0/1, tlColor0..2 (accent, surface,
     * on-surface; sRGB 0..1).
     */
    public static final Uniform[] UNIFORMS = {
        new Uniform("vec3", "iResolution", 0),
        new Uniform("float", "iTime", 0),
        new Uniform("float", "iTimeDelta", 0),
        new Uniform("int", "iFrame", 0),
        new Uniform("float", "iFrameRate", 0),
        new Uniform("vec4", "iMouse", 0),
        new Uniform("vec4", "iDate", 0),
        new Uniform("sampler2D", "iChannel0", 0),
        new Uniform("sampler2D", "iChannel1", 0),
        new Uniform("sampler2D", "iChannel2", 0),
        new Uniform("sampler2D", "iChannel3", 0),
        new Uniform("vec3", "iChannelResolution", CHANNELS),
        new Uniform("vec2", "resolution", 0),
        new Uniform("float", "time", 0),
        new Uniform("vec2", "touch", 0),
        new Uniform("int", "pointerCount", 0),
        new Uniform("vec3", "pointers", MAX_POINTERS),
        new Uniform("float", "battery", 0),
        new Uniform("vec2", "offset", 0),
        new Uniform("vec4", "date", 0),
        new Uniform("float", "tlPageOffset", 0),
        new Uniform("float", "tlBattery", 0),
        new Uniform("float", "tlKeyboard", 0),
        new Uniform("float", "tlDark", 0),
        new Uniform("vec3", "tlColor0", 0),
        new Uniform("vec3", "tlColor1", 0),
        new Uniform("vec3", "tlColor2", 0),
    };

    /** The output the generated ES 3 {@code main} writes. */
    static final String OUT_NAME = "tlFragColor";

    /** What the compiler is handed, and how to read its line numbers back. */
    public static final class Built {
        @NonNull public final String source;
        @NonNull public final Flavour flavour;
        /** Compiler line {@code n} past the prelude is the user's line {@code n - lineOffset}. */
        public final int lineOffset;
        /** Lines in the user's text. */
        public final int userLines;
        /** The user's line of each line moved to the top ({@code #version}, {@code #extension}), in order. */
        @NonNull private final int[] hoisted;

        Built(@NonNull String source, @NonNull Flavour flavour, int lineOffset, int userLines,
              @NonNull int[] hoisted) {
            this.source = source;
            this.flavour = flavour;
            this.lineOffset = lineOffset;
            this.userLines = userLines;
            this.hoisted = hoisted;
        }

        /** The user's line for compiler line {@code compiledLine}, or -1 when it falls in the prelude or the generated main. */
        public int userLine(int compiledLine) {
            if (compiledLine >= 1 && compiledLine <= hoisted.length) return hoisted[compiledLine - 1];
            int line = compiledLine - lineOffset;
            return line >= 1 && line <= userLines ? line : -1;
        }
    }

    private static final Pattern VERSION = Pattern.compile("^[ \\t]*#[ \\t]*version[ \\t]+(\\d+)[^\\n]*", Pattern.MULTILINE);
    private static final Pattern EXTENSION = Pattern.compile("^[ \\t]*#[ \\t]*extension\\b[^\\n]*", Pattern.MULTILINE);
    private static final Pattern MAIN_IMAGE = Pattern.compile("\\bvoid\\s+mainImage\\s*\\(");
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(void\\s*)?\\)");
    private static final Pattern UNIFORM_LIST = Pattern.compile("\\buniform\\s+([^;]*);");
    private static final String TYPES =
        "(?:float|int|uint|bool|[biu]?vec[234]|mat[234](?:x[234])?|sampler2D|samplerCube|sampler3D)";

    private ShaderPrelude() {}

    @NonNull
    public static Built build(@NonNull String userSource) {
        String text = normalise(userSource);
        String code = stripComments(text);

        Matcher version = VERSION.matcher(code);
        int versionNumber = -1;
        int versionStart = -1;
        if (version.find()) {
            versionNumber = parseInt(version.group(1));
            versionStart = version.start();
        }
        boolean mainImage = MAIN_IMAGE.matcher(code).find();
        boolean main = MAIN.matcher(code).find();
        Flavour flavour;
        if (versionNumber >= 0) {
            boolean es3 = versionNumber >= 300;
            boolean wrap = mainImage && !main;
            flavour = es3 ? (wrap ? Flavour.ES3_MAIN_IMAGE : Flavour.ES3_MAIN)
                : (wrap ? Flavour.ES1_MAIN_IMAGE : Flavour.ES1_MAIN);
        } else {
            flavour = main ? Flavour.ES1_MAIN : Flavour.ES3_MAIN_IMAGE;
        }

        // Lines moved to the top: the #version line, then every #extension line, by user line.
        String[] lines = text.split("\n", -1);
        String[] codeLines = code.split("\n", -1);
        List<String> prelude = new ArrayList<>();
        List<Integer> hoisted = new ArrayList<>();
        if (versionStart >= 0) {
            int line = lineOf(code, versionStart);
            prelude.add(codeLines[line - 1].trim());
            hoisted.add(line);
            lines[line - 1] = "";
        } else if (flavour.es3()) {
            prelude.add("#version 300 es");
        }
        Matcher ext = EXTENSION.matcher(code);
        while (ext.find()) {
            int line = lineOf(code, ext.start());
            prelude.add(codeLines[line - 1].trim());
            hoisted.add(line);
            lines[line - 1] = "";
        }
        // Our own #version line is not the user's: it maps to no user line.
        int[] origin = new int[prelude.size()];
        int h = 0;
        for (int i = 0; i < origin.length; i++) {
            boolean ours = versionStart < 0 && flavour.es3() && i == 0;
            origin[i] = ours ? -1 : hoisted.get(h++);
        }

        if (flavour.es3()) {
            prelude.add("precision highp float;");
            prelude.add("precision highp int;");
        } else {
            prelude.add("#ifdef GL_FRAGMENT_PRECISION_HIGH");
            prelude.add("precision highp float;");
            prelude.add("#else");
            prelude.add("precision mediump float;");
            prelude.add("#endif");
        }
        for (Uniform u : UNIFORMS) {
            if (mentions(code, u.name) && !declares(code, u.name)) prelude.add(u.declaration());
        }

        StringBuilder out = new StringBuilder(text.length() + 1024);
        for (String l : prelude) out.append(l).append('\n');
        for (int i = 0; i < lines.length; i++) {
            out.append(lines[i]);
            if (i < lines.length - 1) out.append('\n');
        }
        out.append('\n');
        if (flavour == Flavour.ES3_MAIN_IMAGE) {
            out.append("out vec4 ").append(OUT_NAME).append(";\n")
                .append("void main() {\n")
                .append("    vec4 c = vec4(0.0);\n")
                .append("    mainImage(c, gl_FragCoord.xy);\n")
                .append("    ").append(OUT_NAME).append(" = vec4(c.rgb, 1.0);\n")
                .append("}\n");
        } else if (flavour == Flavour.ES1_MAIN_IMAGE) {
            out.append("void main() {\n")
                .append("    vec4 c = vec4(0.0);\n")
                .append("    mainImage(c, gl_FragCoord.xy);\n")
                .append("    gl_FragColor = vec4(c.rgb, 1.0);\n")
                .append("}\n");
        }
        return new Built(out.toString(), flavour, prelude.size(), lines.length, origin);
    }

    @NonNull
    private static String normalise(@NonNull String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** Whether the code (comments stripped) uses {@code name} as a word. */
    static boolean mentions(@NonNull String code, @NonNull String name) {
        return Pattern.compile("\\b" + name + "\\b").matcher(code).find();
    }

    /**
     * Whether the code declares {@code name} itself: a {@code uniform} list naming it, a typed
     * declaration or function ({@code float time}, {@code vec2 offset(}), or a {@code #define}.
     */
    static boolean declares(@NonNull String code, @NonNull String name) {
        if (Pattern.compile("\\b" + TYPES + "\\s+" + name + "\\b").matcher(code).find()) return true;
        if (Pattern.compile("#\\s*define\\s+" + name + "\\b").matcher(code).find()) return true;
        Matcher list = UNIFORM_LIST.matcher(code);
        Pattern word = Pattern.compile("(?:^|[\\s,])" + name + "\\s*(?:\\[[^\\]]*\\])?\\s*(?:,|$)");
        while (list.find()) {
            if (word.matcher(list.group(1)).find()) return true;
        }
        return false;
    }

    /** Comments replaced by spaces, newlines kept, so offsets and line numbers stay the user's. */
    @NonNull
    static String stripComments(@NonNull String text) {
        StringBuilder out = new StringBuilder(text.length());
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                while (i < n && text.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                out.append("  ");
                i += 2;
                while (i < n && !(text.charAt(i) == '*' && i + 1 < n && text.charAt(i + 1) == '/')) {
                    out.append(text.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                if (i < n) {
                    out.append("  ");
                    i += 2;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int lineOf(@NonNull String text, int offset) {
        int line = 1;
        for (int i = 0; i < offset; i++) if (text.charAt(i) == '\n') line++;
        return line;
    }

    private static int parseInt(@NonNull String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
