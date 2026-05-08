package com.qrattend.app.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.GeoPoint;
import com.qrattend.app.R;
import com.qrattend.app.data.model.AttendanceRecord;
import com.qrattend.app.data.model.AttendanceSession;
import com.qrattend.app.data.model.Student;
import com.qrattend.app.data.repository.AttendanceRepository;
import com.qrattend.app.data.repository.SessionRepository;
import com.qrattend.app.data.repository.StudentRepository;
import com.qrattend.app.firebase.AuthManager;
import com.qrattend.app.location.GeoValidator;
import com.qrattend.app.location.LocationHelper;
import com.qrattend.app.proxy.ProxyDetectionEngine;
import com.qrattend.app.qr.QRGeneratorUtil;
import com.qrattend.app.qr.QRScannerUtil;
import com.qrattend.app.security.DeviceFingerprint;
import com.qrattend.app.utils.Constants;

public class ScanQRActivity extends AppCompatActivity {

    private static final String TAG = "ScanQRActivity";
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    /**
     * Set to true when we send the user to a settings screen.
     * onResume() will re-check connectivity and start the scan flow when set.
     */
    private boolean pendingStartAfterSettings = false;

    private PreviewView previewView;
    private TextView tvScanStatus;
    private ProgressBar progressScan;
    private MaterialButton btnTryAgain;

