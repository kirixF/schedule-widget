package com.kirix.schedule;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ScheduleUpdateJobService extends JobService {
    private static final int JOB_NOW = 42100;
    private static final int JOB_PERIODIC = 42101;
    private static final long PERIOD_MS = 30 * 60 * 1000L;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public boolean onStartJob(JobParameters params) {
        String group = SchedulePrefs.getGroup(this);
        if (group.trim().isEmpty()) {
            ScheduleWidgetProvider.showSetup(this);
            jobFinished(params, false);
            return false;
        }

        ScheduleWidgetProvider.showLoading(this);
        executor.execute(() -> {
            try {
                ScheduleApiClient.Result result = ScheduleApiClient.fetchToday(group);
                SchedulePrefs.setLastSchedule(ScheduleUpdateJobService.this, result.rawJson);
                ScheduleWidgetProvider.showSchedule(ScheduleUpdateJobService.this, result.data);
                jobFinished(params, false);
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
                ScheduleWidgetProvider.showSchedule(ScheduleUpdateJobService.this, data);
                jobFinished(params, false);
            }

            @Override
            public void onError(String message) {
                String fullMessage = "API: " + apiMessage + "\nWebView: " + message;
                SchedulePrefs.setLastError(ScheduleUpdateJobService.this, fullMessage);
                ScheduleWidgetProvider.showError(ScheduleUpdateJobService.this, fullMessage);
                jobFinished(params, false);
            }
        });
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

    static void schedulePeriodic(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null || SchedulePrefs.getGroup(context).trim().isEmpty()) {
            return;
        }
        JobInfo job = new JobInfo.Builder(JOB_PERIODIC, new ComponentName(context, ScheduleUpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIOD_MS)
                .setPersisted(true)
                .build();
        scheduler.schedule(job);
    }
}
