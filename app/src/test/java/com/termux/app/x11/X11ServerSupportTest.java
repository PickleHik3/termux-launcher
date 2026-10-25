package com.termux.app.x11;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class X11ServerSupportTest {

    private static Set<String> set(String... abis) {
        return new HashSet<>(Arrays.asList(abis));
    }

    private static final Set<String> UNIVERSAL_LIBS =
        set("arm64-v8a", "x86_64", "armeabi-v7a", "x86");
    private static final Set<String> UNIVERSAL_SERVER = set("arm64-v8a", "x86_64");

    @Test public void arm64PhoneHasTheServer() {
        assertTrue(X11ServerSupport.decide(new String[] {"arm64-v8a", "armeabi-v7a", "armeabi"},
            UNIVERSAL_LIBS, UNIVERSAL_SERVER));
    }

    @Test public void thirtyTwoBitPhoneDoesNot() {
        assertFalse(X11ServerSupport.decide(new String[] {"armeabi-v7a", "armeabi"},
            UNIVERSAL_LIBS, UNIVERSAL_SERVER));
    }

    @Test public void x86PhoneDoesNot() {
        assertFalse(X11ServerSupport.decide(new String[] {"x86", "armeabi-v7a"},
            UNIVERSAL_LIBS, UNIVERSAL_SERVER));
    }

    @Test public void splitApkForAThirtyTwoBitPhoneDoesNot() {
        assertFalse(X11ServerSupport.decide(new String[] {"armeabi-v7a"},
            set("armeabi-v7a"), Collections.<String>emptySet()));
    }

    @Test public void firstAbiWithLibrariesDecides() {
        // A 64-bit phone whose APK has only 32-bit libraries installs as 32-bit: no server.
        assertFalse(X11ServerSupport.decide(new String[] {"arm64-v8a", "armeabi-v7a"},
            set("armeabi-v7a"), Collections.<String>emptySet()));
    }

    @Test public void noLibrariesOrNoAbisMeansNo() {
        assertFalse(X11ServerSupport.decide(new String[] {"arm64-v8a"},
            Collections.<String>emptySet(), Collections.<String>emptySet()));
        assertFalse(X11ServerSupport.decide(null, UNIVERSAL_LIBS, UNIVERSAL_SERVER));
    }
}
