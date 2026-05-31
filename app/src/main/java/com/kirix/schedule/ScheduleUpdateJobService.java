package com.kirix.schedule;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ScheduleUpdateJobService extends JobService {
    private static final int JOB_NOW = 42100;
    private static final int JOB_DAILY = 42101;
    private static final long DAILY_DEADLINE_WINDOW_MS = 15 * 60 * 1000L;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public boolean onStartJob(JobParameters params) {
        String group = SchedulePrefs.getGroup(this);
        if (group.trim().isEmpty()) {
            ScheduleWidgetProvider.showSetup(this);
            return false;
        }

        if (params.getJobId() == JOB_DAILY) {
            SchedulePrefs.setWidgetSelectedDate(this, LocalDate.now());
        }

        ScheduleData cachedSchedule = ScheduleArchiveStore.getSchedule(this, SchedulePrefs.getWidgetSelectedDate(this));
        if (cachedSchedule == null) {
            ScheduleWidgetProvider.showLoading(this);
        } else {
            ScheduleWidgetProvider.showSchedule(this, cachedSchedule);
        }

        executor.execute(() -> {
            try {
                ScheduleApiClient.ArchiveResult result = ScheduleApiClient.fetchAll(group);
                ScheduleArchiveStore.save(ScheduleUpdateJobService.this, result.archive);
                SchedulePrefs.clearLastError(ScheduleUpdateJobService.this);
                ScheduleWidgetProvider.showSelectedSchedule(ScheduleUpdateJobService.this);
                finishJob(params);
            } catch (Exception apiError) {
                String apiMessage = apiError.getMessage() == null
                        ? "API сайта не ответил"
                        : apiError.getMessage();
                new Handler(Looper.getMainLooper()).post(() -> runWebFallback(params, group, apiMessage));
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    private void runWebFallback(JobParameters params, String group, String apiMessage) {
        ScheduleWebParser.fetchToday(this, group, new ScheduleWebParser.Callback() {
            @Override
            public void onSuccess(ScheduleData data, String rawJson) {
                SchedulePrefs.setLastSchedule(ScheduleUpdateJobService.this, rawJson);
                ScheduleWidgetProvider.showSelectedSchedule(ScheduleUpdateJobService.this);
                finishJob(params);
            }

            @Override
            public void onError(String message) {
                String fullMessage = "API: " + apiMessage + "\nWebView: " + message;
                SchedulePrefs.setLastError(ScheduleUpdateJobService.this, fullMessage);
                ScheduleWidgetProvider.showError(ScheduleUpdateJobService.this, fullMessage);
                finishJob(params);
            }
        });
    }

    private void finishJob(JobParameters params) {
        jobFinished(params, false);
        scheduleDailyAtOne(this);
    }

    static void scheduleNow(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        JobInfo job = new JobInfo.Builder(JOB_NOW, new ComponentName(context, ScheduleUpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setOverrideDeadline(0)
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
                .setOverrideDeadline(delay + DAILY_DEADLINE_WINDOW_MS)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }

    private static long millisUntilNextOneAm() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime next = now.withHour(1).withMinute(0).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return Math.max(0, Duration.between(now, next).toMillis());
    }
}
