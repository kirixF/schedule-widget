package com.kirix.schedule;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ScheduleUpdateJobService extends JobService {
    private static final long DAILY_DEADLINE_WINDOW_MS = 15 * 60 * 1000L;
    private static final int JOB_NOW = 42100;
    private static final int JOB_DAILY = 42101;
    private static final int JOB_PERIODIC = 42102;
    private static final long PERIODIC_INTERVAL_MS = 30 * 60 * 1000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    public boolean onStartJob(final JobParameters params) {
        final String group = SchedulePrefs.getGroup(this);
        if (group.trim().isEmpty()) {
            ScheduleWidgetProvider.showSetup(this);
            jobFinished(params, false);
            return false;
        }
        if (params.getJobId() == JOB_DAILY) {
            SchedulePrefs.setWidgetSelectedDate(this, LocalDate.now());
        }
        ScheduleData cachedSchedule = null;
        try {
            cachedSchedule = ScheduleArchiveStore.getSchedule(this, SchedulePrefs.getWidgetSelectedDate(this));
        } catch (Exception ignored) {
        }
        if (cachedSchedule == null) {
            ScheduleWidgetProvider.showLoading(this);
        } else {
            ScheduleWidgetProvider.showSelectedSchedule(this);
        }
        executor.execute(() -> {
            try {
                boolean isTeacher = SchedulePrefs.isTeacher(this);
                ScheduleApiClient.ArchiveResult result = ScheduleApiClient.fetchAll(group, isTeacher);
                ScheduleArchiveStore.save(ScheduleUpdateJobService.this, result.archive);
                SchedulePrefs.clearLastError(ScheduleUpdateJobService.this);
                fetchWeatherThenFinish(params);
            } catch (Exception apiError) {
                String apiMessage = apiError.getMessage() == null ? "API сайта не ответил" : apiError.getMessage();
                if (SchedulePrefs.isTeacher(this)) {
                    SchedulePrefs.setLastError(this, apiMessage);
                    fetchWeatherThenFinish(params);
                } else {
                    // Для групп пробуем резервный путь через WebView-парсер сайта.
                    final String groupId = group;
                    runOnUiThread(() -> ScheduleWebParser.fetchToday(ScheduleUpdateJobService.this, groupId,
                            new ScheduleWebParser.Callback() {
                                @Override
                                public void onSuccess(ScheduleData data, String rawJson) {
                                    SchedulePrefs.setLastSchedule(ScheduleUpdateJobService.this, rawJson);
                                    fetchWeatherThenFinish(params);
                                }

                                @Override
                                public void onError(String message) {
                                    SchedulePrefs.setLastError(ScheduleUpdateJobService.this,
                                            "API: " + apiMessage + "\nWebView: " +
                                                    (message == null ? "нет ответа" : message));
                                    fetchWeatherThenFinish(params);
                                }
                            }));
                }
            }
        });
        return true;
    }

    private void fetchWeatherThenFinish(final JobParameters params) {
        try {
            GismeteoWeatherData forecast = GismeteoApiClient.fetchForecastSync(this);
            SchedulePrefs.setLastForecast(this, forecast.toJson());
        } catch (Exception weatherError) {
            String message = weatherError.getMessage() == null || weatherError.getMessage().trim().isEmpty()
                    ? "Погода недоступна"
                    : weatherError.getMessage();
            SchedulePrefs.setLastForecastError(this, message);
        }
        runOnUiThread(() -> {
            ScheduleWidgetProvider.showSelectedSchedule(this);
            jobFinished(params, false);
            scheduleDailyAtOne(this);
        });
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void runOnUiThread(Runnable action) {
        mainHandler.post(action);
    }

    static void scheduleNow(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null || SchedulePrefs.getGroup(context).trim().isEmpty()) {
            return;
        }
        JobInfo job = new JobInfo.Builder(JOB_NOW, new ComponentName(context, ScheduleUpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(0L)
                .build();
        scheduler.schedule(job);
    }

    static void scheduleDailyAtOne(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null || SchedulePrefs.getGroup(context).trim().isEmpty()) {
            return;
        }
        long delay = millisUntilNextOneAm();
        JobInfo job = new JobInfo.Builder(JOB_DAILY, new ComponentName(context, ScheduleUpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(delay)
                .setOverrideDeadline(DAILY_DEADLINE_WINDOW_MS + delay)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }

    private static long millisUntilNextOneAm() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime next = now.withHour(1).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1L);
        }
        return Math.max(0L, Duration.between(now, next).toMillis());
    }

    static void schedulePeriodic(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null || SchedulePrefs.getGroup(context).trim().isEmpty()) {
            return;
        }
        for (JobInfo pending : scheduler.getAllPendingJobs()) {
            if (pending.getId() == JOB_PERIODIC) {
                return;
            }
        }
        JobInfo job = new JobInfo.Builder(JOB_PERIODIC, new ComponentName(context, ScheduleUpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIODIC_INTERVAL_MS)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }

    static void cancelAll(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        scheduler.cancel(JOB_NOW);
        scheduler.cancel(JOB_DAILY);
        scheduler.cancel(JOB_PERIODIC);
    }
}
