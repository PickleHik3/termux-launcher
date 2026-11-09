package com.termux.terminal;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Feeds seeded random terminal input (text of every width, grapheme sequences, cursor motion,
 * insert and delete of characters and lines, erase, wrap, margins, resize, alternate screen) to
 * the whole emulator and compares a hash of the screen after every group of tokens with hashes
 * recorded before the parser speed-ups. A speed-up that renders anything differently changes a
 * hash. The recorded file is {@code parser-golden.txt}; to record it again (only after a change
 * that is meant to render differently), run with TERMUX_PARSER_GOLDEN_OUT set to the file to write.
 */
public class EmulatorParserGoldenTest extends TestCase {

    private static final int CASES = 3000;
    private static int sThrownCases;
    private static final String GOLDEN_RESOURCE = "/com/termux/terminal/parser-golden.txt";

    private static final String[] WORDS = {
        "the", "quick", "brown", "fox", "0123456789", "~/src/app $", "error:", "  ", "main.c:42:7:", "|", "-->",
    };
    private static final String[] WIDE = {
        "日本語", "中文字", "한국어", "ＡＢＣ", "あいう", "漢", "カタカナ", "𠬎", "𠬱",
    };
    private static final String[] EMOJI = {
        "😀", "🔥", "👍", "👍🏽", "🇺🇸", "🇩🇪🇫🇷", "👨‍👩‍👧", "❤️", "☺️", "🏳️‍🌈",
        "🧑‍💻", "1️⃣", "⌚",
    };
    private static final String[] NARROW_UNICODE = {
        "é", "ñ", "ü", "ß", "─┼─", "│", "█▓▒░", "λ", "π", "€", "£", "𝄞", "·",
    };
    private static final String[] COMBINING = {
        "é", "ä", "́", "̈́̂", "ñ", "​", "‍", "️", "กั",
        "क्ष", "각",
    };
    private static final String[] SGR = {
        "0", "1", "2", "3", "4", "7", "9", "22", "27", "31", "32", "34;1", "41", "42;37", "38;5;196",
        "48;5;21", "38;2;10;200;30", "48;2;200;10;30", "90", "100", "4:3", "58;5;9", "39", "49",
    };

    private static String number(Random random, int max) {
        return random.nextInt(4) == 0 ? "" : Integer.toString(random.nextInt(max + 1));
    }

    private static String run(Random random, String[] pool, int maxCount) {
        StringBuilder out = new StringBuilder();
        int count = 1 + random.nextInt(maxCount);
        for (int i = 0; i < count; i++) out.append(pool[random.nextInt(pool.length)]);
        return out.toString();
    }

    /** One token of input; a null return asks the caller to resize the terminal. */
    private static String token(Random random) {
        int roll = random.nextInt(100);
        if (roll < 22) return run(random, WORDS, 3) + (random.nextInt(3) == 0 ? " " : "");
        if (roll < 30) return run(random, WIDE, 4);
        if (roll < 36) return run(random, EMOJI, 3);
        if (roll < 42) return run(random, NARROW_UNICODE, 4);
        if (roll < 46) return run(random, COMBINING, 3);
        if (roll < 48) {
            StringBuilder ascii = new StringBuilder();
            int n = 1 + random.nextInt(40);
            for (int i = 0; i < n; i++) ascii.append((char) (0x20 + random.nextInt(0x5F)));
            return ascii.toString();
        }
        if (roll < 52) return random.nextBoolean() ? "\r\n" : (random.nextBoolean() ? "\n" : "\r");
        if (roll < 54) return random.nextBoolean() ? "\b" : "\t";
        if (roll < 58) return "\033[" + number(random, 6) + "ABCD".charAt(random.nextInt(4));
        if (roll < 61) return "\033[" + number(random, 12) + ";" + number(random, 30) + "H";
        if (roll < 63) return "\033[" + number(random, 30) + (random.nextBoolean() ? "G" : "d");
        if (roll < 67) return "\033[" + number(random, 6) + "@P".charAt(random.nextInt(2));
        if (roll < 69) return "\033[" + number(random, 8) + "X";
        if (roll < 72) return "\033[" + random.nextInt(3) + (random.nextBoolean() ? "K" : "J");
        if (roll < 75) return "\033[" + number(random, 4) + (random.nextBoolean() ? "L" : "M");
        if (roll < 82) return "\033[" + SGR[random.nextInt(SGR.length)] + "m";
        if (roll < 83) return "\033[" + number(random, 5) + "b";
        if (roll < 85) return random.nextBoolean() ? "\033[4h" : "\033[4l";
        if (roll < 87) return random.nextBoolean() ? "\033[?7h" : "\033[?7l";
        if (roll < 89) return "\033[" + number(random, 5) + ";" + number(random, 12) + "r";
        if (roll < 91) return random.nextBoolean() ? "\0337" : "\0338";
        if (roll < 93) return "\033" + "MDE".charAt(random.nextInt(3));
        if (roll < 94) return random.nextBoolean() ? "\033[?1049h" : "\033[?1049l";
        if (roll < 95) return "\033[?69h\033[" + number(random, 8) + ";" + number(random, 30) + "s";
        if (roll < 96) return "\033[?69l";
        if (roll < 97) return "\033[" + number(random, 6) + (random.nextBoolean() ? "S" : "T");
        return null;
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        return hash * 0x100000001B3L;
    }

