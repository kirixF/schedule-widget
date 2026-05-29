package com.kirix.schedule;

import android.app.Activity;
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

public final class MainActivity extends Activity {
    private EditText groupInput;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        groupInput = findViewById(R.id.groupInput);
        statusText = findViewById(R.id.statusText);
        Button saveButton = findViewById(R.id.saveButton);
        Button pinWidgetButton = findViewById(R.id.pinWidgetButton);

        groupInput.setText(SchedulePrefs.getGroup(this));
        groupInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveAndRefresh();
                return true;
            }
            return false;
        });

        saveButton.setOnClickListener(view -> saveAndRefresh());
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
        ScheduleUpdateJobService.schedulePeriodic(this);
        ScheduleUpdateJobService.scheduleNow(this);
        statusText.setText(getString(R.string.status_saved));
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
        ScheduleData last = SchedulePrefs.getLastSchedule(this);
        String lastError = SchedulePrefs.getLastError(this);

        if (group.trim().isEmpty()) {
            statusText.setText(getString(R.string.status_no_group));
            return;
        }

        StringBuilder text = new StringBuilder(getString(R.string.status_selected_group, group));
        if (last == null) {
            text.append('\n').append(getString(R.string.status_no_data));
            appendError(text, lastError, getString(R.string.status_last_error));
            statusText.setText(text.toString());
            return;
        }

        text.append('\n').append(last.widgetSubtitle())
                .append("\n\n")
                .append(last.widgetBody());
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
}
