package com.kirix.schedule;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.Locale;

public final class ScheduleWidgetProvider extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.kirix.schedule.ACTION_REFRESH";
    static final String ACTION_SELECT_DAY = "com.kirix.schedule.ACTION_SELECT_DAY";
    static final String ACTION_SHIFT_WEEK = "com.kirix.schedule.ACTION_SHIFT_WEEK";
    static final String ACTION_TOGGLE_QR = "com.kirix.schedule.ACTION_TOGGLE_QR";
    static final String ACTION_TOGGLE_WEATHER = "com.kirix.schedule.ACTION_TOGGLE_WEATHER";
    static final String ACTION_LAUNCH_TETRIS = "com.kirix.schedule.ACTION_LAUNCH_TETRIS";
    private static final int DEFAULT_WIDGET_HEIGHT_DP = 220;
    private static final String EXTRA_DATE = "date";
    private static final String EXTRA_WEEK_OFFSET = "week_offset";
    private static final int MAX_LESSONS = 4;
    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM", RU);
    private static final int[] DAY_CELL_IDS = {
            R.id.dayCell1, R.id.dayCell2, R.id.dayCell3, R.id.dayCell4,
            R.id.dayCell5, R.id.dayCell6, R.id.dayCell7
    };
    private static final int[] ROW_IDS = {
            R.id.lessonRow1, R.id.lessonRow2, R.id.lessonRow3, R.id.lessonRow4
    };
    private static final int[] DIVIDER_IDS = {
            R.id.lessonDivider1, R.id.lessonDivider2, R.id.lessonDivider3
    };
    private static final int[] NUMBER_IDS = {
            R.id.lessonNumber1, R.id.lessonNumber2, R.id.lessonNumber3, R.id.lessonNumber4
    };
    private static final int[] SUBJECT_IDS = {
            R.id.lessonSubject1, R.id.lessonSubject2, R.id.lessonSubject3, R.id.lessonSubject4
    };
    private static final int[] TIME_IDS = {
            R.id.lessonTime1, R.id.lessonTime2, R.id.lessonTime3, R.id.lessonTime4
    };
    private static final int[] ROOM_IDS = {
            R.id.lessonRoom1, R.id.lessonRoom2, R.id.lessonRoom3, R.id.lessonRoom4
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
        ScheduleUpdateJobService.schedulePeriodic(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
                                          int appWidgetId, Bundle newOptions) {
        showSelectedSchedule(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent == null ? "" : intent.getAction();
        if (ACTION_SELECT_DAY.equals(action)) {
            SchedulePrefs.setWidgetSelectedDate(context,
                    parseDate(intent.getStringExtra(EXTRA_DATE), SchedulePrefs.getWidgetSelectedDate(context)));
            showSelectedSchedule(context);
        } else if (ACTION_SHIFT_WEEK.equals(action)) {
            LocalDate selectedDate = SchedulePrefs.getWidgetSelectedDate(context);
            SchedulePrefs.setWidgetSelectedDate(context,
                    selectedDate.plusWeeks(intent.getIntExtra(EXTRA_WEEK_OFFSET, 0)));
            showSelectedSchedule(context);
        } else if (ACTION_TOGGLE_QR.equals(action)) {
            boolean show = !SchedulePrefs.getWidgetShowQr(context);
            SchedulePrefs.setWidgetShowQr(context, show);
            if (show) {
                SchedulePrefs.setWidgetShowWeather(context, false);
            }
            showSelectedSchedule(context);
        } else if (ACTION_TOGGLE_WEATHER.equals(action)) {
            boolean show = !SchedulePrefs.getWidgetShowWeather(context);
            SchedulePrefs.setWidgetShowWeather(context, show);
            if (show) {
                SchedulePrefs.setWidgetShowQr(context, false);
                if (SchedulePrefs.getLastForecast(context) == null) {
                    triggerWeatherRefresh(context);
                }
            }
            showSelectedSchedule(context);
        } else if (ACTION_LAUNCH_TETRIS.equals(action)) {
            Intent launch = new Intent(context, TetrisActivity.class);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
        } else if (ACTION_REFRESH.equals(action)) {
            showSelectedSchedule(context);
            ScheduleUpdateJobService.scheduleNow(context);
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            ScheduleUpdateJobService.scheduleDailyAtOne(context);
            ScheduleUpdateJobService.schedulePeriodic(context);
        }
    }

    static void showSetup(Context context) {
        updateMessage(context,
                context.getString(R.string.widget_setup_title),
                context.getString(R.string.widget_setup_subtitle),
                context.getString(R.string.widget_setup_body));
    }

    static void showLoading(Context context) {
        String group = SchedulePrefs.getGroup(context);
        boolean isTeacher = SchedulePrefs.isTeacher(context);
        LocalDate selectedDate = SchedulePrefs.getWidgetSelectedDate(context);
        String title = group.trim().isEmpty()
                ? context.getString(R.string.app_name)
                : widgetTitle(context, group, isTeacher, selectedDate);
        updateMessage(context,
                title,
                context.getString(R.string.widget_loading_subtitle),
                context.getString(R.string.widget_loading_body));
    }

    static void showSchedule(Context context, ScheduleData data) {
        updateSchedule(context, data, scheduleSubtitle(data));
    }

    static void showSelectedSchedule(Context context) {
        if (SchedulePrefs.getWidgetShowWeather(context)) {
            showWeatherOverlay(context);
            return;
        }
        if (SchedulePrefs.hasQrCode(context) && SchedulePrefs.getWidgetShowQr(context)) {
            LocalDate selectedDate = SchedulePrefs.getWidgetSelectedDate(context);
            updateMessage(context,
                    widgetTitle(context, SchedulePrefs.getGroup(context), SchedulePrefs.isTeacher(context), selectedDate),
                    "",
                    "");
            return;
        }
        LocalDate selectedDate = SchedulePrefs.getWidgetSelectedDate(context);
        ScheduleData selectedSchedule = null;
        try {
            selectedSchedule = ScheduleArchiveStore.getSchedule(context, selectedDate);
        } catch (Exception ignored) {
        }
        if (selectedSchedule != null) {
            showSchedule(context, selectedSchedule);
            return;
        }
        updateMessage(context,
                widgetTitle(context, SchedulePrefs.getGroup(context), SchedulePrefs.isTeacher(context), selectedDate),
                SHORT_DATE.format(selectedDate),
                context.getString(R.string.widget_no_day_data));
    }

    static void showError(Context context, String error) {
        ScheduleData last = null;
        try {
            last = ScheduleArchiveStore.getSchedule(context, SchedulePrefs.getWidgetSelectedDate(context));
        } catch (Exception ignored) {
        }
        if (last != null) {
            updateSchedule(context, last, context.getString(R.string.widget_last_data_error));
        } else {
            updateMessage(context,
                    context.getString(R.string.app_name),
                    context.getString(R.string.widget_error_subtitle),
                    error);
        }
    }

    private static void updateMessage(Context context, String title, String subtitle, String body) {
        RemoteViews views = createViews(context, title, subtitle);
        views.setViewVisibility(R.id.widgetMessage, View.VISIBLE);
        views.setTextViewText(R.id.widgetMessage, body);
        views.setTextColor(R.id.widgetMessage, ThemeRes.getMutedColor(SchedulePrefs.getWidgetTheme(context)));
        clearLessons(views);
        updateWidgets(context, views);
    }

    private static void updateSchedule(Context context, ScheduleData data, String subtitle) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, ScheduleWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) {
            manager.updateAppWidget(id,
                    createScheduleViews(context, data, subtitle, visibleLessonLimit(context, id)));
        }
    }

    private static RemoteViews createScheduleViews(Context context, ScheduleData data, String subtitle, int lessonLimit) {
        LocalDate date = parseDate(data.dateKey, LocalDate.now());
        RemoteViews views = createViews(context, widgetTitle(context, data.group, data.isTeacher, date), subtitle);
        int visibleCount = Math.min(data.lessons.size(), lessonLimit);
        String theme = SchedulePrefs.getWidgetTheme(context);
        String accent = SchedulePrefs.getAccentColor(context);
        if (visibleCount == 0) {
            views.setViewVisibility(R.id.widgetMessage, View.VISIBLE);
            views.setTextViewText(R.id.widgetMessage, context.getString(R.string.widget_no_lessons));
            views.setTextColor(R.id.widgetMessage, ThemeRes.getMutedColor(theme));
            clearLessons(views);
            return views;
        }
        views.setViewVisibility(R.id.widgetMessage, View.GONE);
        views.setTextViewText(R.id.widgetMessage, "");
        for (int i = 0; i < visibleCount; i++) {
            bindLesson(views, i, data.lessons.get(i), data.isTeacher, theme, accent);
        }
        for (int i = visibleCount; i < MAX_LESSONS; i++) {
            hideLesson(views, i);
        }
        for (int i = 0; i < DIVIDER_IDS.length; i++) {
            boolean visible = i < visibleCount - 1;
            views.setViewVisibility(DIVIDER_IDS[i], visible ? View.VISIBLE : View.GONE);
            if (visible) {
                views.setInt(DIVIDER_IDS[i], "setBackgroundColor", ThemeRes.getDividerColor(theme));
            }
        }
        return views;
    }

    private static RemoteViews createViews(Context context, String title, String subtitle) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_schedule);
        String theme = SchedulePrefs.getWidgetTheme(context);
        String accent = SchedulePrefs.getAccentColor(context);
        views.setInt(R.id.widgetRoot, "setBackgroundResource", ThemeRes.getWidgetBg(theme));
        views.setTextColor(R.id.widgetTitle, ThemeRes.getTextColor(theme));
        views.setTextColor(R.id.widgetSubtitle, ThemeRes.getMutedColor(theme));
        views.setTextViewText(R.id.widgetTitle, title);
        views.setTextViewText(R.id.widgetSubtitle, subtitle);
        views.setOnClickPendingIntent(R.id.widgetRoot, openAppIntent(context));

        boolean showWeather = SchedulePrefs.getWidgetShowWeather(context);
        boolean hasQr = SchedulePrefs.hasQrCode(context);
        boolean showQr = hasQr && SchedulePrefs.getWidgetShowQr(context);

        views.setViewVisibility(R.id.weatherToggleButton, View.VISIBLE);
        views.setTextViewText(R.id.weatherToggleButton, showWeather
                ? context.getString(R.string.weather_back_button)
                : context.getString(R.string.weather_toggle_button));
        views.setInt(R.id.weatherToggleButton, "setBackgroundResource",
                showWeather ? ThemeRes.getBadgeBg(accent) : ThemeRes.getDayBg(theme));
        views.setTextColor(R.id.weatherToggleButton, showWeather ? -1 : ThemeRes.getTextColor(theme));
        views.setOnClickPendingIntent(R.id.weatherToggleButton, toggleWeatherIntent(context));

        if (hasQr) {
            views.setViewVisibility(R.id.widgetQrToggleButton, View.VISIBLE);
            views.setTextViewText(R.id.widgetQrToggleButton, showQr ? context.getString(R.string.weather_back_button) : "QR");
            views.setInt(R.id.widgetQrToggleButton, "setBackgroundResource",
                    showQr ? ThemeRes.getBadgeBg(accent) : ThemeRes.getDayBg(theme));
            views.setTextColor(R.id.widgetQrToggleButton, showQr ? -1 : ThemeRes.getTextColor(theme));
            views.setOnClickPendingIntent(R.id.widgetQrToggleButton, toggleQrIntent(context));
        } else {
            views.setViewVisibility(R.id.widgetQrToggleButton, View.GONE);
        }

        if (showQr) {
            views.setViewVisibility(R.id.widgetDayStrip, View.GONE);
            views.setViewVisibility(R.id.tetrisButton, View.GONE);
            File qrFile = new File(context.getFilesDir(), "pass_qr.png");
            Bitmap bitmap = qrFile.exists() ? BitmapFactory.decodeFile(qrFile.getAbsolutePath()) : null;
            if (bitmap != null) {
                views.setViewVisibility(R.id.widgetQrImage, View.VISIBLE);
                views.setImageViewBitmap(R.id.widgetQrImage, bitmap);
            } else {
                views.setViewVisibility(R.id.widgetQrImage, View.GONE);
            }
        } else {
            views.setViewVisibility(R.id.widgetQrImage, View.GONE);
            views.setViewVisibility(R.id.tetrisButton, View.VISIBLE);
            views.setTextViewText(R.id.tetrisButton, context.getString(R.string.tetris_widget_button));
            views.setContentDescription(R.id.tetrisButton, context.getString(R.string.tetris_launch_desc));
            views.setOnClickPendingIntent(R.id.tetrisButton, launchTetrisIntent(context));
            // Лента дат остаётся видимой и в режиме погоды — выбор даты меняет прогноз.
            views.setViewVisibility(R.id.widgetDayStrip, View.VISIBLE);
            bindDayStrip(context, views);
        }
        views.setViewVisibility(R.id.weatherDetails, View.GONE);
        return views;
    }

    // Оверлей погоды: детали по выбранной дате, лента дат остаётся для переключения.
    private static void showWeatherOverlay(Context context) {
        LocalDate date = SchedulePrefs.getWidgetSelectedDate(context);
        String title = widgetTitle(context, SchedulePrefs.getGroup(context), SchedulePrefs.isTeacher(context), date);
        RemoteViews views = createViews(context, title, overlaySubtitle(context));
        clearLessons(views);
        views.setViewVisibility(R.id.widgetMessage, View.GONE);
        renderWeatherDetails(context, views, date);
        updateWidgets(context, views);
    }

    private static void renderWeatherDetails(Context context, RemoteViews views, LocalDate date) {
        String theme = SchedulePrefs.getWidgetTheme(context);
        int textColor = ThemeRes.getTextColor(theme);
        int mutedColor = ThemeRes.getMutedColor(theme);
        views.setTextColor(R.id.weatherTempBig, textColor);
        views.setTextColor(R.id.weatherCondition, textColor);
        views.setTextColor(R.id.weatherFeels, mutedColor);
        views.setTextColor(R.id.weatherInfoRow, mutedColor);
        GismeteoWeatherData forecast = SchedulePrefs.getLastForecast(context);
        if (forecast == null || forecast.days.isEmpty()) {
            triggerWeatherRefresh(context);
            views.setViewVisibility(R.id.weatherDetails, View.VISIBLE);
            views.setViewVisibility(R.id.weatherBigIcon, View.GONE);
            views.setViewVisibility(R.id.weatherTempBig, View.GONE);
            String lastError = SchedulePrefs.getLastForecastError(context);
            views.setTextViewText(R.id.weatherCondition, lastError != null && !lastError.trim().isEmpty()
                    ? lastError.trim()
                    : context.getString(R.string.weather_loading));
            views.setViewVisibility(R.id.weatherFeels, View.GONE);
            views.setViewVisibility(R.id.weatherInfoRow, View.GONE);
            hideWeatherSlots(views);
            return;
        }
        views.setViewVisibility(R.id.weatherDetails, View.VISIBLE);
        views.setViewVisibility(R.id.weatherTempBig, View.VISIBLE);
        GismeteoWeatherData.DayForecast day = forecast.dayByDate(SHORT_DATE.format(date));
        boolean isToday = date.equals(LocalDate.now());
        if (day == null) {
            views.setViewVisibility(R.id.weatherBigIcon, View.GONE);
            views.setTextViewText(R.id.weatherCondition, context.getString(R.string.weather_no_data));
            views.setViewVisibility(R.id.weatherFeels, View.GONE);
            views.setViewVisibility(R.id.weatherInfoRow, View.GONE);
            hideWeatherSlots(views);
            return;
        }
        String condition = isToday && !forecast.conditionNow.isEmpty() ? forecast.conditionNow : day.condition;
        views.setTextViewText(R.id.weatherCondition, condition.isEmpty() ? "—" : condition);
        views.setTextViewText(R.id.weatherBigIcon, day.iconEmoji);
        views.setViewVisibility(R.id.weatherBigIcon, View.VISIBLE);
        if (isToday && forecast.currentTemp != GismeteoWeatherData.DayForecast.NO_VALUE) {
            views.setTextViewText(R.id.weatherTempBig, formatTempShort(forecast.currentTemp) + "\u00B0");
            if (forecast.feelsLike != GismeteoWeatherData.DayForecast.NO_VALUE) {
                views.setTextViewText(R.id.weatherFeels, "ощущается как " + formatTempShort(forecast.feelsLike) + "\u00B0");
                views.setViewVisibility(R.id.weatherFeels, View.VISIBLE);
            } else {
                views.setViewVisibility(R.id.weatherFeels, View.GONE);
            }
        } else {
            views.setTextViewText(R.id.weatherTempBig, formatTempShort(day.tempMax) + "\u00B0");
            views.setTextViewText(R.id.weatherFeels, "макс. " + formatTempShort(day.tempMax) + "\u00B0 / мин. " + formatTempShort(day.tempMin) + "\u00B0");
            views.setViewVisibility(R.id.weatherFeels, View.VISIBLE);
        }
        StringBuilder info = new StringBuilder();
        if (isToday && forecast.humidity >= 0) {
            info.append("\uD83D\uDCA7 влажность ").append(forecast.humidity).append("%");
        }
        if (isToday && forecast.pressureMm > 0) {
            if (info.length() > 0) info.append("   ·   ");
            info.append(forecast.pressureMm).append(" мм рт. ст.");
        }
        int wind = isToday && forecast.windSpeedNow >= 0 ? forecast.windSpeedNow : day.windSpeed;
        if (wind > 0) {
            if (info.length() > 0) info.append("   ·   ");
            info.append("\uD83C\uDF2C ветер ").append(wind).append(" м/с");
            String dir = isToday && !forecast.windDirNow.isEmpty() ? forecast.windDirNow : day.windDir;
            if (!dir.isEmpty()) info.append(", ").append(dir);
        } else if (info.length() == 0) {
            info.append("штиль");
        }
        views.setViewVisibility(R.id.weatherInfoRow, View.VISIBLE);
        views.setTextViewText(R.id.weatherInfoRow, info.toString());
        int slots = Math.min(4, day.slotTemps.length);
        for (int i = 0; i < 4; i++) {
            if (i < slots) {
                views.setTextViewText(slotLabelId(i + 1), day.slotLabel(i));
                views.setTextViewText(slotTempId(i + 1), day.slotTempLabel(i));
                String slotCondition = i < day.slotConditions.length ? day.slotConditions[i] : "";
                views.setTextViewText(slotIconId(i + 1), slotCondition.isEmpty()
                        ? GismeteoWeatherData.DayForecast.iconEmojiForCondition("")
                        : GismeteoWeatherData.DayForecast.iconEmojiForCondition(slotCondition));
            } else {
                views.setTextViewText(slotLabelId(i + 1), "");
                views.setTextViewText(slotTempId(i + 1), "");
                views.setTextViewText(slotIconId(i + 1), "");
            }
        }
    }

    private static void hideWeatherSlots(RemoteViews views) {
        for (int i = 1; i <= 4; i++) {
            views.setTextViewText(slotLabelId(i), "");
            views.setTextViewText(slotTempId(i), "");
            views.setTextViewText(slotIconId(i), "");
        }
    }

    private static String formatTempShort(int value) {
        if (value == GismeteoWeatherData.DayForecast.NO_VALUE) return "—";
        if (value > 0) return "+" + value;
        if (value == 0) return "0";
        return String.valueOf(value);
    }

    private static int slotLabelId(int index) {
        switch (index) {
            case 1: return R.id.weatherSlotLabel1;
            case 2: return R.id.weatherSlotLabel2;
            case 3: return R.id.weatherSlotLabel3;
            default: return R.id.weatherSlotLabel4;
        }
    }

    private static int slotTempId(int index) {
        switch (index) {
            case 1: return R.id.weatherSlotTemp1;
            case 2: return R.id.weatherSlotTemp2;
            case 3: return R.id.weatherSlotTemp3;
            default: return R.id.weatherSlotTemp4;
        }
    }

    private static int slotIconId(int index) {
        switch (index) {
            case 1: return R.id.weatherSlotIcon1;
            case 2: return R.id.weatherSlotIcon2;
            case 3: return R.id.weatherSlotIcon3;
            default: return R.id.weatherSlotIcon4;
        }
    }

    private static String overlaySubtitle(Context context) {
        if (SchedulePrefs.getWidgetShowWeather(context)) {
            GismeteoWeatherData forecast = SchedulePrefs.getLastForecast(context);
            String city = SchedulePrefs.getCityName(context);
            if (forecast != null) {
                return city + " · обновлено " + formatTime(forecast.updatedAtMillis);
            }
            return city;
        }
        return "";
    }

    static void triggerWeatherRefresh(Context context) {
        GismeteoApiClient.fetchForecast(context, new GismeteoApiClient.Callback() {
            @Override
            public void onSuccess(GismeteoWeatherData data, String rawJson) {
                SchedulePrefs.setLastForecast(context, rawJson);
                if (SchedulePrefs.getWidgetShowWeather(context) && !SchedulePrefs.getWidgetShowQr(context)) {
                    showSelectedSchedule(context);
                }
            }

            @Override
            public void onError(String message) {
                SchedulePrefs.setLastForecastError(context, message);
                if (SchedulePrefs.getWidgetShowWeather(context) && !SchedulePrefs.getWidgetShowQr(context)
                        && SchedulePrefs.getLastForecast(context) == null) {
                    showSelectedSchedule(context);
                }
            }
        });
    }

    private static PendingIntent toggleQrIntent(Context context) {
        Intent intent = new Intent(context, ScheduleWidgetProvider.class).setAction(ACTION_TOGGLE_QR);
        return PendingIntent.getBroadcast(context, 300, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent launchTetrisIntent(Context context) {
        Intent intent = new Intent(context, ScheduleWidgetProvider.class).setAction(ACTION_LAUNCH_TETRIS);
        return PendingIntent.getBroadcast(context, 302, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent toggleWeatherIntent(Context context) {
        Intent intent = new Intent(context, ScheduleWidgetProvider.class).setAction(ACTION_TOGGLE_WEATHER);
        return PendingIntent.getBroadcast(context, 301, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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

    private static int visibleLessonLimit(Context context, int appWidgetId) {
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId);
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_WIDGET_HEIGHT_DP);
        int maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight);
        int height = Math.max(minHeight, maxHeight);
        if (height <= 0) {
            height = DEFAULT_WIDGET_HEIGHT_DP;
        }
        if (height < 155) {
            return 1;
        }
        if (height < 188) {
            return 2;
        }
        if (height < DEFAULT_WIDGET_HEIGHT_DP) {
            return 3;
        }
        return MAX_LESSONS;
    }

    private static void bindLesson(RemoteViews views, int index, ScheduleData.Lesson lesson,
                                   boolean isTeacher, String theme, String accent) {
        views.setViewVisibility(ROW_IDS[index], View.VISIBLE);
        views.setTextViewText(NUMBER_IDS[index], lessonNumber(lesson, index));
        views.setInt(NUMBER_IDS[index], "setBackgroundResource", ThemeRes.getBadgeBg(accent));
        views.setTextColor(NUMBER_IDS[index], -1);
        String subject = subjectLabel(lesson.subject);
        if (isTeacher && lesson.teacher != null && !lesson.teacher.trim().isEmpty()) {
            subject = subject + " (" + lesson.teacher.trim() + ")";
        }
        views.setTextViewText(SUBJECT_IDS[index], subject);
        views.setTextColor(SUBJECT_IDS[index], ThemeRes.getTextColor(theme));
        views.setTextViewText(TIME_IDS[index], timeLabel(lesson.time));
        views.setTextColor(TIME_IDS[index], ThemeRes.getMutedColor(theme));
        views.setTextViewText(ROOM_IDS[index], roomLabel(lesson.room));
        views.setTextColor(ROOM_IDS[index], ThemeRes.getMutedColor(theme));
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
        if (raw.contains("пон") || raw.contains("monday")) return "ПН";
        if (raw.contains("вто") || raw.contains("tuesday")) return "ВТ";
        if (raw.contains("сре") || raw.contains("wednesday")) return "СР";
        if (raw.contains("чет") || raw.contains("thursday")) return "ЧТ";
        if (raw.contains("пят") || raw.contains("friday")) return "ПТ";
        if (raw.contains("суб") || raw.contains("saturday")) return "СБ";
        if (raw.contains("вос") || raw.contains("sunday")) return "ВС";
        LocalDate date = parseDate(data.dateKey, LocalDate.now());
        return date.getDayOfWeek().getDisplayName(TextStyle.SHORT, RU).toUpperCase(RU).replace(".", "");
    }

    private static String lessonCountLabel(ScheduleData data) {
        int count = data.lessons.size();
        if (count == 0) return "0 пар";
        if (count == 1) return "1 пара";
        if (count >= 2 && count <= MAX_LESSONS) return count + " пары";
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
        return clean(time).replace("-", "\u2013");
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
        String theme = SchedulePrefs.getWidgetTheme(context);
        String accent = SchedulePrefs.getAccentColor(context);
        LocalDate selectedDate = SchedulePrefs.getWidgetSelectedDate(context);
        LocalDate today = LocalDate.now();
        LocalDate weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int mutedColor = ThemeRes.getMutedColor(theme);
        int accentColor = ThemeRes.getAccentColorHex(accent);
        for (int i = 0; i < DAY_CELL_IDS.length; i++) {
            int viewId = DAY_CELL_IDS[i];
            LocalDate date = weekStart.plusDays(i);
            boolean selected = date.equals(selectedDate);
            boolean isToday = date.equals(today);
            int background = selected
                    ? ThemeRes.getDaySelectedBg(accent)
                    : isToday ? ThemeRes.getDayTodayBg(accent) : ThemeRes.getDayBg(theme);
            int color = selected ? -1 : isToday ? accentColor : mutedColor;
            views.setTextViewText(viewId, dayCellLabel(date));
            views.setTextColor(viewId, color);
            views.setInt(viewId, "setBackgroundResource", background);
            views.setOnClickPendingIntent(viewId, selectDayIntent(context, date, i));
        }
        views.setTextColor(R.id.previousWeekButton, accentColor);
        views.setTextColor(R.id.nextWeekButton, accentColor);
        views.setOnClickPendingIntent(R.id.previousWeekButton, shiftWeekIntent(context, -1));
        views.setOnClickPendingIntent(R.id.nextWeekButton, shiftWeekIntent(context, 1));
    }

    private static String dayCellLabel(LocalDate date) {
        return date.getDayOfWeek().getDisplayName(TextStyle.SHORT, RU).toUpperCase(RU).replace(".", "")
                + "\n" + date.getDayOfMonth();
    }

    private static String widgetTitle(Context context, String name, boolean isTeacher, LocalDate date) {
        String day = date.equals(LocalDate.now())
                ? context.getString(R.string.widget_today)
                : SHORT_DATE.format(date);
        String entityName;
        if (clean(name).isEmpty()) {
            entityName = isTeacher ? "преподаватель" : context.getString(R.string.widget_group);
        } else {
            entityName = name.toUpperCase(RU);
        }
        return day + " · " + entityName;
    }

    private static String formatTime(long millis) {
        return new SimpleDateFormat("HH:mm", RU).format(new Date(millis));
    }

    private static LocalDate parseDate(String rawDate, LocalDate fallback) {
        try {
            return LocalDate.parse(rawDate);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static PendingIntent openAppIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent selectDayIntent(Context context, LocalDate date, int index) {
        Intent intent = new Intent(context, ScheduleWidgetProvider.class)
                .setAction(ACTION_SELECT_DAY)
                .putExtra(EXTRA_DATE, date.toString());
        return PendingIntent.getBroadcast(context, index + 100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent shiftWeekIntent(Context context, int weeks) {
        Intent intent = new Intent(context, ScheduleWidgetProvider.class)
                .setAction(ACTION_SHIFT_WEEK)
                .putExtra(EXTRA_WEEK_OFFSET, weeks);
        return PendingIntent.getBroadcast(context, weeks < 0 ? 201 : 202, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
