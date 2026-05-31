package com.kirix.schedule;

import android.content.Context;
import android.util.AtomicFile;

import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;

final class ScheduleArchiveStore {
    private static final String ARCHIVE_FILE = "schedule_archive.json";

    private ScheduleArchiveStore() {
    }

    static ScheduleArchive loadForCurrentGroup(Context context) {
        ScheduleArchive archive = load(context);
        String selectedGroup = SchedulePrefs.getGroup(context);
        if (archive == null || !groupsMatch(selectedGroup, archive.group)) {
            return null;
        }
        return archive;
    }

    static ScheduleData getSchedule(Context context, LocalDate date) {
        ScheduleArchive archive = loadForCurrentGroup(context);
        return archive == null ? null : archive.getDay(date);
    }

    static ScheduleData getTodaySchedule(Context context) {
        return getSchedule(context, LocalDate.now());
    }

    static void save(Context context, ScheduleArchive freshArchive) throws IOException {
        ScheduleArchive merged = freshArchive.mergeHistoricalDaysFrom(load(context));
        write(context, merged);
    }

    static void saveDay(Context context, ScheduleData day) throws IOException {
        ScheduleArchive archive = load(context);
        if (archive == null || !groupsMatch(archive.group, day.group)) {
            archive = ScheduleArchive.containingDay(day);
        } else {
            archive = archive.withDay(day);
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
        String safeSecond = second == null ? "" : second;
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

    private static ScheduleArchive load(Context context) {
        AtomicFile file = archiveFile(context);
        try {
            byte[] raw = file.readFully();
            return ScheduleArchive.fromJson(new String(raw, StandardCharsets.UTF_8));
        } catch (IOException | JSONException ignored) {
            ScheduleData legacy = SchedulePrefs.getLegacySchedule(context);
            return legacy == null ? null : ScheduleArchive.containingDay(legacy);
        }
    }

    private static void write(Context context, ScheduleArchive archive) throws IOException {
        AtomicFile file = archiveFile(context);
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            output.write(archive.toJson().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (JSONException error) {
            if (output != null) {
                file.failWrite(output);
            }
            throw new IOException("Could not encode schedule archive", error);
        } catch (IOException error) {
            if (output != null) {
                file.failWrite(output);
            }
            throw error;
        }
    }

    private static AtomicFile archiveFile(Context context) {
        File file = new File(context.getApplicationContext().getFilesDir(), ARCHIVE_FILE);
        return new AtomicFile(file);
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim()
                .toLowerCase(Locale.forLanguageTag("ru"))
                .replace('\u0451', '\u0435')
                .replace(" ", "");
    }
}
