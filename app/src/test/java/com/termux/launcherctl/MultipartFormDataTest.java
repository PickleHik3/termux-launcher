package com.termux.launcherctl;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The multipart reader behind /v1/audio/transcriptions: fields, a binary file part, and malformed bodies. */
public class MultipartFormDataTest {
    private static final String BOUNDARY = "----WebKitFormBoundary7MA4YWxkTrZu0gW";

    private static byte[] body(byte[] audio) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\nwhisper-acft-base-en\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"clip.wav\"\r\nContent-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(audio);
        out.write(("\r\n--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"prompt_mode\"\r\n\r\nterminal\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    @Test
    public void fieldsAndTheBinaryFileComeThroughExactly() throws IOException {
        // Bytes a text decode would mangle: CR LF, NUL, 0xFF, and a lone "--" that is not a boundary.
        byte[] audio = {'R', 'I', 'F', 'F', 0, (byte) 0xFF, '\r', '\n', '-', '-', 'x', '\r', '\n', 1, 2};
        Map<String, MultipartFormData.Part> parts = MultipartFormData.parse(body(audio), "multipart/form-data; boundary=" + BOUNDARY);

        assertEquals(3, parts.size());
        assertEquals("whisper-acft-base-en", parts.get("model").text());
        assertNull(parts.get("model").filename);
        assertEquals("terminal", parts.get("prompt_mode").text());
        MultipartFormData.Part file = parts.get("file");
        assertEquals("clip.wav", file.filename);
        assertEquals("audio/wav", file.contentType);
        assertArrayEquals(audio, file.data);
    }

    @Test
    public void aQuotedBoundaryAndAnEmptyFileAreHandled() throws IOException {
        Map<String, MultipartFormData.Part> parts = MultipartFormData.parse(body(new byte[0]),
            "multipart/form-data; boundary=\"" + BOUNDARY + "\"");
        assertEquals(0, parts.get("file").data.length);
        assertEquals(BOUNDARY, MultipartFormData.boundary("multipart/form-data; charset=utf-8; boundary=\"" + BOUNDARY + "\""));
    }

    @Test
    public void malformedBodiesAreRefusedNotGuessed() throws IOException {
        try {
            MultipartFormData.parse(body(new byte[] {1}), "multipart/form-data");
            fail("no boundary");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("boundary"));
        }
        try {
            MultipartFormData.parse("not multipart at all".getBytes(StandardCharsets.UTF_8), "multipart/form-data; boundary=" + BOUNDARY);
            fail("body without the boundary");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("boundary"));
        }
        try {
            MultipartFormData.parse(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"\r\n\r\nunterminated")
                .getBytes(StandardCharsets.UTF_8), "multipart/form-data; boundary=" + BOUNDARY);
            fail("unterminated part");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("not closed"));
        }
    }

    @Test
    public void contentTypeDetectionIsCaseInsensitive() {
        assertTrue(MultipartFormData.isMultipart("Multipart/Form-Data; boundary=x"));
        assertFalse(MultipartFormData.isMultipart("application/json"));
        assertFalse(MultipartFormData.isMultipart(null));
        assertNull(MultipartFormData.boundary("application/json"));
    }
}
