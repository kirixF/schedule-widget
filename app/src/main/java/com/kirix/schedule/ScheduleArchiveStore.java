package com.kirix.schedule;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import org.json.JSONException;

/* loaded from: classes2.dex */
final class ScheduleArchiveStore {
    private static final String ARCHIVE_FILE = "schedule_archive.json";

    private ScheduleArchiveStore() {
    }

    static ScheduleArchive loadForCurrentGroup(Context context) throws IOException {
        ScheduleArchive archive = load(context);
        String selectedGroup = SchedulePrefs.getGroup(context);
        if (archive == null || !groupsMatch(selectedGroup, archive.group)) {
            return null;
        }
        return archive;
    }

    static ScheduleData getSchedule(Context context, LocalDate date) throws IOException {
        ScheduleArchive archive = loadForCurrentGroup(context);
        if (archive == null) {
            return null;
        }
        return archive.getDay(date);
    }

    static ScheduleData getTodaySchedule(Context context) {
        try {
            return getSchedule(context, LocalDate.now());
        } catch (IOException e) {
            return null;
        }
    }

    static void save(Context context, ScheduleArchive freshArchive) throws IOException {
        ScheduleArchive merged = freshArchive.mergeHistoricalDaysFrom(load(context));
        write(context, merged);
    }

    static void saveDay(Context context, ScheduleData day) throws IOException {
        ScheduleArchive archive;
        ScheduleArchive archive2 = load(context);
        if (archive2 == null || !groupsMatch(archive2.group, day.group)) {
            archive = ScheduleArchive.containingDay(day);
        } else {
            archive = archive2.withDay(day);
        }
        write(context, archive);
    }

    static boolean groupsMatch(String first, String second) {
        String normalizedFirst = normalize(first);
        String normalizedSecond = normalize(second);
        if (!normalizedFirst.isEmpty() && normalizedFirst.equals(normalizedSecond)) {
            return true;
        }
        String safeFirst = first == null ? "" : first;
        String safeSecond = second != null ? second : "";
        for (String firstPart : safeFirst.split(",")) {
            for (String secondPart : safeSecond.split(",")) {
                String normalizedPart = normalize(firstPart);
                if (!normalizedPart.isEmpty() && normalizedPart.equals(normalize(secondPart))) {
                    return true;
                }
            }
        }
        return false;
    }

    // Кэш разобранного архива: чтение+парс большого JSON — дорогая операция,
    // виджет перерисовывается часто. Ключ — размер файла (меняется при каждой записи).
    private static ScheduleArchive cache;
    private static String cacheGroup = "";
    private static long cacheFileLength = -1;

    private static ScheduleArchive load(Context context) throws IOException {
        AtomicFile file = archiveFile(context);
        File base = file.getBaseFile();
        long length = base.exists() ? base.length() : -1;
        String group = SchedulePrefs.getGroup(context);
        if (cache != null && cacheFileLength == length && cacheGroup.equals(group)) {
            return cache;
        }
        try {
            byte[] raw = file.readFully();
            ScheduleArchive archive = ScheduleArchive.fromJson(new String(raw, StandardCharsets.UTF_8));
            cache = archive;
            cacheGroup = group;
            cacheFileLength = length;
            return archive;
        } catch (IOException | JSONException e) {
            Log.w("ScheduleArchive", "load failed, trying legacy", e);
            ScheduleData legacy = SchedulePrefs.getLegacySchedule(context);
            if (legacy == null) {
                return null;
            }
            return ScheduleArchive.containingDay(legacy);
        }
    }

    private static void write(Context context, ScheduleArchive archive) throws IOException {
        AtomicFile file = archiveFile(context);
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(archive.toJson().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
            cache = archive;
            cacheGroup = SchedulePrefs.getGroup(context);
            File base = file.getBaseFile();
            cacheFileLength = base.exists() ? base.length() : -1;
        } catch (IOException error) {
            if (output != null) {
                file.failWrite(output);
            }
            throw error;
        } catch (JSONException error2) {
            if (output != null) {
                file.failWrite(output);
            }
            throw new IOException("Could not encode schedule archive", error2);
        }
    }

    private static AtomicFile archiveFile(Context context) {
        File file = new File(context.getApplicationContext().getFilesDir(), ARCHIVE_FILE);
        return new AtomicFile(file);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.forLanguageTag("ru")).replace((char) 1105, (char) 1077).replace(" ", "");
    }
}