    private static long snapshot(TerminalEmulator emulator, int rows) {
        TerminalBuffer screen = emulator.getScreen();
        long hash = 0xCBF29CE484222325L;
        for (int r = -screen.getActiveTranscriptRows(); r < rows; r++) {
            TerminalRow row = screen.mLines[screen.externalToInternalRow(r)];
            if (row == null) {
                hash = mix(hash, -1);
                continue;
            }
            hash = mix(hash, row.getSpaceUsed());
            for (int i = 0; i < row.getSpaceUsed(); i++) {
                hash = mix(hash, row.mText[i]);
                hash = mix(hash, row.getDisplayWidthAt(i));
            }
            for (int c = 0; c < row.mStyle.length; c++) {
                hash = mix(hash, row.getStyle(c));
                hash = mix(hash, row.getDecorationColor(c));
                hash = mix(hash, row.getHyperlinkId(c));
            }
            hash = mix(hash, row.mLineWrap ? 1 : 0);
        }
        hash = mix(hash, emulator.getCursorRow());
        hash = mix(hash, emulator.getCursorCol());
        hash = mix(hash, emulator.isAlternateBufferActive() ? 1 : 0);
        return hash;
    }

    private static final class Silent extends TerminalOutput {
        @Override
        public void write(byte[] data, int offset, int count) {
        }

        @Override
        public void titleChanged(String oldTitle, String newTitle) {
        }

        @Override
        public void onCopyTextToClipboard(String text) {
        }

        @Override
        public void onPasteTextFromClipboard() {
        }

        @Override
        public void onBell() {
        }

        @Override
        public void onColorsChanged() {
        }
    }

    /** The hash of case {@code index}: the screen after every group of tokens, chained. */
    private static long runCase(int index) {
        Random random = new Random(0x5EED0000L + index);
        int columns = 4 + random.nextInt(40);
        int rows = 3 + random.nextInt(12);
        TerminalEmulator emulator = new TerminalEmulator(new Silent(), false, columns, rows, 13, 15, 40, null);
        long chain = 0;
        int tokens = 20 + random.nextInt(90);
        for (int t = 0; t < tokens; ) {
            StringBuilder group = new StringBuilder();
            int groupSize = 1 + random.nextInt(6);
            boolean resize = false;
            for (int g = 0; g < groupSize && t < tokens; g++, t++) {
                String text = token(random);
                if (text == null) {
                    resize = true;
                    break;
                }
                group.append(text);
            }
            byte[] bytes = group.toString().getBytes(StandardCharsets.UTF_8);
            try {
                // Split at arbitrary byte positions, including inside a UTF-8 sequence.
                for (int offset = 0; offset < bytes.length; ) {
                    int size = Math.min(bytes.length - offset, 1 + random.nextInt(48));
                    byte[] piece = new byte[size];
                    System.arraycopy(bytes, offset, piece, 0, size);
                    emulator.append(piece, size);
                    offset += size;
                }
                if (resize) {
                    columns = Math.max(2, columns + random.nextInt(9) - 4);
                    rows = Math.max(2, rows + random.nextInt(5) - 2);
                    emulator.resize(columns, rows, 13, 15);
                }
                chain = mix(chain, snapshot(emulator, rows));
            } catch (RuntimeException e) {
                // Some random input reaches an exception the emulator already had (a block fill
                // past a margin). It must throw the same one afterwards; the case ends there.
                for (char c : (e.getClass().getName() + e.getMessage()).toCharArray()) chain = mix(chain, c);
                sThrownCases++;
                break;
            }
        }
        return chain;
    }

    private static List<Long> readGolden() throws IOException {
        InputStream stream = EmulatorParserGoldenTest.class.getResourceAsStream(GOLDEN_RESOURCE);
        assertNotNull("missing test resource " + GOLDEN_RESOURCE, stream);
        List<Long> hashes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.isEmpty()) hashes.add(Long.parseUnsignedLong(line, 16));
            }
        }
        return hashes;
    }

    public void testScreensMatchTheRecordedHashes() throws IOException {
        String writeTo = System.getenv("TERMUX_PARSER_GOLDEN_OUT");
        if (writeTo != null && !writeTo.isEmpty()) {
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < CASES; i++) out.append(Long.toHexString(runCase(i))).append('\n');
            Files.write(Paths.get(writeTo), out.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println("PARSER-GOLDEN cases that ended in an exception: " + sThrownCases);
            return;
        }
        List<Long> golden = readGolden();
        assertEquals("recorded cases", CASES, golden.size());
        Set<Long> distinct = new HashSet<>(golden);
        assertTrue("recorded hashes should differ from case to case", distinct.size() > CASES * 9 / 10);
        for (int i = 0; i < CASES; i++) {
            assertEquals("case " + i + " renders differently", (long) golden.get(i), runCase(i));
        }
    }
}
