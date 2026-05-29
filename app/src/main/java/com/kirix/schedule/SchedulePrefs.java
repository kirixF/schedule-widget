package com.kirix.schedule;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;

final class SchedulePrefs {
    private static final String PREFS = "schedule_widget";
    private static final String KEY_GROUP = "group";
    private static final String KEY_LAST_JSON = "last_json";
    private static final String KEY_LAST_ERROR = "last_error";

    private SchedulePrefs() {
    }

    static String getGroup(Context context) {
        return prefs(context).getString(KEY_GROUP, "");
    }

    static void setGroup(Context context, String group) {
        prefs(context).edit().putString(KEY_GROUP, group.trim()).apply();
    }

    static void setLastSchedule(Context context, String rawJson) {
        prefs(context).edit()
                .putString(KEY_LAST_JSON, rawJson)
                .remove(KEY_LAST_ERROR)
                .apply();
    }

    static void setLastError(Context context, String message) {
        prefs(context).edit().putString(KEY_LAST_ERROR, message).apply();
    }

    static String getLastError(Context context) {
        return prefs(context).getString(KEY_LAST_ERROR, "");
    }

    static ScheduleData getLastSchedule(Context context) {
        String raw = prefs(context).getString(KEY_LAST_JSON, "");
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return ScheduleData.fromJson(raw);
        } catch (JSONException ignored) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
