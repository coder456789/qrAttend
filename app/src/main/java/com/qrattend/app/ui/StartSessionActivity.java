package com.qrattend.app.ui;

import android.Manifest;
import android.app.TimePickerDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.qrattend.app.R;
import com.qrattend.app.data.model.AttendanceSession;
import com.qrattend.app.data.model.ClassInfo;
import com.qrattend.app.data.repository.ClassRepository;
import com.qrattend.app.data.repository.SessionRepository;
import com.qrattend.app.firebase.AuthManager;
import com.qrattend.app.location.LocationHelper;
import com.qrattend.app.qr.QRGeneratorUtil;
import com.qrattend.app.security.AESCryptoUtil;
import com.qrattend.app.utils.Constants;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StartSessionActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_CODE = 200;

    private Spinner spinnerClass, spinnerDuration;
    private TextView tvLocationLabel, tvLocationCoords;
    private TextView tvLectureStart, tvLectureEnd;
    private MaterialButton btnGetLocation, btnStartSession, btnAddClass;
    private MaterialButton btnPickStartTime, btnPickEndTime;
    private ProgressBar progressLocation, progressStartSession;

    /** QR session duration options (minutes) — controls how long the QR is accepted */
    private static final int[] DURATION_MINUTES = {1, 2, 3, 4, 5};

    private AuthManager authManager;
    private ClassRepository classRepo;
    private SessionRepository sessionRepo;

    private List<ClassInfo> classesList = new ArrayList<>();
    private List<String> classDocIds = new ArrayList<>();

    private double latitude = 0, longitude = 0;
    private float locationAccuracy = Float.MAX_VALUE;
    private boolean locationSet = false;

    /** Lecture start/end time selected by the teacher (optional). */
    private Calendar lectureStartCal = null;
    private Calendar lectureEndCal   = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_session);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        spinnerClass         = findViewById(R.id.spinnerClass);
        tvLocationLabel      = findViewById(R.id.tvLocationLabel);
        tvLocationCoords     = findViewById(R.id.tvLocationCoords);
        btnGetLocation       = findViewById(R.id.btnGetLocation);
        btnStartSession      = findViewById(R.id.btnStartSession);
        progressLocation     = findViewById(R.id.progressLocation);
        progressStartSession = findViewById(R.id.progressStartSession);
        btnAddClass          = findViewById(R.id.btnAddClass);
        spinnerDuration      = findViewById(R.id.spinnerDuration);

        // Lecture time pickers (may be absent in older layout versions)
        tvLectureStart  = findViewById(R.id.tvLectureStart);
        tvLectureEnd    = findViewById(R.id.tvLectureEnd);
        btnPickStartTime = findViewById(R.id.btnPickStartTime);
        btnPickEndTime   = findViewById(R.id.btnPickEndTime);

        authManager = new AuthManager();
        classRepo   = new ClassRepository();
        sessionRepo = new SessionRepository();

        setupDurationSpinner();

        btnGetLocation.setOnClickListener(v -> fetchLocation());
        btnStartSession.setOnClickListener(v -> checkConnectivityThenStart());
        btnAddClass.setOnClickListener(v -> showAddClassDialog());

        if (btnPickStartTime != null) {
            btnPickStartTime.setOnClickListener(v -> showTimePicker(true));
        }
        if (btnPickEndTime != null) {
            btnPickEndTime.setOnClickListener(v -> showTimePicker(false));
        }

        loadClasses();
    }

    private void setupDurationSpinner() {
        String[] labels = {"1 minute", "2 minutes", "3 minutes", "4 minutes", "5 minutes"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDuration.setAdapter(adapter);
        spinnerDuration.setSelection(1); // Default 2 minutes
    }

    private void showTimePicker(boolean isStart) {
        Calendar cal = Calendar.getInstance();
        int hour   = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);

        new TimePickerDialog(this, (view, h, m) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(Calendar.HOUR_OF_DAY, h);
            selected.set(Calendar.MINUTE, m);
            selected.set(Calendar.SECOND, 0);

            String label = String.format(Locale.getDefault(), "%02d:%02d %s",
                    h > 12 ? h - 12 : (h == 0 ? 12 : h), m, h >= 12 ? "PM" : "AM");

            if (isStart) {
                lectureStartCal = selected;
                if (tvLectureStart != null) tvLectureStart.setText("Start: " + label);
            } else {
                lectureEndCal = selected;
                if (tvLectureEnd != null) tvLectureEnd.setText("End: " + label);
            }
        }, hour, minute, false).show();
    }

    private void loadClasses() {
        String uid = authManager.getCurrentUserId();
        if (uid == null) return;

        classRepo.getClassesByTeacher(uid, classes -> {
            classesList  = classes != null ? classes : new ArrayList<>();
            classDocIds  = new ArrayList<>();
            List<String> names = new ArrayList<>();

            for (ClassInfo ci : classesList) {
                names.add(ci.getSubject() + " — " + ci.getClassName());
                classDocIds.add(""); // placeholder
            }

            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, names);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerClass.setAdapter(spinnerAdapter);
        });
    }

    // ── Connectivity Prerequisite Check (Teacher) ──────────────────────────

    /**
     * Called when the teacher taps "Start Session".
     * Checks WiFi, Bluetooth, and Location before proceeding.
     * If any are off → shows a mandatory blocking dialog.
     */
    private void checkConnectivityThenStart() {
        if (isWifiOn() && isBluetoothOn() && isLocationOn()) {
            startSession();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.connectivity_required_title))
                .setMessage(getString(R.string.connectivity_required_message))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.connectivity_open_settings), (d, w) -> {
                    startActivity(new Intent(
                            android.provider.Settings.ACTION_WIRELESS_SETTINGS));
                })
                .setNegativeButton(getString(R.string.cancel), (d, w) -> d.dismiss())
                .show();
    }

    private boolean isWifiOn() {
        WifiManager wm = (WifiManager) getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        return wm != null && wm.isWifiEnabled();
    }

    private boolean isBluetoothOn() {
        try {
            BluetoothManager mgr = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter bt = mgr != null ? mgr.getAdapter() : null;
            return bt != null && bt.isEnabled();
        } catch (SecurityException e) {
            return false; // BLUETOOTH_CONNECT not granted yet (Android 12+)
        }
    }

    private boolean isLocationOn() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    // ── Location Fetch ──────────────────────────────────────────────────────

    private void fetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CODE);
            return;
        }

        progressLocation.setVisibility(View.VISIBLE);
        btnGetLocation.setEnabled(false);

        LocationHelper.fetchQuickLocation(this, new LocationHelper.LocationCallback() {
            @Override
            public void onSuccess(Location location) {
                runOnUiThread(() -> {
                    latitude         = location.getLatitude();
                    longitude        = location.getLongitude();
                    locationAccuracy = location.hasAccuracy() ? location.getAccuracy() : Float.MAX_VALUE;
                    locationSet      = true;
                    tvLocationLabel.setText(R.string.get_my_location);

                    String accuracyText = location.hasAccuracy()
                            ? String.format(" (±%.0fm)", location.getAccuracy()) : "";
                    tvLocationCoords.setText(getString(R.string.location_fetched,
                            latitude, longitude) + accuracyText);
                    tvLocationCoords.setVisibility(View.VISIBLE);
                    progressLocation.setVisibility(View.GONE);
                    btnGetLocation.setEnabled(true);

                    if (location.hasAccuracy() && location.getAccuracy() > Constants.DEFAULT_GEOFENCE_RADIUS) {
                        Toast.makeText(StartSessionActivity.this,
                                "⚠ GPS accuracy is ±" + String.format("%.0f", location.getAccuracy())
                                        + "m — move near a window and tap again.",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFailure(String reason) {
                runOnUiThread(() -> {
                    progressLocation.setVisibility(View.GONE);
                    btnGetLocation.setEnabled(true);
                    Toast.makeText(StartSessionActivity.this,
                            getString(R.string.error_location_unavailable), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocation();
        }
    }

    private void startSession() {
        if (classesList.isEmpty() || spinnerClass.getSelectedItemPosition() < 0) {
            Toast.makeText(this, getString(R.string.error_select_class), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!locationSet) {
            Toast.makeText(this, getString(R.string.error_get_location), Toast.LENGTH_SHORT).show();
            return;
        }

        if (locationAccuracy > Constants.TEACHER_MAX_ACCEPTABLE_ACCURACY) {
            new AlertDialog.Builder(this)
                    .setTitle("⚠ Poor GPS Accuracy")
                    .setMessage(getString(R.string.error_teacher_gps_poor,
                            String.format("%.0f", locationAccuracy)))
                    .setPositiveButton("Retry Location", (d, w) -> fetchLocation())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }

        ClassInfo selectedClass = classesList.get(spinnerClass.getSelectedItemPosition());
        String uid = authManager.getCurrentUserId();
        if (uid == null) return;

        progressStartSession.setVisibility(View.VISIBLE);
        btnStartSession.setEnabled(false);

        try {
            String sessionKey      = AESCryptoUtil.generateSessionKey();
            int    durationMinutes = DURATION_MINUTES[spinnerDuration.getSelectedItemPosition()];
            String classId         = selectedClass.getClassName();

            String sessionId = "session_" + classId.replaceAll("\\s+", "_")
                    + "_" + System.currentTimeMillis();

            AttendanceSession session = new AttendanceSession(
                    classId,
                    selectedClass.getClassName(),
                    selectedClass.getSubject(),
                    uid,
                    "",
                    sessionKey,
                    latitude,
                    longitude,
                    Constants.DEFAULT_GEOFENCE_RADIUS,
                    Timestamp.now(),
                    durationMinutes,
                    null,
                    true
            );

            // Store optional lecture start/end times
            if (lectureStartCal != null) {
                session.setLectureStartTime(new Timestamp(new Date(lectureStartCal.getTimeInMillis())));
            }
            if (lectureEndCal != null) {
                session.setLectureEndTime(new Timestamp(new Date(lectureEndCal.getTimeInMillis())));
            }

            sessionRepo.createSession(sessionId, session, task -> {
                progressStartSession.setVisibility(View.GONE);
                btnStartSession.setEnabled(true);

                if (task.isSuccessful()) {
                    // Lock this device as the active session device
                    String thisDeviceId = com.qrattend.app.security.DeviceFingerprint
                            .getFingerprint(StartSessionActivity.this);
                    java.util.Map<String, Object> lockUpdate = new java.util.HashMap<>();
                    lockUpdate.put("activeDeviceId", thisDeviceId);
                    new com.qrattend.app.data.repository.TeacherRepository()
                            .updateTeacher(uid, lockUpdate, t -> { /* fire-and-forget */ });

                    Intent intent = new Intent(this, DisplayQRActivity.class);
                    intent.putExtra("session_id",       sessionId);
                    intent.putExtra("session_key",      sessionKey);
                    intent.putExtra("teacher_id",       uid);
                    intent.putExtra("course_id",        selectedClass.getClassName());
                    intent.putExtra("latitude",         latitude);
                    intent.putExtra("longitude",        longitude);
                    intent.putExtra("duration_minutes", durationMinutes);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(this, getString(R.string.error_start_session),
                            Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            progressStartSession.setVisibility(View.GONE);
            btnStartSession.setEnabled(true);
            Toast.makeText(this, getString(R.string.error_start_session), Toast.LENGTH_LONG).show();
        }
    }

    private void showAddClassDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding / 2, padding, 0);

        final EditText etClassName = new EditText(this);
        etClassName.setHint(R.string.enter_class_name);
        layout.addView(etClassName);

        final EditText etSubject = new EditText(this);
        etSubject.setHint(R.string.enter_subject);
        layout.addView(etSubject);

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_class_title)
                .setView(layout)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String className = etClassName.getText().toString().trim();
                    String subject   = etSubject.getText().toString().trim();

                    if (className.isEmpty()) {
                        Toast.makeText(this, getString(R.string.error_class_name_empty), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (subject.isEmpty()) {
                        Toast.makeText(this, getString(R.string.error_subject_empty), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String uid = authManager.getCurrentUserId();
                    if (uid == null) return;

                    // Generate unique 6-char alphanumeric join code
                    String joinCode = generateJoinCode();

                    ClassInfo newClass = new ClassInfo(className, subject, uid, new ArrayList<>());
                    newClass.setJoinCode(joinCode);

                    String docId = "class_" + className.replaceAll("\\s+", "_")
                            + "_" + System.currentTimeMillis();

                    classRepo.addClass(docId, newClass, task -> {
                        if (task.isSuccessful()) {
                            // Show the join code to the teacher
                            new AlertDialog.Builder(this)
                                    .setTitle("Class Created! 🎉")
                                    .setMessage("Share this code with your students so they can join:\n\n"
                                            + "📋  " + joinCode + "\n\n"
                                            + "Class: " + subject + " — " + className)
                                    .setPositiveButton("Copy & Close", (d, w) -> {
                                        android.content.ClipboardManager clipboard =
                                                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                        android.content.ClipData clip =
                                                android.content.ClipData.newPlainText("Join Code", joinCode);
                                        clipboard.setPrimaryClip(clip);
                                        Toast.makeText(this, "Code copied: " + joinCode, Toast.LENGTH_SHORT).show();
                                        loadClasses();
                                    })
                                    .setNegativeButton("Close", (d, w) -> loadClasses())
                                    .setCancelable(false)
                                    .show();
                        } else {
                            Toast.makeText(this, getString(R.string.error_create_class), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Generates a random 6-character uppercase alphanumeric join code (e.g. "AX7K9M").
     * Excludes look-alike characters: 0, O, 1, I, L.
     */
    private String generateJoinCode() {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        java.util.Random rnd = new java.util.Random();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }
}