package com.kirix.schedule;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", RU);
    private EditText groupInput;
    private TextView statusText;
    private Button dayPickerButton;
    private LocalDate selectedDate = LocalDate.now();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        groupInput = findViewById(R.id.groupInput);
        statusText = findViewById(R.id.statusText);
        Button saveButton = findViewById(R.id.saveButton);
        Button pinWidgetButton = findViewById(R.id.pinWidgetButton);
        dayPickerButton = findViewById(R.id.dayPickerButton);
        selectedDate = SchedulePrefs.getSelectedDate(this);

        groupInput.setText(SchedulePrefs.getGroup(this));
        groupInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveAndRefresh();
                return true;
            }
            return false;
        });

        saveButton.setOnClickListener(view -> saveAndRefresh());
        setupDayPickerButton();
        setupPinWidgetButton(pinWidgetButton);
        renderStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderStatus();
    }

    private void saveAndRefresh() {
        String group = groupInput.getText().toString().trim();
        if (group.isEmpty()) {
            statusText.setText(getString(R.string.status_enter_group));
            ScheduleWidgetProvider.showSetup(this);
            return;
        }

        SchedulePrefs.setGroup(this, group);
        ScheduleWidgetProvider.showLoading(this);
        ScheduleUpdateJobService.scheduleDailyAtOne(this);
        ScheduleUpdateJobService.scheduleNow(this);
        statusText.setText(getString(R.string.status_saved));
    }

    private void setupDayPickerButton() {
        updateDayPickerLabel();
        dayPickerButton.setOnClickListener(view -> {
            DatePickerDialog dialog = new DatePickerDialog(
                    this,
                    (picker, year, month, dayOfMonth) -> {
                        selectedDate = LocalDate.of(year, month + 1, dayOfMonth);
                        SchedulePrefs.setSelectedDate(this, selectedDate);
                        updateDayPickerLabel();
                        renderStatus();
                    },
                    selectedDate.getYear(),
                    selectedDate.getMonthValue() - 1,
                    selectedDate.getDayOfMonth()
            );
            dialog.show();
        });
    }

    private void updateDayPickerLabel() {
        dayPickerButton.setText(DAY_FORMAT.format(selectedDate));
    }

    private void setupPinWidgetButton(Button button) {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (!manager.isRequestPinAppWidgetSupported()) {
            button.setVisibility(View.GONE);
            return;
        }

        button.setOnClickListener(view -> {
            ComponentName provider = new ComponentName(this, ScheduleWidgetProvider.class);
            Intent success = new Intent(this, MainActivity.class);
            PendingIntent callback = PendingIntent.getActivity(
                    this,
                    1,
                    success,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            manager.requestPinAppWidget(provider, null, callback);
        });
    }

    private void renderStatus() {
        String group = SchedulePrefs.getGroup(this);
        ScheduleArchive archive = ScheduleArchiveStore.loadForCurrentGroup(this);
        ScheduleData selectedSchedule = archive == null ? null : archive.getDay(selectedDate);
        String lastError = SchedulePrefs.getLastError(this);

        if (group.trim().isEmpty()) {
            statusText.setText(getString(R.string.status_no_group));
            return;
        }

        StringBuilder text = new StringBuilder(getString(R.string.status_selected_group, group));
        text.append('\n').append(getString(R.string.status_selected_day, DAY_FORMAT.format(selectedDate)));
        if (archive != null) {
            text.append('\n').append(getString(
                    R.string.status_cache_summary,
                    archive.getCachedDayCount(),
                    formatTime(archive.updatedAtMillis)
            ));
        }

        if (selectedSchedule == null) {
            text.append("\n\n").append(getString(R.string.status_no_day_data));
            appendError(text, lastError, getString(R.string.status_last_error));
            statusText.setText(text.toString());
            return;
        }

        text.append("\n\n").append(selectedSchedule.widgetBody());
        appendError(text, lastError, getString(R.string.status_last_update_error));
        statusText.setText(text.toString());
    }

    private void appendError(StringBuilder text, String lastError, String label) {
        if (lastError != null && !lastError.trim().isEmpty()) {
            text.append("\n\n")
                    .append(label)
                    .append('\n')
                    .append(lastError.trim());
        }
    }

    private String formatTime(long millis) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", RU).format(new Date(millis));
    }
}
