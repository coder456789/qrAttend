package com.qrattend.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

    private Spinner spinnerClass;
    private TextView tvLocationLabel, tvLocationCoords;
    private MaterialButton btnGetLocation, btnStartSession;
    private ProgressBar progressLocation, progressStartSession;

    private AuthManager authManager;
    private ClassRepository classRepo;
    private SessionRepository sessionRepo;

    private List<ClassInfo> classesList = new ArrayList<>();
    private double latitude = 0, longitude = 0;
    private boolean locationSet = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_session);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        spinnerClass = findViewById(R.id.spinnerClass);
        tvLocationLabel = findViewById(R.id.tvLocationLabel);
        tvLocationCoords = findViewById(R.id.tvLocationCoords);
        btnGetLocation = findViewById(R.id.btnGetLocation);
        btnStartSession = findViewById(R.id.btnStartSession);
        progressLocation = findViewById(R.id.progressLocation);
        progressStartSession = findViewById(R.id.progressStartSession);

        authManager = new AuthManager();
        classRepo = new ClassRepository();
        sessionRepo = new SessionRepository();

        btnGetLocation.setOnClickListener(v -> fetchLocation());
        btnStartSession.setOnClickListener(v -> startSession());

        loadClasses();
    }

    private void loadClasses() {
        String uid = authManager.getCurrentUserId();
        if (uid == null) return;

        classRepo.getClassesByTeacher(uid, classes -> {
            classesList = classes != null ? classes : new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (ClassInfo ci : classesList) {
                names.add(ci.getSubject() + " — " + ci.getClassName());
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

        // Use LocationHelper.fetchCurrentLocation() — actual API from source
        LocationHelper.fetchCurrentLocation(this, new LocationHelper.LocationCallback() {
            @Override
            public void onSuccess(Location location) {
                runOnUiThread(() -> {
                    latitude = location.getLatitude();
                    longitude = location.getLongitude();
                    locationSet = true;
                    tvLocationLabel.setText(R.string.get_my_location);
                    tvLocationCoords.setText(getString(R.string.location_fetched, latitude, longitude));
                    tvLocationCoords.setVisibility(View.VISIBLE);
                    progressLocation.setVisibility(View.GONE);
                    btnGetLocation.setEnabled(true);
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

        ClassInfo selectedClass = classesList.get(spinnerClass.getSelectedItemPosition());
        String uid = authManager.getCurrentUserId();
        if (uid == null) return;

        progressStartSession.setVisibility(View.VISIBLE);
        btnStartSession.setEnabled(false);

        try {
            // Generate session key using actual method: AESCryptoUtil.generateSessionKey()
            String sessionKey = AESCryptoUtil.generateSessionKey();

            // Generate session ID
            String sessionId = "session_" + selectedClass.getClassName() + "_" + System.currentTimeMillis();

            // Build AttendanceSession using actual constructor:
            // (classId, className, subject, teacherId, qrCode, sessionKey,
            //  latitude, longitude, geofenceRadius, startTime, endTime, active)
            AttendanceSession session = new AttendanceSession(
                    selectedClass.getTeacherId() != null ? selectedClass.getClassName() : "",  // classId
                    selectedClass.getClassName(),                                               // className
                    selectedClass.getSubject(),                                                 // subject
                    uid,                                                                        // teacherId
                    "",                                                                         // qrCode (empty, will be set by QRRefreshManager)
                    sessionKey,                                                                 // sessionKey
                    latitude,                                                                   // latitude
                    longitude,                                                                  // longitude
                    Constants.DEFAULT_GEOFENCE_RADIUS,                                          // geofenceRadius
                    Timestamp.now(),                                                            // startTime
                    null,                                                                       // endTime
                    true                                                                        // active
            );

            sessionRepo.createSession(sessionId, session, task -> {
                progressStartSession.setVisibility(View.GONE);
                btnStartSession.setEnabled(true);

                if (task.isSuccessful()) {
                    Intent intent = new Intent(this, DisplayQRActivity.class);
                    intent.putExtra("session_id", sessionId);
                    intent.putExtra("session_key", sessionKey);
                    intent.putExtra("teacher_id", uid);
                    intent.putExtra("course_id", selectedClass.getClassName());
                    intent.putExtra("latitude", latitude);
                    intent.putExtra("longitude", longitude);
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
}
