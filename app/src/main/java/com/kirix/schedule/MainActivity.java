package com.kirix.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_PICK_IMAGE = 1001;
    private static final Locale RU = Locale.forLanguageTag("ru");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy", RU);

    private EditText groupInput;
    private TextView statusText;
    private Button dayPickerButton;
    private Button pickQrButton;
    private Button clearQrButton;
    private ImageView qrPreviewImage;
    private RadioButton radioGroupType;
    private RadioButton radioTeacher;
    private LocalDate selectedDate = LocalDate.now();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(getThemeResourceId(SchedulePrefs.getWidgetTheme(this), SchedulePrefs.getAccentColor(this)));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        groupInput = findViewById(R.id.groupInput);
        statusText = findViewById(R.id.statusText);
        Button saveButton = findViewById(R.id.saveButton);
        Button pinWidgetButton = findViewById(R.id.pinWidgetButton);
        dayPickerButton = findViewById(R.id.dayPickerButton);
        pickQrButton = findViewById(R.id.pickQrButton);
        clearQrButton = findViewById(R.id.clearQrButton);
        qrPreviewImage = findViewById(R.id.qrPreviewImage);
        RadioGroup typeRadioGroup = findViewById(R.id.typeRadioGroup);
        radioGroupType = findViewById(R.id.radioGroup);
        radioTeacher = findViewById(R.id.radioTeacher);

        selectedDate = SchedulePrefs.getSelectedDate(this);
        if (SchedulePrefs.isTeacher(this)) {
            radioTeacher.setChecked(true);
        } else {
            radioGroupType.setChecked(true);
        }
        updateInputHints();
        typeRadioGroup.setOnCheckedChangeListener((rg, checkedId) -> updateInputHints());

        groupInput.setText(SchedulePrefs.getGroup(this));
        groupInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                saveAndRefresh();
                return true;
            }
            return false;
        });

        saveButton.setOnClickListener(view -> saveAndRefresh());
        pickQrButton.setOnClickListener(view -> pickQrImage());
        clearQrButton.setOnClickListener(view -> {
            SchedulePrefs.deleteQrCode(this);
            renderStatus();
            ScheduleWidgetProvider.showSelectedSchedule(this);
        });
        setupCityPickerButton(findViewById(R.id.cityPickerButton));
        setupDayPickerButton();
        setupPinWidgetButton(pinWidgetButton);

        RadioGroup themeRadioGroup = findViewById(R.id.themeRadioGroup);
        RadioGroup colorRadioGroup = findViewById(R.id.colorRadioGroup);
        setupStylingOptions(themeRadioGroup, colorRadioGroup);
        renderStatus();
    }

    private void updateInputHints() {
        TextView sectionHint = findViewById(R.id.sectionGroupHint);
        if (radioTeacher.isChecked()) {
            groupInput.setHint("ФИО преподавателя");
            if (sectionHint != null) {
                sectionHint.setText("Введите фамилию или ФИО преподавателя так же, как на сайте. Например: Абилова.");
            }
        } else {
            groupInput.setHint(R.string.group_hint);
            if (sectionHint != null) {
                sectionHint.setText(R.string.section_group_hint);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderStatus();
    }

    private void saveAndRefresh() {
        String group = groupInput.getText().toString().trim();
        boolean isTeacher = radioTeacher.isChecked();
        if (group.isEmpty()) {
            statusText.setText(isTeacher ? "Введите ФИО преподавателя" : getString(R.string.status_enter_group));
            ScheduleWidgetProvider.showSetup(this);
            ScheduleUpdateJobService.cancelAll(this);
            return;
        }
        SchedulePrefs.setGroup(this, group, isTeacher);
        ScheduleWidgetProvider.showLoading(this);
        ScheduleUpdateJobService.scheduleDailyAtOne(this);
        ScheduleUpdateJobService.schedulePeriodic(this);
        ScheduleUpdateJobService.scheduleNow(this);
        statusText.setText(getString(R.string.status_saved));
    }

    private void pickQrImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Выберите QR-код"), REQUEST_PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            saveQrCodeImage(data.getData());
        }
    }

    private void saveQrCodeImage(Uri uri) {
        InputStream is = null;
        try {
            is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap == null) {
                statusText.setText("Не удалось загрузить изображение");
                return;
            }
            Bitmap resized = resizeBitmap(bitmap, 512);
            File file = new File(getFilesDir(), "pass_qr.png");
            FileOutputStream fos = new FileOutputStream(file);
            resized.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            if (resized != bitmap) {
                bitmap.recycle();
            }
            renderStatus();
            ScheduleWidgetProvider.showSelectedSchedule(this);
        } catch (Exception e) {
            statusText.setText("Ошибка сохранения QR-кода: " + e.getMessage());
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Bitmap resizeBitmap(Bitmap source, int maxResolution) {
        int width = source.getWidth();
        int height = source.getHeight();
        int newWidth = width;
        int newHeight = height;
        if (width > maxResolution || height > maxResolution) {
            if (width > height) {
                newWidth = maxResolution;
                newHeight = (height * maxResolution) / width;
            } else {
                newHeight = maxResolution;
                newWidth = (width * maxResolution) / height;
            }
        }
        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true);
    }

    private void setupCityPickerButton(Button button) {
        updateCityButtonLabel(button);
        button.setOnClickListener(view -> showCityPickerDialog(button));
    }

    private void updateCityButtonLabel(Button button) {
        button.setText(SchedulePrefs.getCityName(this));
    }

    private void showCityPickerDialog(Button button) {
        // slug обязателен корректный; ID городов можно проверить на gismeteo.ru
        String[][] cities = {
                {"Челябинск", "chelyabinsk-4565"},
                {"Екатеринбург", "yekaterinburg-11120"},
                {"Москва", "moscow-4368"},
                {"Санкт-Петербург", "saint-petersburg-2606"},
                {"Магнитогорск", "magnitogorsk-4589"},
                {"Уфа", "ufa-350"},
                {"Пермь", "perm-4517"},
                {"Казань", "kazan-3149"},
                {"Новосибирск", "novosibirsk-4690"},
                {"Сочи", "sochi-5233"},
                {getString(R.string.weather_city_custom), ""}
        };
        String[] names = new String[cities.length];
        for (int i = 0; i < cities.length; i++) {
            names[i] = cities[i][0];
        }
        new AlertDialog.Builder(this)
                .setTitle("Город прогноза")
                .setItems(names, (dialog, which) -> {
                    if (which == cities.length - 1) {
                        promptCustomCity(button);
                    } else {
                        applyCity(button, cities[which][0], cities[which][1]);
                    }
                })
                .show();
    }

    private void promptCustomCity(Button button) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("https://www.gismeteo.ru/weather-city-1234/");
        input.setTextSize(16f);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(60, 20, 60, 0);
        container.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("Ссылка на город Gismeteo")
                .setMessage("Откройте gismeteo.ru, найдите город и вставьте сюда ссылку вида https://www.gismeteo.ru/weather-yekaterinburg-11120/")
                .setView(container)
                .setPositiveButton("OK", (dialog, which) -> {
                    String slug = GismeteoApiClient.extractSlug(input.getText().toString());
                    if (slug == null) {
                        statusText.setText("Не удалось распознать ссылку. Нужна вида weather-city-1234");
                        return;
                    }
                    String name = slug.substring(0, slug.lastIndexOf('-'));
                    name = name.substring(0, 1).toUpperCase(RU) + name.substring(1);
                    applyCity(button, name, slug);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void applyCity(Button button, String name, String slug) {
        SchedulePrefs.setCity(this, name, slug);
        updateCityButtonLabel(button);
        statusText.setText("Город погоды: " + name + ". Обновляю прогноз…");
        ScheduleWidgetProvider.triggerWeatherRefresh(this);
    }

    private void setupDayPickerButton() {
        updateDayPickerLabel();
        dayPickerButton.setOnClickListener(view -> {
            DatePickerDialog dialog = new DatePickerDialog(this,
                    (picker, year, month, dayOfMonth) -> {
                        selectedDate = LocalDate.of(year, month + 1, dayOfMonth);
                        SchedulePrefs.setSelectedDate(this, selectedDate);
                        updateDayPickerLabel();
                        renderStatus();
                    },
                    selectedDate.getYear(),
                    selectedDate.getMonthValue() - 1,
                    selectedDate.getDayOfMonth());
            dialog.getDatePicker().setMaxDate(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);
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
            PendingIntent callback = PendingIntent.getActivity(this, 1, success,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            manager.requestPinAppWidget(provider, null, callback);
        });
    }

    private void renderStatus() {
        String group = SchedulePrefs.getGroup(this);
        boolean isTeacher = SchedulePrefs.isTeacher(this);
        ScheduleArchive archive;
        try {
            archive = ScheduleArchiveStore.loadForCurrentGroup(this);
        } catch (Exception e) {
            archive = null;
        }
        ScheduleData selectedSchedule = archive == null ? null : archive.getDay(selectedDate);
        String lastError = SchedulePrefs.getLastError(this);

        if (group.trim().isEmpty()) {
            statusText.setText(getString(R.string.status_no_group));
            updateQrPreview();
            return;
        }
        String entityLabel = isTeacher
                ? "Выбран преподаватель: " + group
                : getString(R.string.status_selected_group, group);
        StringBuilder text = new StringBuilder(entityLabel);
        text.append('\n').append(getString(R.string.status_selected_day, DAY_FORMAT.format(selectedDate)));
        if (archive != null) {
            text.append('\n').append(getString(R.string.status_cache_summary,
                    archive.getCachedDayCount(), formatTime(archive.updatedAtMillis)));
        }
        if (selectedSchedule == null) {
            text.append("\n\n").append(getString(R.string.status_no_day_data));
            appendError(text, lastError, getString(R.string.status_last_error));
            statusText.setText(text.toString());
            updateQrPreview();
            return;
        }
        text.append("\n\n").append(selectedSchedule.widgetBody());
        appendError(text, lastError, getString(R.string.status_last_update_error));
        statusText.setText(text.toString());
        updateQrPreview();
    }

    private void updateQrPreview() {
        File qrFile = new File(getFilesDir(), "pass_qr.png");
        Bitmap bitmap = qrFile.exists() ? BitmapFactory.decodeFile(qrFile.getAbsolutePath()) : null;
        if (bitmap != null) {
            qrPreviewImage.setImageBitmap(bitmap);
            qrPreviewImage.setVisibility(View.VISIBLE);
            clearQrButton.setVisibility(View.VISIBLE);
        } else {
            qrPreviewImage.setVisibility(View.GONE);
            clearQrButton.setVisibility(View.GONE);
        }
    }

    private void appendError(StringBuilder text, String lastError, String label) {
        if (lastError != null && !lastError.trim().isEmpty()) {
            text.append("\n\n").append(label).append('\n').append(lastError.trim());
        }
    }

    private String formatTime(long millis) {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm", RU).format(new Date(millis));
    }

    private int getThemeResourceId(String theme, String accent) {
        boolean dark = "dark".equals(theme) || "dark_glass".equals(theme);
        switch (accent) {
            case "mint":
                return dark ? R.style.AppTheme_Dark_Mint : R.style.AppTheme_Light_Mint;
            case "emerald":
                return dark ? R.style.AppTheme_Dark_Emerald : R.style.AppTheme_Light_Emerald;
            case "red":
                return dark ? R.style.AppTheme_Dark_Red : R.style.AppTheme_Light_Red;
            case "orange":
                return dark ? R.style.AppTheme_Dark_Orange : R.style.AppTheme_Light_Orange;
            default:
                return dark ? R.style.AppTheme_Dark_Indigo : R.style.AppTheme_Light_Indigo;
        }
    }

    private void setupStylingOptions(RadioGroup themeRadioGroup, RadioGroup colorRadioGroup) {
        String theme = SchedulePrefs.getWidgetTheme(this);
        if ("light_glass".equals(theme)) {
            themeRadioGroup.check(R.id.radioThemeLightGlass);
        } else if ("dark".equals(theme)) {
            themeRadioGroup.check(R.id.radioThemeDark);
        } else if ("light".equals(theme)) {
            themeRadioGroup.check(R.id.radioThemeLight);
        } else {
            themeRadioGroup.check(R.id.radioThemeDarkGlass);
        }
        String accent = SchedulePrefs.getAccentColor(this);
        if ("mint".equals(accent)) {
            colorRadioGroup.check(R.id.radioColorMint);
        } else if ("emerald".equals(accent)) {
            colorRadioGroup.check(R.id.radioColorEmerald);
        } else if ("red".equals(accent)) {
            colorRadioGroup.check(R.id.radioColorRed);
        } else if ("orange".equals(accent)) {
            colorRadioGroup.check(R.id.radioColorOrange);
        } else {
            colorRadioGroup.check(R.id.radioColorIndigo);
        }
        themeRadioGroup.setOnCheckedChangeListener((rg, checkedId) -> {
            String selectedTheme = "dark_glass";
            if (checkedId == R.id.radioThemeLightGlass) {
                selectedTheme = "light_glass";
            } else if (checkedId == R.id.radioThemeDark) {
                selectedTheme = "dark";
            } else if (checkedId == R.id.radioThemeLight) {
                selectedTheme = "light";
            }
            if (!selectedTheme.equals(SchedulePrefs.getWidgetTheme(this))) {
                SchedulePrefs.setWidgetTheme(this, selectedTheme);
                ScheduleWidgetProvider.showSelectedSchedule(this);
                recreate();
            }
        });
        colorRadioGroup.setOnCheckedChangeListener((rg, checkedId) -> {
            String selectedAccent = "indigo";
            if (checkedId == R.id.radioColorMint) {
                selectedAccent = "mint";
            } else if (checkedId == R.id.radioColorEmerald) {
                selectedAccent = "emerald";
            } else if (checkedId == R.id.radioColorRed) {
                selectedAccent = "red";
            } else if (checkedId == R.id.radioColorOrange) {
                selectedAccent = "orange";
            }
            if (!selectedAccent.equals(SchedulePrefs.getAccentColor(this))) {
                SchedulePrefs.setAccentColor(this, selectedAccent);
                ScheduleWidgetProvider.showSelectedSchedule(this);
                recreate();
            }
        });
    }
}
