package com.termux.app.launcher.widget;

import android.app.Application;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetBindFlowPolicyTest {
    @Test public void allocationDirectConsentConfigureAndReadyPaths() {
        assertEquals(WidgetAddTransaction.Stage.BOUND,
            WidgetBindFlowPolicy.afterAllocation(true).nextStage);
        assertEquals(WidgetAddTransaction.Stage.WAITING_FOR_BIND_CONSENT,
            WidgetBindFlowPolicy.afterAllocation(false).nextStage);
        WidgetAddTransaction bind = tx(WidgetAddTransaction.Stage.WAITING_FOR_BIND_CONSENT);
        assertEquals(WidgetAddTransaction.Stage.BOUND,
            WidgetBindFlowPolicy.onBindResult(bind, 8, 8, true, true).nextStage);
        assertEquals(WidgetBindFlowPolicy.Outcome.READY,
            WidgetBindFlowPolicy.onConfigureResult(
                tx(WidgetAddTransaction.Stage.WAITING_FOR_CONFIGURATION), 8, -1, true, true).outcome);
    }

    @Test public void declineForeignMismatchUnavailableAndCancelDeleteExactlyOnce() {
        WidgetAddTransaction bind = tx(WidgetAddTransaction.Stage.WAITING_FOR_BIND_CONSENT);
        WidgetBindFlowPolicy.Decision decline = WidgetBindFlowPolicy.onBindResult(
            bind, 8, 8, false, false);
        assertEquals(WidgetBindFlowPolicy.Outcome.DECLINED, decline.outcome);
        assertTrue(decline.deleteId);
        assertFalse(WidgetBindFlowPolicy.onBindResult(bind, 8, 99, true, true).deleteId);
        assertEquals(WidgetBindFlowPolicy.Outcome.IGNORE_FOREIGN_RESULT,
            WidgetBindFlowPolicy.onBindResult(bind, 8, 99, true, true).outcome);
        assertTrue(WidgetBindFlowPolicy.onBindResult(bind, 8, 8, true, false).deleteId);
        assertTrue(WidgetBindFlowPolicy.afterConfigureDecision(
            WidgetConfigurePolicy.Decision.UNAVAILABLE).deleteId);
        for (WidgetAddTransaction.Stage stage : WidgetAddTransaction.Stage.values()) {
            WidgetBindFlowPolicy.Decision cancel = WidgetBindFlowPolicy.cancel(tx(stage));
            assertTrue(cancel.deleteId);
        }
    }

    @Test public void canceledExpectedResultIgnoresStrayReturnedIdAndDeletesDurableId() {
        WidgetBindFlowPolicy.Decision bind = WidgetBindFlowPolicy.onBindResult(
            tx(WidgetAddTransaction.Stage.WAITING_FOR_BIND_CONSENT), 8, 99, false, false);
        assertEquals(WidgetBindFlowPolicy.Outcome.DECLINED, bind.outcome);
        assertTrue(bind.deleteId);
        WidgetBindFlowPolicy.Decision configure = WidgetBindFlowPolicy.onConfigureResult(
            tx(WidgetAddTransaction.Stage.WAITING_FOR_CONFIGURATION), 8, 99, false, false);
        assertEquals(WidgetBindFlowPolicy.Outcome.DECLINED, configure.outcome);
        assertTrue(configure.deleteId);
    }

    private static WidgetAddTransaction tx(WidgetAddTransaction.Stage stage) {
        return new WidgetAddTransaction("t", 8, new ComponentName("pkg", "Provider"), 0,
            stage, new Bundle(), 1);
    }
}
