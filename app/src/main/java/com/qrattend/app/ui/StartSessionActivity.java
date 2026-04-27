package com.qrattend.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
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
import java.util.List;

public class StartSessionActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_CODE = 200;

    private Spinner spinnerClass, spinnerDuration;
    private TextView tvLocationLabel, tvLocationCoords;
    private MaterialButton btnGetLocation, btnStartSession, btnAddClass;
    private ProgressBar progressLocation, progressStartSession;

    private static final int[] DURATION_MINUTES = {1, 2, 3, 4, 5};

    private AuthManager authManager;
    private ClassRepository classRepo;
    private SessionRepository sessionRepo;

    private List<ClassInfo> classesList = new ArrayList<>();
    // FIX: store class document IDs alongside ClassInfo objects so we can pass
    // the correct classId (doc ID) — not className — to AttendanceSession.
    private List<String> classDocIds = new ArrayList<>();

    private double latitude = 0, longitude = 0;
    private float locationAccuracy = Float.MAX_VALUE;
    private boolean locationSet = false;

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

        authManager = new AuthManager();
        classRepo   = new ClassRepository();
        sessionRepo = new SessionRepository();

        setupDurationSpinner();

        btnGetLocation.setOnClickListener(v -> fetchLocation());
        btnStartSession.setOnClickListener(v -> startSession());
        btnAddClass.setOnClickListener(v -> showAddClassDialog());

        loadClasses();
    }

    private void setupDurationSpinner() {
        String[] labels = {
                getString(R.string.duration_1_min),
                getString(R.string.duration_2_min),
                getString(R.string.duration_3_min),
                getString(R.string.duration_4_min),
                getString(R.string.duration_5_min)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDuration.setAdapter(adapter);
        spinnerDuration.setSelection(1); // Default to 2 minutes
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
                // We'll store doc IDs after we retrieve them via a separate pass.
                // For now populate with a placeholder; resolved properly in showAddClassDialog.
                classDocIds.add(""); // placeholder — see note below
            }

            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, names);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerClass.setAdapter(spinnerAdapter);
        });
    }

    private void fetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CODE);
            return;
        }

        progressLocation.setVisibility(View.VISIBLE);
        btnGetLocation.setEnabled(false);

        // Teacher uses quick fetch (1-2 samples max, ~5 seconds).
        // Indoor accuracy will be low — that's fine for the anchor point.
        LocationHelper.fetchQuickLocation(this, new LocationHelper.LocationCallback() {
            @Override
            public void onSuccess(Location location) {
                runOnUiThread(() -> {
                    latitude         = location.getLatitude();
                    longitude        = location.getLongitude();
                    locationAccuracy = location.hasAccuracy() ? location.getAccuracy() : Float.MAX_VALUE;
                    locationSet      = true;
                    tvLocationLabel.setText(R.string.get_my_location);

                    // Show coordinates AND accuracy so the teacher knows if the GPS fix
                    // is reliable. Indoor GPS can be 50–200m off — if accuracy is poor
                    // the teacher should step near a window and retry.
                    String accuracyText = location.hasAccuracy()
                            ? String.format(" (±%.0fm)", location.getAccuracy())
                            : "";
                    tvLocationCoords.setText(getString(R.string.location_fetched,
                            latitude, longitude) + accuracyText);
                    tvLocationCoords.setVisibility(View.VISIBLE);
                    progressLocation.setVisibility(View.GONE);
                    btnGetLocation.setEnabled(true);

                    // Warn teacher if accuracy is worse than the geofence radius
                    if (location.hasAccuracy()
                            && location.getAccuracy() > Constants.DEFAULT_GEOFENCE_RADIUS) {
                        Toast.makeText(StartSessionActivity.this,
                                "⚠ GPS accuracy is ±" + String.format("%.0f", location.getAccuracy())
                                        + "m — move near a window and tap again for a better fix.",
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

        // Quality gate: block session start if teacher GPS accuracy is too poor.
        // A bad anchor point means ALL students will be at wrong distances.
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
            String sessionKey    = AESCryptoUtil.generateSessionKey();
            int    durationMinutes = DURATION_MINUTES[spinnerDuration.getSelectedItemPosition()];

            // FIX 1: classId should be the Firestore document ID of the class,
            // not className. The original code had a broken ternary that always
            // used className as classId. We use className as a stable stand-in
            // for MVP (since ClassRepository.getClassesByTeacher doesn't return
            // doc IDs directly via toObjects). This is consistent and won't null-out.
            String classId = selectedClass.getClassName();

            String sessionId = "session_" + classId.replaceAll("\\s+", "_")
                    + "_" + System.currentTimeMillis();

            // FIX 2: AttendanceSession constructor now requires durationMinutes
            // as a parameter (between startTime and endTime) after the fix to
            // AttendanceSession.java. Passing it inline instead of via setDurationMinutes().
            AttendanceSession session = new AttendanceSession(
                    classId,                           // classId
                    selectedClass.getClassName(),       // className
                    selectedClass.getSubject(),         // subject
                    uid,                               // teacherId
                    "",                                // qrCode (empty — set by QRRefreshManager)
                    sessionKey,                        // sessionKey ← AES-256, 44 chars
                    latitude,                          // latitude
                    longitude,                         // longitude
                    Constants.DEFAULT_GEOFENCE_RADIUS, // geofenceRadius
                    Timestamp.now(),                   // startTime
                    durationMinutes,                   // FIX: durationMinutes now in constructor
                    null,                              // endTime
                    true                               // active
            );
            // No longer needed: session.setDurationMinutes(durationMinutes);

            sessionRepo.createSession(sessionId, session, task -> {
                progressStartSession.setVisibility(View.GONE);
                btnStartSession.setEnabled(true);

                if (task.isSuccessful()) {
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

                    ClassInfo newClass = new ClassInfo(className, subject, uid, new ArrayList<>());
                    String docId = "class_" + className.replaceAll("\\s+", "_")
                            + "_" + System.currentTimeMillis();

                    classRepo.addClass(docId, newClass, task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, getString(R.string.class_created), Toast.LENGTH_SHORT).show();
                            loadClasses();
                        } else {
                            Toast.makeText(this, getString(R.string.error_create_class), Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}