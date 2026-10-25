package com.termux.app.x11;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import dalvik.system.BaseDexClassLoader;

public class X11ServerSupportTest {

    /** A loader that answers findLibrary the way the app's PathClassLoader does on a device. */
    private static final class Loader extends BaseDexClassLoader {
        private final String mPath;

        Loader(String path) {
            super("", null, null, null);
            mPath = path;
        }

        @Override public String findLibrary(String name) {
            return X11ServerSupport.LIB.equals(name) ? mPath : null;
        }
    }

    @Test public void aLoaderThatFindsTheServerMeansAvailable() {
        assertTrue(X11ServerSupport.canLoad(
            new Loader("/data/app/x/base.apk!/lib/arm64-v8a/libXlorie.so")));
    }

    @Test public void aLoaderWithoutItForThisAbiMeansNot() {
        assertFalse(X11ServerSupport.canLoad(new Loader(null)));
    }

    @Test public void aLoaderThatCannotAnswerMeansNot() {
        assertFalse(X11ServerSupport.canLoad(getClass().getClassLoader()));
        assertFalse(X11ServerSupport.canLoad(null));
    }
}