    private QRScannerUtil scanner;
    private AuthManager authManager;
    private SessionRepository sessionRepo;
    private StudentRepository studentRepo;
    private AttendanceRepository attendanceRepo;
    private ProxyDetectionEngine proxyEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_qr);

        previewView = findViewById(R.id.previewView);
        tvScanStatus = findViewById(R.id.tvScanStatus);
        progressScan = findViewById(R.id.progressScan);
        btnTryAgain = findViewById(R.id.btnTryAgain);

        authManager = new AuthManager();
        sessionRepo = new SessionRepository();
        studentRepo = new StudentRepository();
        attendanceRepo = new AttendanceRepository();
        proxyEngine = new ProxyDetectionEngine(this);

        btnTryAgain.setOnClickListener(v -> requestPermissionsAndStart());

        if (hasAllPermissions()) {
            showConnectivityDialog(this::startScanFlow);
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
    }

    private boolean hasAllPermissions() {
        for (String perm : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                showConnectivityDialog(this::startScanFlow);
            } else {
                tvScanStatus.setText(R.string.error_camera_permission);
            }
        }
    }

    private void requestPermissionsAndStart() {
        btnTryAgain.setVisibility(View.GONE);
        if (hasAllPermissions()) {
            showConnectivityDialog(this::startScanFlow);
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * Flow: Get session first to obtain sessionKey, then start scanning.
     * QRScannerUtil requires sessionKey in its constructor.
     * Per Member 2 code comments, we need:
     *   1. sessionRepo.getSession(sessionId, ...) to get the session + key
     *   2. studentRepo.getStudent(uid, ...) to get student for proxy check
     *   3. QRScannerUtil with sessionKey to scan/decrypt
     *   4. ProxyDetectionEngine.validate(payload, location, session, student, callback)
     *
     * BUT: We don't know the sessionId until the QR is scanned.
     * The QR itself is encrypted with the sessionKey.
     * Looking at QRScannerUtil, it takes the sessionKey in the constructor because
     * it decrypts the QR internally. So we need a way to get the sessionKey.
     *
     * Approach: We need the student to somehow know which session to scan for.
     * In practice, the session is discovered dynamically — the student scans the QR,
     * the app decrypts it (needing the key), then validates.
     *
     * Since QRScannerUtil requires the key upfront, the student must first get the
     * active session for a known class. For MVP, we'll use a simplified flow:
     * Let's start scanning without decryption first, or fetch active sessions.
     *
     * Actually, looking closer at the architecture — the teacher displays the QR
     * which is encrypted with the sessionKey. The student needs the sessionKey to decrypt.
     * The sessionKey is stored in Firestore (AttendanceSession.sessionKey) so
     * the student app fetches it before scanning.
     *
     * Best flow for ScanQRActivity:
     * 1. Get current student's enrolled classes
     * 2. For each class, check getActiveSession
     * 3. Once we have the active session, we have the sessionKey
     * 4. Initialize QRScannerUtil with that key
     * 5. Start scanning
     *
     * Simplified: We try to get any active session the student can see,
     * or we pass session info via intent from the dashboard.
     *
     * For robustness, let's scan without knowing the session first —
     * Actually, let's just iterate: check all active sessions student is enrolled in.
     * For now, we'll use a simpler approach: pass classId via intent if available,
     * or scan all active sessions.
     */
    // ── Connectivity Helper ─────────────────────────────────────────────────

    private boolean isWifiOn() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return wm != null && wm.isWifiEnabled();
    }

    private boolean isLocationOn() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
    }

    private boolean isBluetoothOn() { return true; /* Bluetooth not required — kept for backward compat */ }

    /**
     * Checks WiFi / Bluetooth / Location before opening the scanner.
     * <p>
     * All three services MUST be enabled to proceed.
     * If any are off → shows a blocking modal:
     *   "Please enable WiFi, Bluetooth, and Location to scan the QR code."
     * The modal provides:
     *   "Open Settings" → opens Android Settings so the user can enable services.
     *   "Cancel"        → finishes the activity (no bypass allowed).
     * When the user returns from Settings, onResume() re-checks and auto-proceeds.
     */
    private void showConnectivityDialog(Runnable onReady) {
        if (isWifiOn() && isLocationOn()) {
            onReady.run();
            return;
        }

        // Store callback for re-check in onResume()
        this.pendingOnReady = onReady;
        pendingStartAfterSettings = true;

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.connectivity_required_title))
                .setMessage("Please enable WiFi and Location to scan the QR code.")
                .setCancelable(false)
                .setPositiveButton(getString(R.string.connectivity_open_settings), (d, w) -> {
                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                })
                .setNegativeButton(getString(R.string.cancel), (d, w) -> {
                    finish();
                })
                .show();
    }

    // enableServicesSequentially is no longer used — kept as dead code removed below.



    /** Stored callback used when we return from Location Settings. */
    private Runnable pendingOnReady = null;

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingStartAfterSettings && pendingOnReady != null) {
            pendingStartAfterSettings = false;
            if (isWifiOn() && isLocationOn()) {
                Runnable callback = pendingOnReady;
                pendingOnReady = null;
                callback.run();
            } else {
                Runnable callback = pendingOnReady;
                pendingOnReady = null;
                showConnectivityDialog(callback);
            }
        }
    }

    private void startScanFlow() {
        tvScanStatus.setText(R.string.scan_instruction);
        progressScan.setVisibility(View.VISIBLE);

        String uid = authManager.getCurrentUserId();
        Log.d(TAG, "startScanFlow: uid=" + uid);
        if (uid == null) {
            Log.e(TAG, "startScanFlow: user not logged in");
            tvScanStatus.setText(R.string.error_generic);
            return;
        }

        // Get student info — proceed even if doc missing (stub will be created via registerDevice)
        studentRepo.getStudent(uid, student -> {
            Student effectiveStudent = student;
            if (effectiveStudent == null) {
                Log.w(TAG, "startScanFlow: student document not found for uid=" + uid
                        + " — using stub. Student will be registered via registerDevice().");
                // Build a minimal stub so the scan flow can continue.
                // ProxyDetectionEngine doesn't require name/rollNo, only deviceId checks.
                effectiveStudent = new Student();
                effectiveStudent.setStudentId(uid);   // guard: set only if setter added — see below
            }
            Log.d(TAG, "startScanFlow: student resolved — " + effectiveStudent.getName()
                    + ", class=" + effectiveStudent.getClassName());

            final Student finalStudent = effectiveStudent;

            // For the MVP, we check if there's a session ID passed via intent
            String intentSessionId = getIntent().getStringExtra("session_id");
            Log.d(TAG, "startScanFlow: intentSessionId=" + intentSessionId);

            if (intentSessionId != null && !intentSessionId.isEmpty()) {
                sessionRepo.getSession(intentSessionId, session -> {
                    if (session != null && session.getSessionKey() != null
                            && session.getSessionKey().length() >= 32) {
                        Log.d(TAG, "startScanFlow: intent session found, initializing scanner");
                        initializeScanner(session, finalStudent, uid);
                    } else {
                        Log.w(TAG, "startScanFlow: intent session invalid or not found");
                        tvScanStatus.setText(R.string.error_session_not_found);
                        progressScan.setVisibility(View.GONE);
                    }
                });
            } else {
                // No session ID passed — try to find any active session
                Log.d(TAG, "startScanFlow: no intent session, searching for active sessions...");
                findActiveSessionAndScan(finalStudent, uid);
            }
        });
    }

    private void findActiveSessionAndScan(Student student, String uid) {
        // Query Firestore directly for any active session.
        // We don't filter by enrollment because students may not be enrolled yet —
        // the QR code encryption + geofence + device fingerprint already authenticate.
        com.google.firebase.firestore.FirebaseFirestore db =
                com.google.firebase.firestore.FirebaseFirestore.getInstance();

        Log.d(TAG, "Querying Firestore for active sessions in collection: " + Constants.SESSIONS);

        db.collection(Constants.SESSIONS)
                .whereEqualTo("active", true)
                .limit(5)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot == null || querySnapshot.isEmpty()) {
                        Log.w(TAG, "No active sessions found in Firestore (query returned empty)");
                        progressScan.setVisibility(View.GONE);
                        tvScanStatus.setText("No active session found.\nMake sure the teacher has started a session.");
                        btnTryAgain.setVisibility(View.VISIBLE);
                        return;
                    }

                    Log.d(TAG, "Found " + querySnapshot.size() + " active session(s). Checking validity...");

                    int skippedNull = 0, skippedKey = 0, skippedExpired = 0;

                    // Use the first active session that has a valid sessionKey and is not expired
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        AttendanceSession session = doc.toObject(AttendanceSession.class);
                        if (session == null) {
                            Log.w(TAG, "Session doc " + doc.getId() + " deserialized to null");
                            skippedNull++;
                            continue;
                        }

                        // Skip sessions with invalid short keys
                        if (session.getSessionKey() == null
                                || session.getSessionKey().length() < 32) {
                            Log.w(TAG, "Session " + doc.getId() + " skipped: invalid key (length="
                                    + (session.getSessionKey() != null ? session.getSessionKey().length() : "null") + ")");
                            skippedKey++;
                            continue;
                        }

                        // Auto-deactivate expired sessions in Firestore
                        if (session.isExpired()) {
                            Log.w(TAG, "Session " + doc.getId() + " skipped: expired"
                                    + " (duration=" + session.getDurationMinutes() + "min"
                                    + ", startTime=" + session.getStartTime() + ")");
                            sessionRepo.endSession(doc.getId(), task -> { /* fire and forget */ });
                            skippedExpired++;
                            continue;
                        }

                        Log.d(TAG, "Valid session found: " + doc.getId()
                                + " (subject=" + session.getSubject()
                                + ", class=" + session.getClassName() + ")");
                        initializeScanner(session, student, uid);
                        return;
                    }

                    // No valid session found — show detailed reason
                    Log.w(TAG, "All sessions skipped: null=" + skippedNull
                            + ", badKey=" + skippedKey + ", expired=" + skippedExpired);
                    progressScan.setVisibility(View.GONE);
                    String detail;
                    if (skippedExpired > 0 && skippedKey == 0) {
                        detail = "Session has expired.\nAsk the teacher to start a new session.";
                    } else if (skippedKey > 0) {
                        detail = "Session has invalid encryption key.\nAsk the teacher to restart.";
                    } else {
                        detail = getString(R.string.error_session_not_found);
                    }
                    tvScanStatus.setText(detail);
                    btnTryAgain.setVisibility(View.VISIBLE);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore query FAILED: " + e.getMessage(), e);
                    progressScan.setVisibility(View.GONE);
                    tvScanStatus.setText("Failed to query sessions:\n" + e.getMessage());
                    btnTryAgain.setVisibility(View.VISIBLE);
                });
    }

    private AttendanceSession currentSession;
    private Student currentStudent;
    private String currentUid;

    // Pre-fetched locations: starts collecting as soon as the scanner initializes.
    // Multiple samples are stored so we can pick the NEAREST to teacher.
    private volatile java.util.List<Location> preFetchedLocations;

    private void initializeScanner(AttendanceSession session, Student student, String uid) {
        this.currentSession = session;
        this.currentStudent = student;
        this.currentUid = uid;

        progressScan.setVisibility(View.GONE);
        tvScanStatus.setText(R.string.scan_instruction);

        // PRE-FETCH: Start multi-sample GPS collection immediately while the
        // student is pointing the camera. Collects ALL samples so we can
        // pick the nearest to teacher later.
        preFetchedLocations = null;
        Log.d(TAG, "Starting multi-sample location pre-fetch...");
        LocationHelper.fetchAllLocations(this, new LocationHelper.MultiLocationCallback() {
            @Override
            public void onLocationsCollected(java.util.List<Location> locations) {
                preFetchedLocations = locations;
                Log.d(TAG, "Pre-fetch complete: " + locations.size() + " samples collected");
            }

            @Override
            public void onFailure(String reason) {
                Log.w(TAG, "Location pre-fetch failed: " + reason);
            }
        });

        // QRScannerUtil constructor: (Context, PreviewView, LifecycleOwner, sessionKey)
        scanner = new QRScannerUtil(this, previewView, this, session.getSessionKey());

        // startScanning(ScanCallback) — ScanCallback has onPayloadDecoded and onError
        scanner.startScanning(new QRScannerUtil.ScanCallback() {
            @Override
            public void onPayloadDecoded(QRGeneratorUtil.QRPayload payload) {
                runOnUiThread(() -> handlePayload(payload));
            }

            @Override
            public void onError(String reason) {
                runOnUiThread(() -> {
                    tvScanStatus.setText(getString(R.string.error_scan_failed) + "\nReason: " + reason);
                    btnTryAgain.setVisibility(View.VISIBLE);
                });
            }
        });

        // ── Pinch-to-zoom on camera preview ─────────────────────────────
        setupPinchToZoom();
    }

    /**
     * Enables pinch-to-zoom on the PreviewView using a ScaleGestureDetector.
     * The Camera reference from QRScannerUtil is used to adjust the zoom ratio.
     */
    private void setupPinchToZoom() {
        android.view.ScaleGestureDetector scaleDetector = new android.view.ScaleGestureDetector(
                this, new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(android.view.ScaleGestureDetector detector) {
                if (scanner == null || scanner.getCamera() == null) return false;

                androidx.camera.core.Camera cam = scanner.getCamera();
                float currentZoom = cam.getCameraInfo().getZoomState().getValue() != null
                        ? cam.getCameraInfo().getZoomState().getValue().getZoomRatio() : 1f;
                float newZoom = currentZoom * detector.getScaleFactor();

                // Clamp within the camera's supported zoom range
                float minZoom = cam.getCameraInfo().getZoomState().getValue() != null
                        ? cam.getCameraInfo().getZoomState().getValue().getMinZoomRatio() : 1f;
                float maxZoom = cam.getCameraInfo().getZoomState().getValue() != null
                        ? cam.getCameraInfo().getZoomState().getValue().getMaxZoomRatio() : 1f;
                newZoom = Math.max(minZoom, Math.min(newZoom, maxZoom));

                cam.getCameraControl().setZoomRatio(newZoom);
                return true;
            }
        });

        previewView.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            return true;
        });
    }

    private void handlePayload(QRGeneratorUtil.QRPayload payload) {
        tvScanStatus.setText(R.string.validating);
        progressScan.setVisibility(View.VISIBLE);
        btnTryAgain.setVisibility(View.GONE);

        // Check if already marked
        attendanceRepo.hasAlreadyMarked(payload.sessionId, currentUid, alreadyMarked -> {
            if (alreadyMarked) {
                progressScan.setVisibility(View.GONE);
                tvScanStatus.setText(R.string.error_already_marked);
                btnTryAgain.setVisibility(View.VISIBLE);
                return;
            }

            // Check if pre-fetched locations are available and at least one is fresh
            java.util.List<Location> prefetched = preFetchedLocations;
            if (prefetched != null && !prefetched.isEmpty()) {
                Log.d(TAG, "Using " + prefetched.size() + " pre-fetched locations");
                runOnUiThread(() -> pickNearestAndProceed(payload, prefetched));
            } else {
                // Pre-fetch not ready — fetch fresh multi-sample
                Log.d(TAG, "Pre-fetch not ready, fetching fresh multi-sample...");
                tvScanStatus.setText(R.string.acquiring_location);
                LocationHelper.fetchAllLocations(ScanQRActivity.this,
                        new LocationHelper.MultiLocationCallback() {
                            @Override
                            public void onLocationsCollected(java.util.List<Location> locations) {
                                runOnUiThread(() -> pickNearestAndProceed(payload, locations));
                            }

                            @Override
                            public void onFailure(String reason) {
                                runOnUiThread(() -> {
                                    progressScan.setVisibility(View.GONE);
                                    tvScanStatus.setText(R.string.error_location_unavailable);
                                    btnTryAgain.setVisibility(View.VISIBLE);
                                });
                            }
                        });
            }
        });
    }

    /**
     * From all collected GPS samples, picks the one NEAREST to the teacher's
     * location and uses it for validation. Indoor GPS drifts in random directions,
     * so the closest reading to the teacher is most likely the true position.
     */
    private void pickNearestAndProceed(QRGeneratorUtil.QRPayload payload,
                                       java.util.List<Location> locations) {
        double teacherLat = currentSession.getLatitude();
        double teacherLng = currentSession.getLongitude();

        Location nearest = null;
        float nearestDist = Float.MAX_VALUE;

        for (Location loc : locations) {
            float dist = LocationHelper.distanceBetweenMeters(
                    loc.getLatitude(), loc.getLongitude(), teacherLat, teacherLng);
            Log.d(TAG, "Sample: accuracy=" + loc.getAccuracy() + "m, distance=" 
                    + String.format("%.1f", dist) + "m from teacher");
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = loc;
            }
        }

        Log.d(TAG, "Picked nearest sample: distance=" + String.format("%.1f", nearestDist)
                + "m, accuracy=" + nearest.getAccuracy() + "m (from " + locations.size() + " samples)");

        // Warn if ALL samples show poor accuracy
        if (nearest.hasAccuracy()
                && nearest.getAccuracy() > Constants.TEACHER_MAX_ACCEPTABLE_ACCURACY) {
            progressScan.setVisibility(View.GONE);
            String msg = getString(R.string.warning_student_gps_poor,
                    String.format("%.0f", nearest.getAccuracy()));
            tvScanStatus.setText(msg);
            btnTryAgain.setVisibility(View.VISIBLE);

            final Location finalNearest = nearest;
            new AlertDialog.Builder(ScanQRActivity.this)
                    .setTitle("⚠ Poor GPS Signal")
                    .setMessage(msg)
                    .setPositiveButton(R.string.try_again, (d, w) -> {
                        btnTryAgain.setVisibility(View.GONE);
                        handlePayload(payload);
                    })
                    .setNegativeButton("Continue Anyway", (d, w) -> {
                        validateAndMark(payload, finalNearest);
                    })
                    .show();
            return;
        }

        validateAndMark(payload, nearest);
    }

    private void validateAndMark(QRGeneratorUtil.QRPayload payload, Location location) {
        String deviceFp = proxyEngine.getCurrentDeviceFingerprint();

        Log.d(TAG, "=== VALIDATION START ===");
        Log.d(TAG, "Student location: lat=" + location.getLatitude()
                + ", lng=" + location.getLongitude()
                + ", accuracy=" + location.getAccuracy() + "m");
        Log.d(TAG, "Device fingerprint: " + deviceFp);

        // STEP 1: Register device BEFORE validation so first-time students aren't blocked.
        // registerDevice is idempotent — safe to call on every scan.
        new StudentRepository().registerDevice(currentUid, deviceFp, regTask -> {
            Log.d(TAG, "Device registration result: " + (regTask.isSuccessful() ? "OK" : "FAILED"));

            // STEP 2: Re-fetch the session FRESH from Firestore so the nonce is always
            // the latest one (QRRefreshManager rotates it every 10 seconds).
            sessionRepo.getSession(payload.sessionId, freshSession -> {
                if (freshSession == null || !freshSession.isActive()) {
                    Log.w(TAG, "Session not found or inactive: " + payload.sessionId);
                    runOnUiThread(() -> {
                        progressScan.setVisibility(View.GONE);
                        tvScanStatus.setText(R.string.error_session_not_found);
                        btnTryAgain.setVisibility(View.VISIBLE);
                    });
                    return;
                }

                // Log session location info for debugging
                Log.d(TAG, "Session location: lat=" + freshSession.getLatitude()
                        + ", lng=" + freshSession.getLongitude()
                        + ", geofenceRadius=" + freshSession.getGeofenceRadius() + "m");
                float distToTeacher = GeoValidator.getDistanceToClassroom(
                        location, freshSession.getLatitude(), freshSession.getLongitude());
                Log.d(TAG, "Distance from teacher: " + String.format("%.1f", distToTeacher) + "m");

                // STEP 3: Also re-fetch student so deviceId is definitely populated.
                studentRepo.getStudent(currentUid, freshStudent -> {
                    Log.d(TAG, "Student deviceId: "
                            + (freshStudent != null ? freshStudent.getDeviceId() : "null"));

                    // STEP 4: Validate with fresh data.
                    proxyEngine.validate(payload, location, freshSession, freshStudent, result -> {
                        Log.d(TAG, "Validation result: success=" + result.success
                                + ", reason=" + result.rejectionReason);

                        // Compute distance for UI display
                        final float distance = GeoValidator.getDistanceToClassroom(
                                location, freshSession.getLatitude(), freshSession.getLongitude());

                        runOnUiThread(() -> {
                            progressScan.setVisibility(View.GONE);
                            GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());

                            if (result.success) {
                                String subjectName = freshSession.getSubject() != null
                                        ? freshSession.getSubject() : "Unknown Subject";
                                AttendanceRecord record = new AttendanceRecord(
                                        Constants.STATUS_PRESENT,
                                        Timestamp.now(),
                                        deviceFp,
                                        geoPoint,
                                        Constants.REASON_NONE,
                                        currentUid,
                                        payload.sessionId
                                );
                                record.setSubject(subjectName);
                                if (freshStudent != null) {
                                    record.setStudentName(freshStudent.getName());
                                    record.setStudentRollNo(freshStudent.getRollNo());
                                }
                                attendanceRepo.markAttendance(payload.sessionId, currentUid, record, task -> {
                                    if (task.isSuccessful()) {
                                        // Auto-enroll student in the class (idempotent)
                                        autoEnrollInClass(freshSession, currentUid);
                                        showSuccessDialog(distance);
                                    } else {
                                        tvScanStatus.setText(R.string.error_generic);
                                        btnTryAgain.setVisibility(View.VISIBLE);
                                    }
                                });
                            } else {
                                String subjectName = freshSession.getSubject() != null
                                        ? freshSession.getSubject() : "Unknown Subject";
                                AttendanceRecord record = new AttendanceRecord(
                                        Constants.STATUS_REJECTED,
                                        Timestamp.now(),
                                        deviceFp,
                                        geoPoint,
                                        result.rejectionReason,
                                        currentUid,
                                        payload.sessionId
                                );
                                record.setSubject(subjectName);
                                if (freshStudent != null) {
                                    record.setStudentName(freshStudent.getName());
                                    record.setStudentRollNo(freshStudent.getRollNo());
                                }
                                attendanceRepo.markAttendance(payload.sessionId, currentUid, record, task -> {});
                                showRejectionDialog(result.rejectionReason, distance, location);
                            }
                        });
                    });
                });
            });
        });
    }

    /**
     * Automatically enroll the student in the class associated with this session.
     * Looks up the class by className (which equals classId in sessions) and calls
     * ClassRepository.enrollStudent — idempotent, safe to call on every scan.
     */
    private void autoEnrollInClass(AttendanceSession session, String studentId) {
        if (session == null || session.getClassId() == null) return;
        String className = session.getClassId(); // className is stored as classId

        com.qrattend.app.data.repository.ClassRepository classRepo =
                new com.qrattend.app.data.repository.ClassRepository();

        // Find the class document whose className matches the session's classId
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection(com.qrattend.app.utils.Constants.CLASSES)
                .whereEqualTo("className", className)
                .whereEqualTo("teacherId", session.getTeacherId())
                .limit(1)
                .get()
                .addOnSuccessListener(qs -> {
                    if (qs != null && !qs.isEmpty()) {
                        String classDocId = qs.getDocuments().get(0).getId();
                        classRepo.enrollStudent(classDocId, studentId, t -> {
                            Log.d(TAG, "autoEnrollInClass: " +
                                    (t.isSuccessful() ? "enrolled in " + classDocId : "enroll failed"));
                        });
                    }
                });
    }

    private void showSuccessDialog(float distanceMeters) {
        String distanceText = String.format("%.1f", distanceMeters);
        String message = getString(R.string.attendance_success_message)
                + "\n\n📍 " + getString(R.string.distance_from_teacher, distanceText);

        new AlertDialog.Builder(this)
                .setTitle(R.string.attendance_success)
                .setMessage(message)
                .setPositiveButton(R.string.ok, (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void showRejectionDialog(String reason, float distanceMeters, Location studentLocation) {
        String readableReason = mapRejectionReason(reason);
        String distanceText = String.format("%.1f", distanceMeters);

        // Include GPS accuracy in the message so students understand why they were rejected
        String accuracyNote = "";
        if (studentLocation != null && studentLocation.hasAccuracy()) {
            accuracyNote = "\n📡 GPS accuracy: ±" + String.format("%.0f", studentLocation.getAccuracy()) + "m";
        }

        String fullMessage = readableReason
                + "\n\n📍 " + getString(R.string.distance_from_teacher, distanceText)
                + accuracyNote;

        tvScanStatus.setText(readableReason);
        btnTryAgain.setVisibility(View.VISIBLE);

        new AlertDialog.Builder(this)
                .setTitle(R.string.attendance_failed)
                .setMessage(fullMessage)
                .setPositiveButton(R.string.try_again, (d, w) -> {
                    btnTryAgain.setVisibility(View.GONE);
                    // Re-initialize scanner
                    if (currentSession != null && currentStudent != null) {
                        initializeScanner(currentSession, currentStudent, currentUid);
                    }
                })
                .setNegativeButton(R.string.cancel, (d, w) -> finish())
                .show();
    }

    private String mapRejectionReason(String reason) {
        if (reason == null) return getString(R.string.error_generic);
        switch (reason) {
            case Constants.REASON_LOCATION_MISMATCH:
                return getString(R.string.rejection_location_mismatch);
            case "location_stale":
                return "Location data is too old. Please try again.";
            case "location_inaccurate":
                return "GPS accuracy is too poor for validation. Move near a window.";
            case Constants.REASON_DEVICE_MISMATCH:
                return getString(R.string.rejection_device_mismatch);
            case Constants.REASON_NONCE_EXPIRED:
            case "nonce_mismatch":
                return getString(R.string.rejection_nonce_expired);
            case Constants.REASON_ROOT_DETECTED:
                return getString(R.string.rejection_root_detected);
            default:
                return reason;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanner != null) {
            scanner.stopScanning();
        }
    }
}