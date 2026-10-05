package com.termux.ai;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The shared diagnostics file never carries the remote provider's key or a bearer value. */
public class TaiDiagnosticsRedactionTest {

    @Test
    public void diagnostics_scrubTheRemoteKey() {
        String key = "sk-proj-AbCdEf0123456789XYZ";
        String events = "2026-10-05T10:00:00 api_error backend=remote reason=\"Could not reach host: " + key + "\"\n"
            + "2026-10-05T10:00:01 api_error reason=\"Authorization: Bearer " + key + "\"\n";
        String text = TaiDiagnostics.compose(0L, events, "{\"history\":[]}", null);
        assertTrue(text.contains(key));

        String shared = TaiDiagnostics.redactSecrets(text, key);
        assertFalse(shared, shared.contains(key));
        assertTrue(shared, shared.contains("api_error"));
    }

    @Test
    public void diagnostics_scrubBearerValuesWithoutAKnownKey() {
        String text = TaiDiagnostics.compose(0L, "x Authorization: Bearer leaked-token-123\n", "{}", null);
        String shared = TaiDiagnostics.redactSecrets(text, (String) null);
        assertFalse(shared, shared.contains("leaked-token-123"));
    }
}
