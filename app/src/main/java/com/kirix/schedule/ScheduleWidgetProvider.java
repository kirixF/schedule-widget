package com.kirix.schedule;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

public final class ScheduleWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.kirix.schedule.ACTION_REFRESH";
    static final String ACTION_SELECT_DAY = "com.kirix.schedule.ACTION_SELECT_DAY";
    static final String ACTION_SHIFT_WEEK = "com.kirix.schedule.ACTION_SHIFT_WEEK";
    private static final String EXTRA_DATE = "date";
    private static final String EXTRA_WEEK_OFFSET = "week_offset";
    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM", RU);
    private static final int MAX_LESSONS = 4;
    private static final int DEFAULT_WIDGET_HEIGHT_DP = 220;
    private static final int[] DAY_CELL_IDS = {
            R.id.dayCell1,
            R.id.dayCell2,
            R.id.dayCell3,
            R.id.dayCell4,
            R.id.dayCell5,
            R.id.dayCell6,
            R.id.dayCell7
    };
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

        showSelectedSchedule(context);
        ScheduleUpdateJobService.scheduleNow(context);
        ScheduleUpdateJobService.scheduleDailyAtOne(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context,
            AppWidgetManager appWidgetManager,
            int appWidgetId,
            Bundle newOptions
    ) {
        showSelectedSchedule(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_SELECT_DAY.equals(action)) {
            SchedulePrefs.setWidgetSelectedDate(
                    context,
                    parseDate(intent.getStringExtra(EXTRA_DATE), SchedulePrefs.getWidgetSelectedDate(context))
            );
            showSelectedSchedule(context);
        } else if (ACTION_SHIFT_WEEK.equals(action)) {
            LocalDate selectedDate = SchedulePrefs.getWidgetSelectedDate(context);
            SchedulePrefs.setWidgetSelectedDate(
                    context,
                    selectedDate.plusWeeks(intent.getIntExtra(EXTRA_WEEK_OFFSET, 0))
            );
            showSelectedSchedule(context);
        } else if (ACTION_REFRESH.equals(action)) {
            showSelectedSchedule(context);
            ScheduleUpdateJobService.scheduleNow(context);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            ScheduleUpdateJobService.scheduleDailyAtOne(context);
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
        LocalDate selectedDate = SchedulePrefs.getWidgetSelectedDate(context);
        String title = group.trim().isEmpty()
                ? context.getString(R.string.app_name)
                : widgetTitle(context, group, selectedDate);
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

    static void showSelectedSchedule(Context context) {
        LocalDate selectedDate = SchedulePrefs.getWidgetSelectedDate(context);
        ScheduleData selectedSchedule = ScheduleArchiveStore.getSchedule(context, selectedDate);
        if (selectedSchedule != null) {
            showSchedule(context, selectedSchedule);
            return;
        }

        String group = SchedulePrefs.getGroup(context);
        updateMessage(
                context,
                widgetTitle(context, group, selectedDate),
                SHORT_DATE.format(selectedDate),
                context.getString(R.string.widget_no_day_data)
        );
    }

    static void showError(Context context, String error) {
        ScheduleData last = ScheduleArchiveStore.getSchedule(context, SchedulePrefs.getWidgetSelectedDate(context));
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
        updateWidgets(context, appWidgetId -> {
            RemoteViews views = createViews(context, title, subtitle);
            views.setViewVisibility(R.id.widgetMessage, View.VISIBLE);
            views.setTextViewText(R.id.widgetMessage, body);
            clearLessons(views);
            return views;
        });
    }

    private static void updateSchedule(Context context, ScheduleData data, String subtitle) {
        updateWidgets(
                context,
                appWidgetId -> createScheduleViews(context, data, subtitle, visibleLessonLimit(context, appWidgetId))
        );
    }

    private static RemoteViews createScheduleViews(
            Context context,
            ScheduleData data,
            String subtitle,
            int lessonLimit
    ) {
        RemoteViews views = createViews(context, widgetTitle(context, data.group, parseDate(data.dateKey, LocalDate.now())), subtitle);
        int visibleCount = Math.min(data.lessons.size(), lessonLimit);
        if (visibleCount == 0) {
            views.setViewVisibility(R.id.widgetMessage, View.VISIBLE);
            views.setTextViewText(R.id.widgetMessage, context.getString(R.string.widget_no_lessons));
            clearLessons(views);
            return views;
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
        return views;
    }

    private static RemoteViews createViews(Context context, String title, String subtitle) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_schedule);
        views.setTextViewText(R.id.widgetTitle, title);
        views.setTextViewText(R.id.widgetSubtitle, subtitle);
        views.setOnClickPendingIntent(R.id.widgetRoot, openAppIntent(context));
        bindDayStrip(context, views);
        return views;
    }

    private static void updateWidgets(Context context, WidgetViewsFactory factory) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, ScheduleWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids.length == 0) {
            return;
        }

        for (int id : ids) {
            manager.updateAppWidget(id, factory.create(id));
        }
    }

    private static int visibleLessonLimit(Context context, int appWidgetId) {
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId);
        int height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_WIDGET_HEIGHT_DP);
        if (height < 155) {
            return 1;
        }
        if (height < 188) {
            return 2;
        }
        if (height < 220) {
            return 3;
        }
        return MAX_LESSONS;
    }

    private interface WidgetViewsFactory {
        RemoteViews create(int appWidgetId);
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
        return dayLabel(data) + " · " + formatScheduleDate(data) + " · " + lessonCountLabel(data);
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
        LocalDate date = parseDate(data.dateKey, LocalDate.now());
        return date.getDayOfWeek()
                .getDisplayName(TextStyle.SHORT, RU)
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

    private static String formatScheduleDate(ScheduleData data) {
        return SHORT_DATE.format(parseDate(data.dateKey, LocalDate.now()));
    }

    private static void bindDayStrip(Context context, RemoteViews views) {
        LocalDate selectedDate = SchedulePrefs.getWidgetSelectedDate(context);
        LocalDate today = LocalDate.now();
        LocalDate weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int mutedColor = context.getColor(R.color.schedule_muted);
        int accentColor = context.getColor(R.color.schedule_accent);
        int selectedColor = context.getColor(android.R.color.white);

        for (int i = 0; i < DAY_CELL_IDS.length; i++) {
            int viewId = DAY_CELL_IDS[i];
            LocalDate date = weekStart.plusDays(i);
            boolean selected = date.equals(selectedDate);
            boolean isToday = date.equals(today);
            int background = selected
                    ? R.drawable.widget_day_selected_bg
                    : isToday ? R.drawable.widget_day_today_bg : R.drawable.widget_day_bg;
            int color = selected ? selectedColor : isToday ? accentColor : mutedColor;

            views.setTextViewText(viewId, dayCellLabel(date));
            views.setTextColor(viewId, color);
            views.setInt(viewId, "setBackgroundResource", background);
            views.setOnClickPendingIntent(viewId, selectDayIntent(context, date, i));
        }

        views.setOnClickPendingIntent(R.id.previousWeekButton, shiftWeekIntent(context, -1));
        views.setOnClickPendingIntent(R.id.nextWeekButton, shiftWeekIntent(context, 1));
    }

    private static String dayCellLabel(LocalDate date) {
        return date.getDayOfWeek()
                .getDisplayName(TextStyle.SHORT, RU)
                .toUpperCase(RU)
                .replace(".", "")
                + "\n"
                + date.getDayOfMonth();
    }

    private static String widgetTitle(Context context, String group, LocalDate date) {
        String day = date.equals(LocalDate.now())
                ? context.getString(R.string.widget_today)
                : SHORT_DATE.format(date);
        String normalizedGroup = clean(group).isEmpty()
                ? context.getString(R.string.widget_group)
                : group.toUpperCase(RU);
        return day + " · " + normalizedGroup;
    }

    private static LocalDate parseDate(String rawDate, LocalDate fallback) {
        try {
            return LocalDate.parse(rawDate);
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
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

    private static PendingIntent selectDayIntent(Context context, LocalDate date, int index) {
        Intent intent = new Intent(context, ScheduleWidgetProvider.class)
                .setAction(ACTION_SELECT_DAY)
                .putExtra(EXTRA_DATE, date.toString());
        return PendingIntent.getBroadcast(
                context,
                100 + index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent shiftWeekIntent(Context context, int weeks) {
        Intent intent = new Intent(context, ScheduleWidgetProvider.class)
                .setAction(ACTION_SHIFT_WEEK)
                .putExtra(EXTRA_WEEK_OFFSET, weeks);
        return PendingIntent.getBroadcast(
                context,
                weeks < 0 ? 201 : 202,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}
