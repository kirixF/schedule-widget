package com.kirix.schedule;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.util.Locale;

public final class ScheduleWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.kirix.schedule.ACTION_REFRESH";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        if (SchedulePrefs.getGroup(context).trim().isEmpty()) {
            showSetup(context);
            return;
        }
        ScheduleData last = SchedulePrefs.getLastSchedule(context);
        if (last != null) {
            showSchedule(context, last);
        } else {
            showLoading(context);
        }
        ScheduleUpdateJobService.scheduleNow(context);
        ScheduleUpdateJobService.schedulePeriodic(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_REFRESH.equals(action)) {
            showLoading(context);
            ScheduleUpdateJobService.scheduleNow(context);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            ScheduleUpdateJobService.schedulePeriodic(context);
        }
    }

    static void showSetup(Context context) {
        updateAll(
                context,
                context.getString(R.string.widget_setup_title),
                context.getString(R.string.widget_setup_subtitle),
                context.getString(R.string.widget_setup_body)
        );
    }

    static void showLoading(Context context) {
        String group = SchedulePrefs.getGroup(context);
        String title = group.trim().isEmpty()
                ? context.getString(R.string.app_name)
                : "Сегодня · " + group.toUpperCase(Locale.forLanguageTag("ru"));
        updateAll(
                context,
                title,
                context.getString(R.string.widget_loading_subtitle),
                context.getString(R.string.widget_loading_body)
        );
    }

    static void showSchedule(Context context, ScheduleData data) {
        updateAll(context, data.widgetTitle(), data.widgetSubtitle(), data.widgetBody());
    }

    static void showError(Context context, String error) {
        ScheduleData last = SchedulePrefs.getLastSchedule(context);
        if (last != null) {
            updateAll(
                    context,
                    last.widgetTitle(),
                    context.getString(R.string.widget_last_data_error),
                    last.widgetBody()
            );
        } else {
            updateAll(
                    context,
                    context.getString(R.string.app_name),
                    context.getString(R.string.widget_error_subtitle),
                    error
            );
        }
    }

    private static void updateAll(Context context, String title, String subtitle, String body) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, ScheduleWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids.length == 0) {
            return;
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_schedule);
        views.setTextViewText(R.id.widgetTitle, title);
        views.setTextViewText(R.id.widgetSubtitle, subtitle);
        views.setTextViewText(R.id.widgetBody, body);
        views.setOnClickPendingIntent(R.id.widgetRoot, openAppIntent(context));
        manager.updateAppWidget(ids, views);
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
