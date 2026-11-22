package com.termux.privileged.lane;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class RootLaneCommandsTest {

    private static final String DIGEST = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String DEST = "'/data/local/tmp/tl/bin/btop-0123456789ab'";

    @Test
    public void quoteEscapesEmbeddedQuotes() {
        assertEquals("'a'\\''b'", RootLaneCommands.quote("a'b"));
        assertEquals("''", RootLaneCommands.quote(""));
    }

    @Test
    public void stageScriptQuotesEveryPath() {
        String s = RootLaneCommands.stageScript("/data/user/0/x/cache/it's.tmp", "btop", DIGEST);
        assertTrue(s, s.contains("cp '/data/user/0/x/cache/it'\\''s.tmp' '/data/local/tmp/tl/bin/btop-0123456789ab.new'"));
        assertTrue(s, s.contains("mkdir -p '/data/local/tmp/tl/bin'"));
        assertTrue(s, s.startsWith("umask 077 && "));
        assertTrue(s, s.endsWith("echo " + RootLaneCommands.STAGED_MARKER));
    }

    @Test
    public void stageScriptStepsRunInOrder() {
        String s = RootLaneCommands.stageScript("/tmp/x", "btop", DIGEST);
        int mkdir = s.indexOf("mkdir -p");
        int copy = s.indexOf("cp '/tmp/x'");
        int older = s.indexOf("for f in");
        int chown = s.indexOf("chown -R 2000:2000 '/data/local/tmp/tl'");
        int dirs = s.indexOf("chmod 0700 '/data/local/tmp/tl' '/data/local/tmp/tl/bin'");
        int exec = s.indexOf("chmod 0755 " + DEST);
        assertTrue(s, mkdir >= 0 && mkdir < copy && copy < older && older < chown && chown < dirs && dirs < exec);
    }

    @Test
    public void stageScriptSkipsCopyWhenDigestMatches() {
        String s = RootLaneCommands.stageScript("/tmp/x", "btop", DIGEST);
        assertTrue(s, s.contains("if [ \"$(sha256sum " + DEST + " 2>/dev/null | cut -d' ' -f1)\" != '" + DIGEST + "' ]; then cp"));
    }

    @Test
    public void stageScriptRemovesOnlyOlderCopiesOfTheSameName() {
        String s = RootLaneCommands.stageScript("/tmp/x", "btop", DIGEST);
        assertTrue(s, s.contains("for f in '/data/local/tmp/tl/bin/btop-'????????????; do [ \"$f\" = " + DEST));
    }

    @Test
    public void stageScriptRefusesBadNamesBeforeAnyCommand() {
        String[] bad = {"btop'; rm -rf /; '", "../x", "a b", "", ".hidden", "a/b", "x$(id)"};
        for (String name : bad) {
            try {
                RootLaneCommands.stageScript("/tmp/x", name, DIGEST);
                fail("accepted " + name);
            } catch (IllegalArgumentException expected) {
                // refused
            }
        }
    }

    @Test
    public void stageScriptRefusesBadDigests() {
        for (String digest : new String[] {"", "zz", DIGEST + "0", "abc'; id; '"}) {
            try {
                RootLaneCommands.stageScript("/tmp/x", "btop", digest);
                fail("accepted " + digest);
            } catch (IllegalArgumentException expected) {
                // refused
            }
        }
    }

    @Test
    public void stagedOkNeedsTheMarker() {
        assertTrue(RootLaneCommands.stagedOk(RootLaneCommands.STAGED_MARKER + "\n"));
        assertFalse(RootLaneCommands.stagedOk("Error (1): cp failed"));
        assertFalse(RootLaneCommands.stagedOk(null));
    }

    @Test
    public void spawnArgvIsSuAsShellWithUmaskAndCleanEnv() {
        String[] argv = RootLaneCommands.spawnArgv("/data/local/tmp/tl/bin/btop-0123456789ab",
            new String[] {"-p", "1"}, null);
        assertEquals(4, argv.length);
        assertEquals("su", argv[0]);
        assertEquals("2000", argv[1]);
        assertEquals("-c", argv[2]);
        assertEquals("umask 077; mkdir -p '/data/local/tmp/tl/home/btop' && cd '/data/local/tmp/tl/home/btop'"
            + " && exec env -i HOME='/data/local/tmp/tl/home/btop' TERM='xterm-256color' LANG='C.UTF-8'"
            + " PATH='/system/bin:/system/xbin' TMPDIR='/data/local/tmp'"
            + " '/data/local/tmp/tl/bin/btop-0123456789ab' '-p' '1'", argv[3]);
    }

    @Test
    public void spawnQuotesArgsWithSpacesAndQuotes() {
        String script = RootLaneCommands.spawnScript("/data/local/tmp/tl/bin/t-0123456789ab",
            new String[] {"a b", "it's", "$(id)", ""}, null);
        assertTrue(script, script.endsWith("'a b' 'it'\\''s' '$(id)' ''"));
    }

    @Test
    public void extraEnvOverridesByKeyAndIsQuoted() {
        String script = RootLaneCommands.spawnScript("/data/local/tmp/tl/bin/t-0123456789ab", null,
            new String[] {"TERM=screen", "COLUMNS=a b", "BAD", "=x", "Q=it's"});
        assertTrue(script, script.contains(" TERM='screen' "));
        assertFalse(script, script.contains("xterm-256color"));
        assertTrue(script, script.contains(" COLUMNS='a b' "));
        assertTrue(script, script.contains(" Q='it'\\''s' "));
        assertFalse(script, script.contains("BAD"));
        assertArrayEquals(new String[] {"su", "2000", "-c"},
            java.util.Arrays.copyOf(RootLaneCommands.spawnArgv("/data/local/tmp/tl/bin/t-0123456789ab", null, null), 3));
    }
}
