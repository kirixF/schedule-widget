package com.kirix.schedule;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ScheduleWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.kirix.schedule.ACTION_REFRESH";
    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final int MAX_LESSONS = 4;
    private static final int[] ROW_IDS = {
            R.id.lessonRow1,
            R.id.lessonRow2,
            R.id.lessonRow3,
            R.id.lessonRow4
    };
    private static final int[] DIVIDER_IDS = {
            R.id.lessonDivider1,
            R.id.lessonDivider2,
            R.id.lessonDivider3
    };
    private static final int[] NUMBER_IDS = {
            R.id.lessonNumber1,
            R.id.lessonNumber2,
            R.id.lessonNumber3,
            R.id.lessonNumber4
    };
    private static final int[] SUBJECT_IDS = {
            R.id.lessonSubject1,
            R.id.lessonSubject2,
            R.id.lessonSubject3,
            R.id.lessonSubject4
    };
    private static final int[] TIME_IDS = {
            R.id.lessonTime1,
            R.id.lessonTime2,
            R.id.lessonTime3,
            R.id.lessonTime4
    };
    private static final int[] ROOM_IDS = {
            R.id.lessonRoom1,
            R.id.lessonRoom2,
            R.id.lessonRoom3,
            R.id.lessonRoom4
    };

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
                context.getString(R.string.widget_setup_body)
        );
    }

    static void showLoading(Context context) {
        String group = SchedulePrefs.getGroup(context);
        String title = group.trim().isEmpty()
                ? context.getString(R.string.app_name)
                : "Сегодня · " + group.toUpperCase(RU);
        updateMessage(
                context,
                title,
                context.getString(R.string.widget_loading_subtitle),
                context.getString(R.string.widget_loading_body)
        );
    }

    static void showSchedule(Context context, ScheduleData data) {
        updateSchedule(context, data, scheduleSubtitle(data));
    }

    static void showError(Context context, String error) {
        ScheduleData last = SchedulePrefs.getLastSchedule(context);
        if (last != null) {
            updateSchedule(
                    context,
                    last,
                    context.getString(R.string.widget_last_data_error)
            );
        } else {
            updateMessage(
                    context,
                    context.getString(R.string.app_name),
                    context.getString(R.string.widget_error_subtitle),
                    error
            );
        }
    }

    private static void updateMessage(Context context, String title, String subtitle, String body) {
        RemoteViews views = createViews(context, title, subtitle);
        views.setViewVisibility(R.id.widgetMessage, View.VISIBLE);
        views.setTextViewText(R.id.widgetMessage, body);
        clearLessons(views);
        updateWidgets(context, views);
    }

    private static void updateSchedule(Context context, ScheduleData data, String subtitle) {
        RemoteViews views = createViews(context, data.widgetTitle(), subtitle);
        int visibleCount = Math.min(data.lessons.size(), MAX_LESSONS);
        if (visibleCount == 0) {
            views.setViewVisibility(R.id.widgetMessage, View.VISIBLE);
            views.setTextViewText(R.id.widgetMessage, "Занятий нет");
            clearLessons(views);
            updateWidgets(context, views);
            return;
        }

        views.setViewVisibility(R.id.widgetMessage, View.GONE);
        views.setTextViewText(R.id.widgetMessage, "");
        for (int i = 0; i < visibleCount; i++) {
            bindLesson(views, i, data.lessons.get(i));
        }
        for (int i = visibleCount; i < MAX_LESSONS; i++) {
            hideLesson(views, i);
        }
        for (int i = 0; i < DIVIDER_IDS.length; i++) {
            views.setViewVisibility(DIVIDER_IDS[i], i < visibleCount - 1 ? View.VISIBLE : View.GONE);
        }
        updateWidgets(context, views);
    }

    private static RemoteViews createViews(Context context, String title, String subtitle) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_schedule);
        views.setTextViewText(R.id.widgetTitle, title);
        views.setTextViewText(R.id.widgetSubtitle, subtitle);
        views.setOnClickPendingIntent(R.id.widgetRoot, openAppIntent(context));
        return views;
    }

    private static void updateWidgets(Context context, RemoteViews views) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, ScheduleWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids.length == 0) {
            return;
        }

        manager.updateAppWidget(ids, views);
    }

    private static void bindLesson(RemoteViews views, int index, ScheduleData.Lesson lesson) {
        views.setViewVisibility(ROW_IDS[index], View.VISIBLE);
        views.setTextViewText(NUMBER_IDS[index], lessonNumber(lesson, index));
        views.setTextViewText(SUBJECT_IDS[index], subjectLabel(lesson.subject));
        views.setTextViewText(TIME_IDS[index], timeLabel(lesson.time));
        views.setTextViewText(ROOM_IDS[index], roomLabel(lesson.room));
    }

    private static void clearLessons(RemoteViews views) {
        for (int i = 0; i < MAX_LESSONS; i++) {
            hideLesson(views, i);
        }
        for (int dividerId : DIVIDER_IDS) {
            views.setViewVisibility(dividerId, View.GONE);
        }
    }

    private static void hideLesson(RemoteViews views, int index) {
        views.setViewVisibility(ROW_IDS[index], View.GONE);
        views.setTextViewText(NUMBER_IDS[index], "");
        views.setTextViewText(SUBJECT_IDS[index], "");
        views.setTextViewText(TIME_IDS[index], "");
        views.setTextViewText(ROOM_IDS[index], "");
    }

    private static String scheduleSubtitle(ScheduleData data) {
        return dayLabel(data) + " · " + formatDate(data.updatedAtMillis) + " · " + lessonCountLabel(data);
    }

    private static String dayLabel(ScheduleData data) {
        String raw = clean(data.dayName).toLowerCase(RU);
        if (raw.length() <= 3 && !raw.isEmpty()) {
            return raw.toUpperCase(RU).replace(".", "");
        }
        if (raw.contains("пон") || raw.contains("monday")) {
            return "ПН";
        }
        if (raw.contains("вто") || raw.contains("tuesday")) {
            return "ВТ";
        }
        if (raw.contains("сре") || raw.contains("wednesday")) {
            return "СР";
        }
        if (raw.contains("чет") || raw.contains("thursday")) {
            return "ЧТ";
        }
        if (raw.contains("пят") || raw.contains("friday")) {
            return "ПТ";
        }
        if (raw.contains("суб") || raw.contains("saturday")) {
            return "СБ";
        }
        if (raw.contains("вос") || raw.contains("sunday")) {
            return "ВС";
        }
        return new SimpleDateFormat("EE", RU)
                .format(new Date(data.updatedAtMillis))
                .toUpperCase(RU)
                .replace(".", "");
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

    private static String lessonNumber(ScheduleData.Lesson lesson, int index) {
        String number = clean(lesson.number).replaceAll("\\D+", "");
        return number.isEmpty() ? String.valueOf(index + 1) : number;
    }

    private static String subjectLabel(String subject) {
        String clean = clean(subject);
        return clean.isEmpty() ? "Без названия" : clean;
    }

    private static String timeLabel(String time) {
        return clean(time).replace("-", "–");
    }

    private static String roomLabel(String room) {
        String clean = clean(room);
        int comma = clean.indexOf(',');
        if (comma > 0) {
            clean = clean.substring(0, comma).trim();
        }
        if (clean.isEmpty()) {
            return "";
        }

        String lower = clean.toLowerCase(RU);
        if (lower.startsWith("ауд") || lower.startsWith("каб")) {
            return clean;
        }
        return "ауд. " + clean;
    }

    private static String formatDate(long millis) {
        return new SimpleDateFormat("dd.MM", RU).format(new Date(millis));
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
