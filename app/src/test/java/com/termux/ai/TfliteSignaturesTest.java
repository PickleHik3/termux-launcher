package com.termux.ai;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Signature names read from the {@code .tflite} flatbuffer: a model built here byte by byte in
 * the TFLite schema's layout (the Whisper graphs' two signatures, the export's odd input order
 * kept), the app's own Silero VAD graph, and malformed files.
 */
public class TfliteSignaturesTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void readsWhisperShapedSignaturesInFileOrder() throws Exception {
        byte[] model = new ModelWriter().model(
            sig("encode", new String[] {"args_0"}, new String[] {"output_0"}),
            sig("decode", new String[] {"args_2", "args_0", "args_1"}, new String[] {"output_0"}));
        File file = tmp.newFile("whisper.tflite");
        Files.write(file.toPath(), model);

        Map<String, TfliteSignatures.Signature> read = TfliteSignatures.read(file);
        assertEquals(Arrays.asList("encode", "decode"), new ArrayList<>(read.keySet()));
        assertArrayEquals(new String[] {"args_0"}, read.get("encode").inputs);
        assertArrayEquals(new String[] {"output_0"}, read.get("encode").outputs);
        assertArrayEquals(new String[] {"args_2", "args_0", "args_1"}, read.get("decode").inputs);
        assertArrayEquals(new String[] {"output_0"}, read.get("decode").outputs);
    }

    @Test
    public void aModelWithoutSignaturesHasNone() throws Exception {
        byte[] model = new ModelWriter().model();
        assertTrue(TfliteSignatures.read(ByteBuffer.wrap(model)).isEmpty());
    }

    @Test
    public void theBundledSileroGraphReadsWithoutError() throws Exception {
        // A real converter output: signature_defs present, and empty.
        File silero = new File("src/main/assets/vad/silero_vad_v5.tflite");
        if (!silero.isFile()) silero = new File("app/src/main/assets/vad/silero_vad_v5.tflite");
        assertTrue("silero graph not found from " + new File(".").getAbsolutePath(), silero.isFile());
        assertTrue(TfliteSignatures.read(silero).isEmpty());
    }

    @Test
    public void truncatedAndGarbageFilesAreIoErrors() {
        byte[] model = new ModelWriter().model(sig("encode", new String[] {"a"}, new String[] {"b"}));
        for (int length : new int[] {0, 3, 8, model.length / 2, model.length - 6}) {
            try {
                TfliteSignatures.read(ByteBuffer.wrap(Arrays.copyOf(model, length)));
                fail("length " + length + " should not parse");
            } catch (IOException expected) {
                // Bounds-checked, never a wild read.
            }
        }
        try {
            TfliteSignatures.read(ByteBuffer.wrap("not a flatbuffer at all".getBytes(StandardCharsets.UTF_8)));
            fail();
        } catch (IOException expected) {
            // As above.
        }
    }

    private static String[][] sig(String key, String[] inputs, String[] outputs) {
        return new String[][] {{key}, inputs, outputs};
    }

    /**
     * Writes front to back: every uoffset points forward, every vtable sits just before its
     * table, so the result is a valid flatbuffer of the TFLite schema's {@code Model}.
     */
    private static final class ModelWriter {
        private final ByteBuffer out = ByteBuffer.allocate(1 << 16).order(ByteOrder.LITTLE_ENDIAN);

        private int alloc(int bytes) {
            int at = out.position();
            out.position(at + ((bytes + 3) & ~3));
            return at;
        }

        /** Points the uoffset at {@code from} to {@code to} (always forward). */
        private void link(int from, int to) {
            out.putInt(from, to - from);
        }

        /** A table of {@code fields} uoffset slots with only {@code present} set; its start and slots. */
        private int[] table(int fields, int... present) {
            int vtableSize = 4 + 2 * fields;
            int vtable = alloc(vtableSize);
            int table = alloc(4 + 4 * fields);
            out.putShort(vtable, (short) vtableSize);
            out.putShort(vtable + 2, (short) (4 + 4 * fields));
            out.putInt(table, table - vtable);
            int[] startAndSlots = new int[1 + fields];
            startAndSlots[0] = table;
            for (int field : present) {
                out.putShort(vtable + 4 + 2 * field, (short) (4 + 4 * field));
                startAndSlots[1 + field] = table + 4 + 4 * field;
            }
            return startAndSlots;
        }

        private int string(String value) {
            byte[] raw = value.getBytes(StandardCharsets.UTF_8);
            int at = alloc(4 + raw.length + 1);
            out.putInt(at, raw.length);
            for (int i = 0; i < raw.length; i++) out.put(at + 4 + i, raw[i]);
            return at;
        }

        /** A vector of {@code count} uoffsets that {@code from} points to; its element positions. */
        private int[] vector(int from, int count) {
            int at = alloc(4 + 4 * count);
            link(from, at);
            out.putInt(at, count);
            int[] slots = new int[count];
            for (int i = 0; i < count; i++) slots[i] = at + 4 + 4 * i;
            return slots;
        }

        /** A {@code [TensorMap]} of these names at the slot {@code from}. */
        private void names(int from, String[] names) {
            int[] slots = vector(from, names.length);
            for (int i = 0; i < names.length; i++) {
                int[] map = table(2, 0);
                link(slots[i], map[0]);
                link(map[1], string(names[i]));
            }
        }

        /** {@code Model} with {@code signature_defs} (field 7) when any are given. */
        byte[] model(String[][]... signatures) {
            int root = alloc(4);
            int[] model = signatures.length == 0 ? table(8) : table(8, 7);
            link(root, model[0]);
            if (signatures.length > 0) {
                int[] defs = vector(model[1 + 7], signatures.length);
                for (int i = 0; i < signatures.length; i++) {
                    int[] def = table(5, 0, 1, 2);
                    link(defs[i], def[0]);
                    names(def[1], signatures[i][1]);
                    names(def[2], signatures[i][2]);
                    link(def[3], string(signatures[i][0][0]));
                }
            }
            return Arrays.copyOf(out.array(), out.position());
        }
    }
}
