package com.termux.app.launcher.widget.builtin;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.termux.app.statusbar.WeatherController;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * A weather card made while the shared controller already holds a forecast (the second page
 * layer of a Home swipe) shows it, even when the forecast is stale and a fetch is in flight.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WeatherWidgetNeighbourDataTest {
    private static final BuiltinWidgetServices.Host HOST = new BuiltinWidgetServices.Host() {
        @Override public void requestCalendarPermission() { }
        @Override public boolean openCommandWindow(@NonNull List<String> command,
                                                   String title) { return false; }
        @Override public android.graphics.Typeface monoTypeface() { return null; }
    };

    @Test public void aNewCardShowsTheForecastTheControllerHoldsEvenWhenStale() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(com.termux.R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        BuiltinWidgetServices services = new BuiltinWidgetServices(activity, HOST);
        WeatherController controller = services.acquireWeather(w -> { });
        WeatherController.Weather cache = controller.cache();
        cache.valid = true;
        cache.currentC = 32;
        cache.currentCode = 2;
        // An hour old: past the replay window, so the controller fetches instead of replaying.
        cache.fetchedAtMs = System.currentTimeMillis() - 60 * 60 * 1000L;

        BuiltinWidgetStyle style = BuiltinWidgetStyle.resolve(activity,
            BuiltinWidgetStyle.Numerals.SANS, false, null);
        WeatherWidgetView view = new WeatherWidgetView(activity, services, style);
        view.setCells(2, 2);
        view.bind(new android.os.Bundle());
        activity.setContentView(view, new ViewGroup.LayoutParams(900, 900));
        view.measure(View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 900, 900);

        String description = String.valueOf(view.getContentDescription());
        assertTrue("the forecast shows, not the empty card: " + description,
            description.contains("32") && !description.contains(
                activity.getString(com.termux.R.string.bw_feeds_weather_empty)));
        services.destroy();
    }
}
