package com.abhim5yu.cake.widgets;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Shared read-side helpers for the home-screen widgets.
 * Birthday data is written by CakeWidgetPlugin.syncBirthdays();
 * per-widget settings (chosen person, transparency, text color) are stored
 * under a key derived from the widget's appWidgetId.
 */
public final class WidgetPrefs {

    private static final String CONFIG_PREFIX = "widget_";

    private WidgetPrefs() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(CakeWidgetPlugin.PREFS, Context.MODE_PRIVATE);
    }

    /** All synced birthdays, as JSON objects. Empty list if nothing synced yet. */
    public static List<JSONObject> loadBirthdays(Context ctx) {
        List<JSONObject> out = new ArrayList<>();
        String raw = prefs(ctx).getString(CakeWidgetPlugin.KEY_DATA, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                out.add(arr.getJSONObject(i));
            }
        } catch (Exception ignored) {
            // Corrupt or missing data: treat as empty rather than crashing the widget.
        }
        return out;
    }

    /** Looks up one birthday by its id, or null if it no longer exists. */
    public static JSONObject findBirthday(Context ctx, String id) {
        if (id == null || id.isEmpty()) return null;
        for (JSONObject b : loadBirthdays(ctx)) {
            if (id.equals(b.optString("id"))) return b;
        }
        return null;
    }

    /**
     * Days from today until the next occurrence of this birthday.
     * 0 means today. Feb 29 falls back to Feb 28 in non-leap years,
     * matching the web app's behavior.
     */
    public static int daysUntil(JSONObject b) {
        int month = b.optInt("month", 1) - 1; // Calendar months are 0-based
        int day = b.optInt("day", 1);

        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);

        Calendar next = occurrenceInYear(today.get(Calendar.YEAR), month, day);
        if (next.before(today)) {
            next = occurrenceInYear(today.get(Calendar.YEAR) + 1, month, day);
        }
        long ms = next.getTimeInMillis() - today.getTimeInMillis();
        return (int) Math.round(ms / 86_400_000.0);
    }

    private static Calendar occurrenceInYear(int year, int month, int day) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month, 1);
        int max = c.getActualMaximum(Calendar.DAY_OF_MONTH);
        c.set(year, month, Math.min(day, max));
        c.set(Calendar.HOUR_OF_DAY, 0);
        return c;
    }

    // ---- per-widget settings ------------------------------------------------

    /** Birthday id this widget instance shows, or null if not configured. */
    public static String getWidgetPerson(Context ctx, int appWidgetId) {
        return prefs(ctx).getString(CONFIG_PREFIX + appWidgetId + "_person", null);
    }

    /** Background transparency 0 (opaque) to 100 (fully transparent). Default 0. */
    public static int getWidgetAlpha(Context ctx, int appWidgetId) {
        return prefs(ctx).getInt(CONFIG_PREFIX + appWidgetId + "_alpha", 0);
    }

    /** Text color as an ARGB int. Default white-ish cream from the app palette. */
    public static int getWidgetTextColor(Context ctx, int appWidgetId) {
        return prefs(ctx).getInt(CONFIG_PREFIX + appWidgetId + "_color", 0xFFF2F0EC);
    }

    /** Removes saved settings when the widget is deleted from the home screen. */
    public static void clearWidget(Context ctx, int appWidgetId) {
        prefs(ctx).edit()
                .remove(CONFIG_PREFIX + appWidgetId + "_person")
                .remove(CONFIG_PREFIX + appWidgetId + "_alpha")
                .remove(CONFIG_PREFIX + appWidgetId + "_color")
                .apply();
    }
}
