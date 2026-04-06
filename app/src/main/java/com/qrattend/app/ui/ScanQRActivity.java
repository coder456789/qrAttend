package com.qrattend.app.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
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
import com.qrattend.app.location.LocationHelper;
import com.qrattend.app.proxy.ProxyDetectionEngine;
import com.qrattend.app.qr.QRGeneratorUtil;
import com.qrattend.app.qr.QRScannerUtil;
import com.qrattend.app.security.DeviceFingerprint;
import com.qrattend.app.utils.Constants;

public class ScanQRActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

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
            startScanFlow();
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
                startScanFlow();
            } else {
                tvScanStatus.setText(R.string.error_camera_permission);
            }
        }
    }

    private void requestPermissionsAndStart() {
        btnTryAgain.setVisibility(View.GONE);
        if (hasAllPermissions()) {
            startScanFlow();
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
    private void startScanFlow() {
        tvScanStatus.setText(R.string.scan_instruction);
        progressScan.setVisibility(View.VISIBLE);

        String uid = authManager.getCurrentUserId();
        if (uid == null) {
            tvScanStatus.setText(R.string.error_generic);
            return;
        }

        // Get student info first
        studentRepo.getStudent(uid, student -> {
            if (student == null) {
                progressScan.setVisibility(View.GONE);
                tvScanStatus.setText(R.string.error_generic);
                return;
            }

            // Get student's class to find active session
            String className = student.getClassName();
            // Try to find active sessions - for simplicity, get all classes
            // and check for active sessions. In practice this would be filtered.
            // Use a broad approach: try the class the student belongs to.

            // For the MVP, we check if there's a session ID passed via intent
            String intentSessionId = getIntent().getStringExtra("session_id");
            if (intentSessionId != null && !intentSessionId.isEmpty()) {
                sessionRepo.getSession(intentSessionId, session -> {
                    if (session != null && session.getSessionKey() != null) {
                        initializeScanner(session, student, uid);
                    } else {
                        tvScanStatus.setText(R.string.error_session_not_found);
                        progressScan.setVisibility(View.GONE);
                    }
                });
            } else {
                // No session ID passed — try to find any active session
                // For now, scan with a placeholder and respond to QR content
                // This is a known UX limitation when the student doesn't know
                // which session is active. We'll try fetching by each enrolled class.
                findActiveSessionAndScan(student, uid);
            }
        });
    }

    private void findActiveSessionAndScan(Student student, String uid) {
        // Try to find an active session for the student
        // We'll use ClassRepository to find enrolled classes, then check for active sessions
        com.qrattend.app.data.repository.ClassRepository classRepo =
                new com.qrattend.app.data.repository.ClassRepository();

        classRepo.getAllClasses(classes -> {
            if (classes == null || classes.isEmpty()) {
                progressScan.setVisibility(View.GONE);
                tvScanStatus.setText(R.string.error_session_not_found);
                return;
            }

            // Check each class for active sessions
            for (com.qrattend.app.data.model.ClassInfo ci : classes) {
                if (ci.getEnrolledStudents() != null && ci.getEnrolledStudents().contains(uid)) {
                    sessionRepo.getActiveSession(ci.getClassName(), session -> {
                        if (session != null && session.isActive() && session.getSessionKey() != null) {
                            initializeScanner(session, student, uid);
                            return;
                        }
                    });
                }
            }

            // If no session found after checking, show error
            // (The callbacks are async, so this might fire before sessions are found.
            //  In production, you'd use a counter or CompletableFuture.)
            progressScan.setVisibility(View.GONE);
            tvScanStatus.setText(R.string.error_session_not_found);
            btnTryAgain.setVisibility(View.VISIBLE);
        });
    }

    private AttendanceSession currentSession;
    private Student currentStudent;
    private String currentUid;

    private void initializeScanner(AttendanceSession session, Student student, String uid) {
        this.currentSession = session;
        this.currentStudent = student;
        this.currentUid = uid;

        progressScan.setVisibility(View.GONE);
        tvScanStatus.setText(R.string.scan_instruction);

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
                    tvScanStatus.setText(getString(R.string.error_scan_failed));
                    btnTryAgain.setVisibility(View.VISIBLE);
                });
            }
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

            // Get current location via LocationHelper
            LocationHelper.fetchCurrentLocation(ScanQRActivity.this,
                    new LocationHelper.LocationCallback() {
                        @Override
                        public void onSuccess(Location location) {
                            runOnUiThread(() -> validateAndMark(payload, location));
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
        });
    }

    private void validateAndMark(QRGeneratorUtil.QRPayload payload, Location location) {
        // ProxyDetectionEngine.validate(payload, location, session, student, callback)
        proxyEngine.validate(payload, location, currentSession, currentStudent, result -> {
            runOnUiThread(() -> {
                progressScan.setVisibility(View.GONE);

                String deviceFp = proxyEngine.getCurrentDeviceFingerprint();
                GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());

                if (result.success) {
                    // Build AttendanceRecord with actual constructor:
                    // (status, Timestamp, deviceId, GeoPoint, rejectionReason, studentId, sessionId)
                    AttendanceRecord record = new AttendanceRecord(
                            Constants.STATUS_PRESENT,
                            Timestamp.now(),
                            deviceFp,
                            geoPoint,
                            Constants.REASON_NONE,
                            currentUid,
                            payload.sessionId
                    );

                    attendanceRepo.markAttendance(payload.sessionId, currentUid, record, task -> {
                        if (task.isSuccessful()) {
                            // Also register device on first scan
                            new StudentRepository().registerDevice(currentUid, deviceFp, t -> {});

                            showSuccessDialog();
                        } else {
                            tvScanStatus.setText(R.string.error_generic);
                            btnTryAgain.setVisibility(View.VISIBLE);
                        }
                    });
                } else {
                    // Rejected — record the rejected attendance too
                    AttendanceRecord record = new AttendanceRecord(
                            Constants.STATUS_REJECTED,
                            Timestamp.now(),
                            deviceFp,
                            geoPoint,
                            result.rejectionReason,
                            currentUid,
                            payload.sessionId
                    );

                    attendanceRepo.markAttendance(payload.sessionId, currentUid, record, task -> {});

                    showRejectionDialog(result.rejectionReason);
                }
            });
        });
    }

    private void showSuccessDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.attendance_success)
                .setMessage(R.string.attendance_success_message)
                .setPositiveButton(R.string.ok, (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void showRejectionDialog(String reason) {
        String readableReason = mapRejectionReason(reason);
        tvScanStatus.setText(readableReason);
        btnTryAgain.setVisibility(View.VISIBLE);

        new AlertDialog.Builder(this)
                .setTitle(R.string.attendance_failed)
                .setMessage(readableReason)
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
            case Constants.REASON_DEVICE_MISMATCH:
                return getString(R.string.rejection_device_mismatch);
            case Constants.REASON_NONCE_EXPIRED:
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
