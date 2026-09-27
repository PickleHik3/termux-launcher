package com.termux.ai;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** The .npy reader against arrays written the way NumPy writes them, and straight out of an .npz. */
public class TaiNpyTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    /** A version-1 .npy exactly as numpy.save lays it out: magic, header padded to 64 bytes, data. */
    static byte[] npy(String descr, boolean fortran, int[] shape, float[] data) throws IOException {
        StringBuilder dims = new StringBuilder();
        for (int dim : shape) dims.append(dim).append(", ");
        String dict = "{'descr': '" + descr + "', 'fortran_order': " + (fortran ? "True" : "False")
            + ", 'shape': (" + dims.toString().trim() + "), }";
        int unpadded = 10 + dict.length() + 1;
        int padding = (64 - unpadded % 64) % 64;
        StringBuilder header = new StringBuilder(dict);
        for (int i = 0; i < padding; i++) header.append(' ');
        header.append('\n');
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {(byte) 0x93, 'N', 'U', 'M', 'P', 'Y', 1, 0});
        int length = header.length();
        out.write(length & 0xff);
        out.write(length >> 8);
        out.write(header.toString().getBytes(StandardCharsets.ISO_8859_1));
        ByteBuffer body = ByteBuffer.allocate(data.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : data) body.putFloat(value);
        out.write(body.array());
        return out.toByteArray();
    }

    @Test
    public void readsA2dFloat32ArrayAndItsRows() throws IOException {
        float[] data = new float[3 * 4];
        for (int i = 0; i < data.length; i++) data[i] = i * 0.5f - 1f;
        TaiNpy.FloatArray array = TaiNpy.read(new ByteArrayInputStream(npy("<f4", false, new int[] {3, 4}, data)));
        assertArrayEquals(new int[] {3, 4}, array.shape);
        assertArrayEquals(data, array.data, 0f);
        float[] row = new float[4];
        array.row(2, row);
        assertArrayEquals(new float[] {3f, 3.5f, 4f, 4.5f}, row, 0f);
    }

    @Test
    public void readsAnArrayLargerThanTheScratchBuffer() throws IOException {
        float[] data = new float[400 * 256];
        for (int i = 0; i < data.length; i++) data[i] = (float) Math.sin(i);
        TaiNpy.FloatArray array = TaiNpy.read(new ByteArrayInputStream(npy("<f4", false, new int[] {400, 256}, data)));
        assertArrayEquals(new int[] {400, 256}, array.shape);
        assertEquals(data[data.length - 1], array.data[data.length - 1], 0f);
        assertEquals(data[16_384], array.data[16_384], 0f);
    }

    @Test
    public void parsesTheHeaderShapesNumPyWrites() throws IOException {
        assertArrayEquals(new int[] {400, 256}, TaiNpy.parseHeader("{'descr': '<f4', 'fortran_order': False, 'shape': (400, 256), }"));
        assertArrayEquals(new int[] {7}, TaiNpy.parseHeader("{'descr': '<f4', 'fortran_order': False, 'shape': (7,), }"));
        assertArrayEquals(new int[] {}, TaiNpy.parseHeader("{'descr': '<f4', 'fortran_order': False, 'shape': (), }"));
    }

    @Test
    public void refusesWhatItCannotReadCorrectly() {
        for (String header : new String[] {
                "{'descr': '>f4', 'fortran_order': False, 'shape': (2,), }",
                "{'descr': '<f8', 'fortran_order': False, 'shape': (2,), }",
                "{'descr': '<f4', 'fortran_order': True, 'shape': (2, 2), }",
                "{'fortran_order': False, 'shape': (2,), }"}) {
            try {
                TaiNpy.parseHeader(header);
                fail("accepted " + header);
            } catch (IOException expected) {
                // The message names what was wrong; the caller reports it.
            }
        }
        try {
            TaiNpy.read(new ByteArrayInputStream("not numpy at all".getBytes(StandardCharsets.US_ASCII)));
            fail("accepted a file without the magic");
        } catch (IOException expected) {
        }
    }

    @Test
    public void truncatedDataIsAnError() throws IOException {
        byte[] whole = npy("<f4", false, new int[] {10}, new float[10]);
        byte[] cut = java.util.Arrays.copyOf(whole, whole.length - 8);
        try {
            TaiNpy.read(new ByteArrayInputStream(cut));
            fail("accepted truncated data");
        } catch (IOException expected) {
        }
    }

    @Test
    public void readsOneEntryOfAStoredNpzByName() throws IOException {
        float[] jasper = {1f, 2f, 3f, 4f};
        float[] rosie = {5f, 6f, 7f, 8f};
        File npz = tmp.newFile("voices.npz");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(npz))) {
            // numpy.savez writes stored (uncompressed) entries, which need their size and CRC up front.
            for (String[] pair : new String[][] {{"expr-voice-2-m.npy", "j"}, {"expr-voice-4-f.npy", "r"}}) {
                byte[] bytes = npy("<f4", false, new int[] {2, 2}, "j".equals(pair[1]) ? jasper : rosie);
                ZipEntry entry = new ZipEntry(pair[0]);
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(bytes.length);
                CRC32 crc = new CRC32();
                crc.update(bytes);
                entry.setCrc(crc.getValue());
                zip.putNextEntry(entry);
                zip.write(bytes);
                zip.closeEntry();
            }
        }
        assertArrayEquals(rosie, TaiNpy.readFromNpz(npz, "expr-voice-4-f").data, 0f);
        assertArrayEquals(jasper, TaiNpy.readFromNpz(npz, "expr-voice-2-m.npy").data, 0f);
        try {
            TaiNpy.readFromNpz(npz, "expr-voice-5-f");
            fail("found an entry that is not there");
        } catch (IOException expected) {
        }
    }
}
