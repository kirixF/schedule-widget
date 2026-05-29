package com.kirix.schedule;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
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
        updateMessage(
                context,
                context.getString(R.string.widget_setup_title),
                context.getString(R.string.widget_setup_subtitle),
                "",
                context.getString(R.string.widget_setup_body)
        );
    }

    static void showLoading(Context context) {
        String group = SchedulePrefs.getGroup(context);
        String title = group.trim().isEmpty()
                ? context.getString(R.string.app_name)
                : "Сегодня · " + group.toUpperCase(Locale.forLanguageTag("ru"));
        updateMessage(
                context,
                title,
                context.getString(R.string.widget_loading_subtitle),
                "",
                context.getString(R.string.widget_loading_body)
        );
    }

    static void showSchedule(Context context, ScheduleData data) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, ScheduleWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids.length == 0) {
            return;
        }

        RemoteViews views = baseViews(context, data.widgetTitle(), data.widgetSubtitle(), lessonCountLabel(data));
        if (data.lessons.isEmpty()) {
            showOnlyMessage(views, "Занятий нет");
        } else {
            views.setViewVisibility(R.id.widgetMessage, View.GONE);
            bindLessonRows(views, data);
        }
        manager.updateAppWidget(ids, views);
    }

    static void showError(Context context, String error) {
        ScheduleData last = SchedulePrefs.getLastSchedule(context);
        if (last != null) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            ComponentName component = new ComponentName(context, ScheduleWidgetProvider.class);
            int[] ids = manager.getAppWidgetIds(component);
            if (ids.length == 0) {
                return;
            }
            RemoteViews views = baseViews(
                    context,
                    last.widgetTitle(),
                    context.getString(R.string.widget_last_data_error),
                    lessonCountLabel(last)
            );
            if (last.lessons.isEmpty()) {
                showOnlyMessage(views, "Занятий нет");
            } else {
                views.setViewVisibility(R.id.widgetMessage, View.GONE);
                bindLessonRows(views, last);
            }
            manager.updateAppWidget(ids, views);
        } else {
            updateMessage(
                    context,
                    context.getString(R.string.app_name),
                    context.getString(R.string.widget_error_subtitle),
                    "",
                    error
            );
        }
    }

    private static void updateMessage(Context context, String title, String subtitle, String chip, String body) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, ScheduleWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids.length == 0) {
            return;
        }

        RemoteViews views = baseViews(context, title, subtitle, chip);
        showOnlyMessage(views, body);
        manager.updateAppWidget(ids, views);
    }

    private static RemoteViews baseViews(Context context, String title, String subtitle, String chip) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_schedule);
        views.setTextViewText(R.id.widgetTitle, title);
        views.setTextViewText(R.id.widgetSubtitle, subtitle);
        String normalizedChip = chip == null ? "" : chip.trim();
        views.setTextViewText(R.id.widgetChip, normalizedChip);
        views.setViewVisibility(R.id.widgetChip, normalizedChip.isEmpty() ? View.GONE : View.VISIBLE);
        views.setOnClickPendingIntent(R.id.widgetRoot, openAppIntent(context));
        return views;
    }

    private static void showOnlyMessage(RemoteViews views, String message) {
        views.setViewVisibility(R.id.widgetMessage, View.VISIBLE);
        views.setTextViewText(R.id.widgetMessage, message);
        setRowVisibility(views, 0, View.GONE);
        setRowVisibility(views, 1, View.GONE);
        setRowVisibility(views, 2, View.GONE);
        setRowVisibility(views, 3, View.GONE);
    }

    private static void bindLessonRows(RemoteViews views, ScheduleData data) {
        int limit = Math.min(data.lessons.size(), 4);
        for (int i = 0; i < 4; i++) {
            if (i < limit) {
                ScheduleData.Lesson lesson = data.lessons.get(i);
                setRowVisibility(views, i, View.VISIBLE);
                views.setTextViewText(numberIds()[i], lessonNumber(lesson, i));
                views.setTextViewText(titleIds()[i], clean(lesson.subject));
                views.setTextViewText(metaIds()[i], lessonMeta(lesson));
            } else {
                setRowVisibility(views, i, View.GONE);
            }
        }
    }

    private static void setRowVisibility(RemoteViews views, int index, int visibility) {
        views.setViewVisibility(rowIds()[index], visibility);
    }

    private static int[] rowIds() {
        return new int[] {R.id.lessonRow1, R.id.lessonRow2, R.id.lessonRow3, R.id.lessonRow4};
    }

    private static int[] numberIds() {
        return new int[] {R.id.lessonNumber1, R.id.lessonNumber2, R.id.lessonNumber3, R.id.lessonNumber4};
    }

    private static int[] titleIds() {
        return new int[] {R.id.lessonTitle1, R.id.lessonTitle2, R.id.lessonTitle3, R.id.lessonTitle4};
    }

    private static int[] metaIds() {
        return new int[] {R.id.lessonMeta1, R.id.lessonMeta2, R.id.lessonMeta3, R.id.lessonMeta4};
    }

    private static String lessonCountLabel(ScheduleData data) {
        int count = data.lessons.size();
        if (count == 0) {
            return "0 пар";
        }
        if (count == 1) {
            return "1 пара";
        }
        if (count >= 2 && count <= 4) {
            return count + " пары";
        }
        return count + " пар";
    }

    private static String lessonNumber(ScheduleData.Lesson lesson, int fallbackIndex) {
        String value = clean(lesson.number).replace("пара", "").trim();
        return value.isEmpty() ? String.valueOf(fallbackIndex + 1) : value;
    }

    private static String lessonMeta(ScheduleData.Lesson lesson) {
        String time = clean(lesson.time);
        String room = clean(lesson.room);
        if (time.isEmpty()) {
            return room;
        }
        if (room.isEmpty()) {
            return time;
        }
        return time + " · " + room;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
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
