package com.termux.terminal;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

/** Reproducible mutation harness for fragmented, untrusted terminal byte streams. */
public class EscapeSequenceFuzzTest extends TerminalTestCase {

    private static final long DEFAULT_SEED = 0x5EED_C0DEL;
    private static final int DEFAULT_CASES = 750;
    private static final int DEFAULT_MAX_BYTES = 2048;
    private static final byte[][] INTRODUCERS = {
        {27, '['}, {27, ']'}, {27, 'P'}, {27, '_'}
    };

    public void testMutatedEscapeStreamsPreserveInvariantsAndRecover() {
        long seed = longProperty("termux.fuzz.seed", DEFAULT_SEED);
        int cases = intProperty("termux.fuzz.cases", DEFAULT_CASES, 1, 100_000);
        int maxBytes = intProperty("termux.fuzz.maxBytes", DEFAULT_MAX_BYTES, 32, 1_000_000);
        Random random = new Random(seed);

        for (int caseIndex = 0; caseIndex < cases; caseIndex++) {
            withTerminalSized(24, 6);
            byte[] input = makeCase(random, maxBytes);
            try {
                appendFragmented(input, random);
                // CAN closes every parser state; RIS then gives every case the same recovery point.
                appendFragmented("\030\033cFUZZ_OK".getBytes(StandardCharsets.UTF_8), random);
                assertInvariants();
                assertTrue(mTerminal.getScreen().getTranscriptText().contains("FUZZ_OK"));
            } catch (RuntimeException | AssertionError failure) {
                AssertionError contextual = new AssertionError("seed=" + seed + ", case=" + caseIndex
                    + ", bytes=" + toHex(input));
                contextual.initCause(failure);
                throw contextual;
            }
        }
    }

    private void appendFragmented(byte[] input, Random random) {
        for (int offset = 0; offset < input.length;) {
            int length = Math.min(input.length - offset, 1 + random.nextInt(23));
            byte[] fragment = Arrays.copyOfRange(input, offset, offset + length);
            mTerminal.append(fragment, fragment.length);
            offset += length;
        }
    }

    private static byte[] makeCase(Random random, int maxBytes) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int target = 1 + random.nextInt(maxBytes);
        while (output.size() < target) {
            if (random.nextInt(7) == 0) {
                byte[] introducer = INTRODUCERS[random.nextInt(INTRODUCERS.length)];
                output.write(introducer, 0, introducer.length);
            } else {
                output.write(randomPayloadByte(random));
            }
        }
        switch(random.nextInt(5)) {
            case 0:
                output.write(7); // BEL terminates OSC.
                break;
            case 1:
                output.write(27);
                output.write('\\'); // ST terminates every string sequence.
                break;
            case 2:
                output.write(24); // CAN.
                break;
            case 3:
                output.write(26); // SUB.
                break;
            default:
                break; // Deliberately leave the final sequence fragmented/unterminated.
        }
        return output.toByteArray();
    }

    private static int randomPayloadByte(Random random) {
        switch(random.nextInt(6)) {
            case 0: return '0' + random.nextInt(10);
            case 1:
                String parameterBytes = ";:?<=>!$*\"'";
                return parameterBytes.charAt(random.nextInt(parameterBytes.length()));
            case 2: return 0x20 + random.nextInt(0x5f);
            case 3: return random.nextInt(0x20);
            default: return random.nextInt(256);
        }
    }

    private static long longProperty(String name, long defaultValue) {
        String value = System.getProperty(name);
        return value == null ? defaultValue : Long.decode(value);
    }

    private static int intProperty(String name, int defaultValue, int minimum, int maximum) {
        String value = System.getProperty(name);
        int parsed = value == null ? defaultValue : Integer.parseInt(value);
        if (parsed < minimum || parsed > maximum)
            throw new IllegalArgumentException(name + " must be in [" + minimum + ", " + maximum + "]");
        return parsed;
    }

    private static String toHex(byte[] input) {
        StringBuilder result = new StringBuilder(input.length * 2);
        for (byte value : input) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }
}
