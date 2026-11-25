package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.ServiceConnection;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * The activity is its own TermuxService connection. A binding that dies (the service destroyed
 * in the ordinary way) is delivered as onBindingDied and never as a later connect, so the activity
 * has to answer it, or it keeps a service it can no longer reach.
 */
public class TermuxActivityServiceBindingTest {

    @Test
    public void activityAnswersADeadBinding() throws Exception {
        assertTrue(ServiceConnection.class.isAssignableFrom(TermuxActivity.class));
        Method m = TermuxActivity.class.getDeclaredMethod("onBindingDied", ComponentName.class);
        assertTrue(Modifier.isPublic(m.getModifiers()));
        assertEquals(void.class, m.getReturnType());
    }
}
